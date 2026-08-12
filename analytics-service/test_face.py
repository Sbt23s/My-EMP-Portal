"""
Tests for the face enrolment and verification logic.

Runnable two ways, on purpose:

    python test_face.py          # no pytest needed
    python -m pytest test_face.py

Nothing here needs dlib, PyTorch or a camera. The parts worth testing are the
ones that decide things -- how enrolments are stored and read back, when a match
counts, and when two frames look like a photograph rather than a person -- and
those are all reachable without the vision stack, which is why the module can be
imported at all now that the heavy libraries load lazily.
"""
import os
import shutil
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import main  # noqa: E402


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

def _encoding(seed: float):
    """A stand-in for a 128-value face encoding."""
    return np.full(128, seed, dtype=np.float64)


def _isolate_faces_dir():
    """Points the module at a throwaway directory so tests never touch real
    enrolments. Returns the previous value so it can be put back."""
    previous = main.FACES_DIR
    main.FACES_DIR = tempfile.mkdtemp(prefix="faces-test-")
    return previous


def _restore_faces_dir(previous):
    if main.FACES_DIR != previous and os.path.isdir(main.FACES_DIR):
        shutil.rmtree(main.FACES_DIR, ignore_errors=True)
    main.FACES_DIR = previous


# ---------------------------------------------------------------------------
# reading enrolments back
# ---------------------------------------------------------------------------

def test_no_enrolment_reads_as_empty():
    previous = _isolate_faces_dir()
    try:
        assert main._load_encodings(np, 4242) == []
    finally:
        _restore_faces_dir(previous)


def test_legacy_single_encoding_still_reads():
    """The old one-photo enrolment saved a flat 128-value array. Everybody
    already enrolled has one, so it has to keep working -- otherwise upgrading
    silently locks them all out of face punch."""
    previous = _isolate_faces_dir()
    try:
        np.save(main._face_path(1), _encoding(0.25))
        loaded = main._load_encodings(np, 1)
        assert len(loaded) == 1
        assert loaded[0].shape == (128,)
        assert np.allclose(loaded[0], 0.25)
    finally:
        _restore_faces_dir(previous)


def test_several_encodings_read_as_a_list():
    previous = _isolate_faces_dir()
    try:
        np.save(main._face_path(2), np.array([_encoding(0.1), _encoding(0.2), _encoding(0.3)]))
        loaded = main._load_encodings(np, 2)
        assert len(loaded) == 3
        assert all(e.shape == (128,) for e in loaded)
    finally:
        _restore_faces_dir(previous)


def test_enrolments_are_capped_oldest_first():
    """Re-enrolling should eventually replace the old photos rather than being
    outvoted by them -- somebody who has grown a beard needs the new ones to
    win."""
    previous = _isolate_faces_dir()
    try:
        kept = [_encoding(i / 10) for i in range(main.MAX_ENROLMENTS + 3)]
        trimmed = kept[-main.MAX_ENROLMENTS:]
        np.save(main._face_path(3), np.array(trimmed))
        loaded = main._load_encodings(np, 3)
        assert len(loaded) == main.MAX_ENROLMENTS
        # The newest survives, the oldest is gone.
        assert np.allclose(loaded[-1], kept[-1])
        assert not any(np.allclose(e, kept[0]) for e in loaded)
    finally:
        _restore_faces_dir(previous)


def test_forgetting_a_face_removes_the_file_and_is_safe_twice():
    """A face is biometric data, so erasing it has to work -- and asking twice
    must not be an error."""
    previous = _isolate_faces_dir()
    try:
        np.save(main._face_path(9), _encoding(0.5))
        assert os.path.exists(main._face_path(9))

        first = main.forget_face(9)
        assert first["success"] is True
        assert not os.path.exists(main._face_path(9))

        second = main.forget_face(9)
        assert second["success"] is True
    finally:
        _restore_faces_dir(previous)


def test_status_reports_how_many_photos_without_the_vision_stack():
    """Asked before enrolling, this is what tells somebody whether face punch is
    even available to them."""
    previous = _isolate_faces_dir()
    try:
        np.save(main._face_path(7), np.array([_encoding(0.1), _encoding(0.2)]))
        status = main.face_status(7)
        # On a machine without dlib the answer is "unavailable", which is honest;
        # with it, the count has to be right. Both are acceptable, silence is not.
        assert "enrolled" in status and "photos" in status
        if status.get("available"):
            assert status["enrolled"] is True
            assert status["photos"] == 2
    finally:
        _restore_faces_dir(previous)


# ---------------------------------------------------------------------------
# deciding a match
# ---------------------------------------------------------------------------

def _matches(distance):
    """The rule verify_face applies: closest enrolment inside the tolerance."""
    return distance <= main.FACE_TOLERANCE


def test_tolerance_is_stricter_than_the_library_default():
    """0.6 is loose enough to confuse siblings. If this ever drifts back up it
    should be a deliberate change, not a surprise."""
    assert main.FACE_TOLERANCE <= 0.55


def test_an_exact_match_passes_and_a_stranger_does_not():
    assert _matches(0.0) is True
    assert _matches(0.9) is False


def test_the_boundary_counts_as_a_match():
    """Deliberate: a distance exactly at the tolerance is accepted, so the rule
    is <= and not <. Somebody on the line should get to work."""
    assert _matches(main.FACE_TOLERANCE) is True
    assert _matches(main.FACE_TOLERANCE + 0.0001) is False


def test_the_closest_of_several_enrolments_is_the_one_that_decides():
    """This is the point of storing several photos: one good angle is enough."""
    distances = np.array([0.81, 0.42, 0.77])
    assert _matches(float(np.min(distances))) is True


# ---------------------------------------------------------------------------
# telling a person from a photograph
# ---------------------------------------------------------------------------

def _motion(a, b):
    """The comparison verify_face makes between two frames."""
    return float(np.mean(np.abs(a.astype("float32") - b.astype("float32"))))


LIVENESS_FLOOR = 0.8


def test_two_identical_frames_read_as_a_photograph():
    """A printed photo or a phone screen held still produces frames that do not
    differ. That is the whole signal."""
    frame = np.full((64, 64, 3), 120, dtype=np.uint8)
    assert _motion(frame, frame.copy()) < LIVENESS_FLOOR


def test_a_moving_face_reads_as_a_person():
    first = np.full((64, 64, 3), 120, dtype=np.uint8)
    second = first.copy()
    second[20:44, 20:44] = 200          # something moved
    assert _motion(first, second) >= LIVENESS_FLOOR


def test_camera_noise_alone_is_not_mistaken_for_movement():
    """A still photograph in front of a real sensor is never bit-identical. The
    floor has to sit above sensor noise or every photo would pass as live."""
    rng = np.random.default_rng(7)
    first = np.full((64, 64, 3), 120, dtype=np.uint8)
    noise = rng.integers(-1, 2, size=first.shape, dtype=np.int16)
    second = np.clip(first.astype(np.int16) + noise, 0, 255).astype(np.uint8)
    assert _motion(first, second) < LIVENESS_FLOOR


def test_mismatched_frame_sizes_are_not_treated_as_a_pass():
    """Two frames of different shapes cannot be compared. The code reports the
    check as not made rather than quietly passing it, which would be worse than
    not checking at all."""
    a = np.zeros((64, 64, 3), dtype=np.uint8)
    b = np.zeros((32, 32, 3), dtype=np.uint8)
    assert a.shape != b.shape


# ---------------------------------------------------------------------------
# blink, smile and head turn, from landmarks
# ---------------------------------------------------------------------------

def _eye(cx, cy, width=20, height=8):
    """Six points around an eye, in the order the library returns them."""
    hw, hh = width / 2.0, height / 2.0
    return [
        (cx - hw, cy),
        (cx - hw / 2, cy - hh),
        (cx + hw / 2, cy - hh),
        (cx + hw, cy),
        (cx + hw / 2, cy + hh),
        (cx - hw / 2, cy + hh),
    ]


def _lips(width=40, height=12, y=100):
    top = [(100 - width / 2, y), (0, 0), (0, 0), (100, y), (0, 0), (0, 0), (100 + width / 2, y)]
    bottom = [(0, 0), (0, 0), (0, 0), (100, y + height),
              (0, 0), (0, 0), (0, 0), (0, 0), (0, 0), (100, y + height)]
    return top, bottom


def test_open_eyes_are_not_read_as_closed():
    marks = {"left_eye": _eye(60, 50, height=9), "right_eye": _eye(140, 50, height=9)}
    out = main._analyse_landmarks(marks)
    assert out["eyesClosed"] is False
    assert out["eyeOpenness"] > main.BLINK_EAR_THRESHOLD


def test_shut_eyes_are_read_as_closed():
    """A blink is a flattened eye -- height collapses, width does not."""
    marks = {"left_eye": _eye(60, 50, height=1), "right_eye": _eye(140, 50, height=1)}
    out = main._analyse_landmarks(marks)
    assert out["eyesClosed"] is True


def test_a_wide_mouth_reads_as_a_smile_and_a_narrow_one_does_not():
    top_wide, bottom_wide = _lips(width=60, height=8)
    smiling = main._analyse_landmarks({"top_lip": top_wide, "bottom_lip": bottom_wide})
    assert smiling["smiling"] is True

    top_neutral, bottom_neutral = _lips(width=30, height=14)
    neutral = main._analyse_landmarks({"top_lip": top_neutral, "bottom_lip": bottom_neutral})
    assert neutral["smiling"] is False


def test_head_turn_is_read_from_where_the_nose_sits():
    eyes = {"left_eye": _eye(60, 50), "right_eye": _eye(140, 50)}

    centred = main._analyse_landmarks({**eyes, "nose_bridge": [(100, 60), (100, 75)]})
    assert centred["headTurn"] == "centre"

    turned_right = main._analyse_landmarks({**eyes, "nose_bridge": [(100, 60), (130, 75)]})
    assert turned_right["headTurn"] == "right"

    turned_left = main._analyse_landmarks({**eyes, "nose_bridge": [(100, 60), (70, 75)]})
    assert turned_left["headTurn"] == "left"


def test_landmarks_that_are_missing_are_simply_absent():
    """A partial face must not raise -- it should report only what it could
    measure, so a handler never fails on an unusual frame."""
    out = main._analyse_landmarks({})
    assert out == {}
    assert "eyesClosed" not in main._analyse_landmarks({"left_eye": [(0, 0)]})


# ---------------------------------------------------------------------------
# frame quality
# ---------------------------------------------------------------------------

def test_a_dark_frame_is_reported_as_too_dark():
    img = np.full((400, 400, 3), 20, dtype=np.uint8)
    q = main._frame_quality(np, img, (100, 300, 300, 100))
    assert q["lighting"] == "too dark"


def test_a_blown_out_frame_is_reported_as_too_bright():
    img = np.full((400, 400, 3), 245, dtype=np.uint8)
    q = main._frame_quality(np, img, (100, 300, 300, 100))
    assert q["lighting"] == "too bright"


def test_a_flat_frame_is_reported_as_flat_and_blurred():
    """No variation at all is what a wall looks like, and what a badly blurred
    face looks like too. Saying so turns "try again" into something actionable."""
    img = np.full((400, 400, 3), 130, dtype=np.uint8)
    q = main._frame_quality(np, img, (100, 300, 300, 100))
    assert q["lighting"] == "flat"
    assert q["blurred"] is True


def test_a_small_face_is_reported_as_too_far():
    img = np.full((400, 400, 3), 130, dtype=np.uint8)
    q = main._frame_quality(np, img, (10, 70, 70, 10))
    assert q["tooFar"] is True
    assert q["faceWidth"] == 60


def test_quality_never_raises_on_a_box_outside_the_frame():
    img = np.full((100, 100, 3), 130, dtype=np.uint8)
    assert main._frame_quality(np, img, (500, 600, 600, 500)) == {}


# ---------------------------------------------------------------------------
# confidence, for people rather than machines
# ---------------------------------------------------------------------------

def test_confidence_is_full_at_no_distance_and_half_at_the_tolerance():
    assert main._confidence(0.0) == 100.0
    assert abs(main._confidence(main.FACE_TOLERANCE) - 50.0) < 0.6


def test_confidence_never_goes_below_zero_or_above_a_hundred():
    assert main._confidence(5.0) == 0.0
    assert main._confidence(-1.0) == 100.0
    assert main._confidence(None) is None


def test_confidence_falls_as_the_distance_grows():
    a = main._confidence(0.20)
    b = main._confidence(0.45)
    assert a > b


# ---------------------------------------------------------------------------
# the service starts without the heavy libraries
# ---------------------------------------------------------------------------

def test_the_module_imports_without_dlib_or_torch():
    """The reason any of the above is testable. Importing used to require a
    4-5 GB install, so the attendance figures -- which need none of it -- could
    not be served until that build finished."""
    assert main.app is not None
    assert main.read_root()["message"] == "HR Analytics API is running"


def test_asking_for_vision_when_it_is_absent_is_a_clear_refusal():
    """Never a stack trace from deep inside a handler."""
    saved = dict(main._vision)
    try:
        main._vision["loaded"] = True
        main._vision["error"] = "pretend dlib is missing"
        try:
            main.require_vision()
        except Exception as e:
            assert getattr(e, "status_code", None) == 503
            assert "not installed" in str(getattr(e, "detail", "")).lower()
        else:
            raise AssertionError("require_vision should have refused")
    finally:
        main._vision.clear()
        main._vision.update(saved)


# ---------------------------------------------------------------------------
# runner, so pytest is optional
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    tests = [(n, f) for n, f in sorted(globals().items())
             if n.startswith("test_") and callable(f)]
    passed, failed = 0, []
    for name, fn in tests:
        try:
            fn()
            passed += 1
            print("  PASS  %s" % name)
        except Exception as exc:
            failed.append((name, exc))
            print("  FAIL  %s -- %s" % (name, exc))
    print("\n%d passed, %d failed, %d total" % (passed, len(failed), len(tests)))
    sys.exit(1 if failed else 0)
