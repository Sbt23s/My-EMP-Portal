import os
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import mysql.connector
from datetime import datetime, timedelta
from typing import List, Dict, Any
from fastapi import File, UploadFile, HTTPException
import re

# ---------------------------------------------------------------------------
# The heavy libraries are loaded when they are first needed, not on import.
#
# face_recognition compiles dlib from source and easyocr pulls in PyTorch —
# together a 4-5 GB install and a long build. Importing them at the top meant
# the service could not start at all without them, so the attendance and payroll
# figures the dashboard asks for — which need none of this — were held hostage to
# a twenty-minute build.
#
# Now they load on the first call to an endpoint that uses them. Everything else
# starts in a second with fastapi, uvicorn and the MySQL driver alone, and the
# face and OCR endpoints report honestly that they are unavailable until the
# libraries are installed.
# ---------------------------------------------------------------------------

_vision = {"loaded": False, "error": None, "np": None, "face": None,
           "Image": None, "ImageOps": None, "cv2": None,
           "pytesseract": None, "ocr_reader": None}


def load_vision():
    """Imports the vision stack once. Returns None when it is unavailable."""
    if _vision["loaded"]:
        return None if _vision["error"] else _vision
    _vision["loaded"] = True
    try:
        import numpy as np
        import face_recognition
        from PIL import Image, ImageOps
        import cv2
        import pytesseract

        _vision.update({"np": np, "face": face_recognition, "Image": Image,
                        "ImageOps": ImageOps, "cv2": cv2, "pytesseract": pytesseract})

        # The OCR model is large; built once and kept. gpu=False unless CUDA is
        # known to be configured, which on a shared host it is not.
        try:
            import easyocr
            _vision["ocr_reader"] = easyocr.Reader(['en'], gpu=False)
        except Exception as e:
            print("EasyOCR unavailable:", e)
            _vision["ocr_reader"] = None

        return _vision
    except Exception as e:
        _vision["error"] = str(e)
        print("Vision libraries unavailable:", e)
        return None


def require_vision():
    """Raises a clear 503 instead of failing obscurely deep inside a handler."""
    v = load_vision()
    if v is None:
        raise HTTPException(
            status_code=503,
            detail="Face recognition and OCR are not installed on this service. "
                   "Install the full requirements (see analytics-service/requirements.txt) "
                   "or run the Docker image, which includes them."
        )
    return v


FACES_DIR = "faces"
if not os.path.exists(FACES_DIR):
    os.makedirs(FACES_DIR)

app = FastAPI(title="HR Analytics Service")

# Allow requests from the React frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5174", "http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def get_db_connection():
    try:
        return mysql.connector.connect(
            host=os.getenv("DB_HOST", "localhost"),
            # The port was assumed to be 3306. It is not on a machine that
            # already runs a MySQL of its own — the portal's sits on 3307 there —
            # and without this the service could only ever reach the other one.
            port=int(os.getenv("DB_PORT", "3306")),
            user=os.getenv("DB_USER", "root"),
            password=os.getenv("DB_PASSWORD", "root123"),
            database=os.getenv("DB_NAME", "hr")
        )
    except mysql.connector.Error as err:
        print(f"Error: {err}")
        raise HTTPException(status_code=500, detail="Database connection failed")

@app.get("/")
def read_root():
    return {"message": "HR Analytics API is running"}

@app.get("/api/analytics/employee/{user_id}")
def get_employee_analytics(user_id: int):
    conn = get_db_connection()
    cursor = conn.cursor(dictionary=True)
    
    try:
        # 1. Weekly Work Hours Trend (Last 7 Days)
        cursor.execute("""
            SELECT work_date, worked_minutes 
            FROM attendance 
            WHERE user_id = %s 
            ORDER BY work_date DESC LIMIT 7
        """, (user_id,))
        recent_attendance = cursor.fetchall()
        
        # Reverse to show chronological order
        recent_attendance.reverse()
        work_hours_trend = [
            {
                "date": rec['work_date'].strftime('%Y-%m-%d') if rec['work_date'] else 'N/A',
                "hours": round((rec['worked_minutes'] or 0) / 60.0, 1)
            }
            for rec in recent_attendance
        ]
        
        # 2. Leave Utilization Prediction
        cursor.execute("""
            SELECT lb.allocated, lb.used, lt.name as leave_type_name
            FROM leave_balances lb
            JOIN leave_types lt ON lb.leave_type_id = lt.id
            WHERE lb.user_id = %s AND lb.year = %s
        """, (user_id, datetime.now().year))
        leave_balances = cursor.fetchall()
        
        high_risk_leaves = []
        for lb in leave_balances:
            if lb['allocated'] > 0:
                utilization_rate = (lb['used'] / lb['allocated']) * 100
                if utilization_rate > 80:
                    high_risk_leaves.append({
                        "name": lb['leave_type_name'],
                        "warning": f"You have used {lb['used']} out of {lb['allocated']} days. Very few remaining."
                    })
                    
        # 3. Overall Attendance Score (Based on late punch-ins vs on-time)
        cursor.execute("""
            SELECT COUNT(*) as total_days, SUM(is_late = 1) as late_days
            FROM attendance 
            WHERE user_id = %s
        """, (user_id,))
        attendance_stats = cursor.fetchone()
        
        punctuality_score = 100
        if attendance_stats and attendance_stats['total_days'] > 0:
            punctuality_score = round(
                100 - ((attendance_stats['late_days'] / attendance_stats['total_days']) * 100)
            )

        return {
            "success": True,
            "data": {
                "workHoursTrend": work_hours_trend,
                "highRiskLeaves": high_risk_leaves,
                "punctualityScore": punctuality_score,
                "insight": f"Your punctuality score is {punctuality_score}%. " + 
                           ("Keep up the great work!" if punctuality_score >= 90 else "Try to arrive on time more often.")
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cursor.close()
        conn.close()

@app.get("/api/analytics/executive")
def get_executive_analytics(industry: str = "ALL"):
    conn = get_db_connection()
    cursor = conn.cursor(dictionary=True)
    
    try:
        # Get list of unique departments.
        #
        # department_title, not department. There is a "department" column in
        # this database but it is on payslips, so this query failed outright
        # with "unknown column" and took the whole executive endpoint down
        # with it -- the dashboard swallowed the 500 and showed empty widgets.
        cursor.execute(
            "SELECT DISTINCT department_title FROM users "
            "WHERE department_title IS NOT NULL AND department_title <> ''"
        )
        depts = [r['department_title'] for r in cursor.fetchall()]
        
        dept_analytics = []
        for dept in depts:
            query = """
                SELECT COUNT(*) as total, SUM(is_late = 1) as late, SUM(status = 'PRESENT' OR status = 'WFH') as present
                FROM attendance a
                JOIN users u ON a.user_id = u.id
                WHERE u.department_title = %s
            """
            params = [dept]
            if industry != "ALL":
                query += " AND u.industry = %s"
                params.append(industry)
                
            cursor.execute(query, tuple(params))
            stats = cursor.fetchone()
            if stats and stats['total'] > 0:
                late_rate = round((stats['late'] / stats['total']) * 100)
                present_rate = round((stats['present'] / stats['total']) * 100)
                dept_analytics.append({
                    "department": dept,
                    "lateRate": late_rate,
                    "attendanceRate": present_rate
                })
        
        query_total = "SELECT COUNT(*) as total FROM users"
        params_total = []
        if industry != "ALL":
            query_total += " WHERE industry = %s"
            params_total.append(industry)
            
        cursor.execute(query_total, tuple(params_total))
        total_users = cursor.fetchone()['total']
        
        insight = f"Organization analytics computed for {total_users} active employees."
        if industry != "ALL":
            insight += f" Filtered by {'Digital' if industry == 'IT' else 'Infra'} team."
        else:
            insight += " Overview of all Digital and Infra segments."
            
        return {
            "success": True,
            "data": {
                "departmentStats": dept_analytics,
                "insight": insight
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cursor.close()
        conn.close()

# A face never matches its enrolment photo exactly, so the comparison allows a
# distance. 0.6 is the library's default and is loose enough to confuse siblings;
# 0.5 is the usual working value and is what this uses.
FACE_TOLERANCE = float(os.getenv("FACE_TOLERANCE", "0.5"))

# Enrolment keeps several encodings per person rather than one. A single photo
# means one pose, one light, one day — and then a genuine person in a different
# light is turned away. A punch matches if it is close to any of them.
MAX_ENROLMENTS = int(os.getenv("FACE_MAX_ENROLMENTS", "5"))


# ---------------------------------------------------------------------------
# What can actually be measured from a face, and what cannot
#
# Everything below is computed from the 68 facial landmarks the library already
# produces, plus the pixels themselves. That covers a genuine set of signals:
# whether eyes are closed (blink), whether the mouth is stretched (smile), which
# way the head is turned, how well lit and how sharp the frame is, how large the
# face is, how many faces are present, and how close the match is.
#
# Deliberately NOT claimed: deepfake detection, mask detection, sunglass
# detection and video-replay detection. Each needs a trained model this service
# does not have, and reporting them from landmarks would be a guess dressed up as
# a measurement. A verification result that says "deepfake: clean" without
# checking is worse than one that does not mention it.
# ---------------------------------------------------------------------------

# Eyes narrower than this are closed. The eye aspect ratio is the standard
# measure: eye height over eye width, which barely varies between people.
BLINK_EAR_THRESHOLD = float(os.getenv("FACE_BLINK_EAR", "0.21"))
# A mouth wider relative to its height than this is a smile rather than a
# neutral face.
SMILE_RATIO_THRESHOLD = float(os.getenv("FACE_SMILE_RATIO", "3.4"))
# How far the nose has to sit from the midpoint between the eyes, as a fraction
# of eye separation, before the head counts as turned.
HEAD_TURN_THRESHOLD = float(os.getenv("FACE_HEAD_TURN", "0.16"))


def _face_path(user_id: int) -> str:
    return os.path.join(FACES_DIR, f"{user_id}.npy")


def _dist(a, b):
    return ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** 0.5


def _eye_aspect_ratio(eye):
    """Height over width for one eye. Small means shut."""
    if len(eye) < 6:
        return None
    width = _dist(eye[0], eye[3])
    if width <= 0:
        return None
    height = (_dist(eye[1], eye[5]) + _dist(eye[2], eye[4])) / 2.0
    return height / width


def _analyse_landmarks(marks):
    """Blink, smile and head turn, from the 68 landmarks.

    Returned as measurements with the thresholds alongside, so a caller can see
    why something was decided rather than only what was decided.
    """
    out = {}

    left = marks.get("left_eye") or []
    right = marks.get("right_eye") or []
    ears = [e for e in (_eye_aspect_ratio(left), _eye_aspect_ratio(right)) if e is not None]
    if ears:
        ear = sum(ears) / len(ears)
        out["eyeOpenness"] = round(ear, 4)
        out["eyesClosed"] = ear < BLINK_EAR_THRESHOLD
        out["eyeThreshold"] = BLINK_EAR_THRESHOLD

    top = marks.get("top_lip") or []
    bottom = marks.get("bottom_lip") or []
    if len(top) >= 7 and len(bottom) >= 7:
        mouth_w = _dist(top[0], top[6])
        mouth_h = abs(((bottom[3][1] + bottom[9][1]) / 2.0 if len(bottom) > 9 else bottom[3][1])
                      - top[3][1]) or 1.0
        ratio = mouth_w / mouth_h
        out["mouthRatio"] = round(ratio, 3)
        out["smiling"] = ratio > SMILE_RATIO_THRESHOLD
        out["smileThreshold"] = SMILE_RATIO_THRESHOLD

    nose = marks.get("nose_bridge") or []
    if left and right and nose:
        lc = (sum(p[0] for p in left) / len(left), sum(p[1] for p in left) / len(left))
        rc = (sum(p[0] for p in right) / len(right), sum(p[1] for p in right) / len(right))
        span = _dist(lc, rc) or 1.0
        midpoint = (lc[0] + rc[0]) / 2.0
        offset = (nose[-1][0] - midpoint) / span
        out["headOffset"] = round(offset, 4)
        out["headTurn"] = ("right" if offset > HEAD_TURN_THRESHOLD
                           else "left" if offset < -HEAD_TURN_THRESHOLD
                           else "centre")
        out["headTurnThreshold"] = HEAD_TURN_THRESHOLD

    return out


def _frame_quality(np, image, box):
    """How usable the frame is: brightness, contrast, sharpness, face size.

    A verification that fails is far more often a bad frame than a wrong person,
    and saying which turns "try again" into something actionable.
    """
    top, right, bottom, left = box
    h, w = image.shape[0], image.shape[1]
    face = image[max(0, top):min(h, bottom), max(0, left):min(w, right)]
    if face.size == 0:
        return {}

    grey = face.mean(axis=2) if face.ndim == 3 else face
    brightness = float(grey.mean())
    contrast = float(grey.std())
    # Variance of the second difference stands in for a Laplacian: a blurred
    # frame has very little of it.
    sharpness = float(np.var(np.diff(grey, n=2, axis=0))) if grey.shape[0] > 2 else 0.0

    face_w, face_h = right - left, bottom - top
    coverage = (face_w * face_h) / float(w * h) if w and h else 0.0

    lighting = ("too dark" if brightness < 55
                else "too bright" if brightness > 215
                else "flat" if contrast < 22
                else "good")

    return {
        "brightness": round(brightness, 1),
        "contrast": round(contrast, 1),
        "sharpness": round(sharpness, 1),
        "lighting": lighting,
        "blurred": sharpness < 12,
        "faceWidth": int(face_w),
        "faceHeight": int(face_h),
        "faceCoverage": round(coverage, 4),
        "tooFar": face_w < 100 or face_h < 100,
        "frameWidth": int(w),
        "frameHeight": int(h),
    }


def _confidence(distance):
    """The match distance as a percentage, for people rather than machines.

    Zero distance is 100%, the tolerance is 50%, and beyond that it falls away.
    Nothing is inferred that the distance did not already say — this is the same
    number in a form somebody can read.
    """
    if distance is None:
        return None
    pct = max(0.0, 100.0 * (1.0 - (distance / (FACE_TOLERANCE * 2.0))))
    return round(min(100.0, pct), 1)


def _load_encodings(np, user_id: int):
    """Enrolments for one person, as a list. Reads both shapes on disk."""
    path = _face_path(user_id)
    if not os.path.exists(path):
        return []
    data = np.load(path)
    # A single 128-value encoding is what the old one-photo enrolment wrote; a
    # 2-D array is the newer several-photo one. Both have to keep working, or
    # everybody already enrolled would have to enrol again.
    if data.ndim == 1:
        return [data]
    return [row for row in data]


@app.get("/api/face/status/{user_id}")
def face_status(user_id: int):
    """Whether this person can use face punch, and from how many photos."""
    v = load_vision()
    if v is None:
        return {"enrolled": False, "photos": 0, "available": False,
                "reason": "The face libraries are not installed on this service."}
    count = len(_load_encodings(v["np"], user_id))
    return {"enrolled": count > 0, "photos": count, "available": True,
            "maxPhotos": MAX_ENROLMENTS, "tolerance": FACE_TOLERANCE}


@app.delete("/api/face/{user_id}")
def forget_face(user_id: int):
    """Removes somebody's enrolment. A face is biometric data; erasing it has to
    be possible, and asking the service to do it is the only honest way."""
    path = _face_path(user_id)
    if os.path.exists(path):
        os.remove(path)
        return {"success": True, "message": "Face data removed"}
    return {"success": True, "message": "Nothing was stored for this user"}


@app.post("/api/face/train/{user_id}")
async def train_face(user_id: int, file: UploadFile = File(...), replace: bool = False):
    try:
        v = require_vision()
        np = v["np"]; face_recognition = v["face"]
        image = face_recognition.load_image_file(file.file)

        # Locate the face first, so "no face" and "several faces" can be told
        # apart. Enrolling from a photo with two people in it silently taught the
        # wrong face before.
        boxes = face_recognition.face_locations(image)
        if len(boxes) == 0:
            raise HTTPException(status_code=400,
                                detail="No face found. Face the camera in good light and try again.")
        if len(boxes) > 1:
            raise HTTPException(status_code=400,
                                detail=f"{len(boxes)} faces in the photo. Only you should be in frame.")

        # A face too small to be measured produces a poor encoding that then fails
        # every verification, so it is refused here rather than stored.
        top, right, bottom, left = boxes[0]
        if (bottom - top) < 80 or (right - left) < 80:
            raise HTTPException(status_code=400,
                                detail="Your face is too small in the frame. Move closer and try again.")

        encodings = face_recognition.face_encodings(image, known_face_locations=boxes)
        if len(encodings) == 0:
            raise HTTPException(status_code=400, detail="Could not read that face. Please try again.")

        existing = [] if replace else _load_encodings(np, user_id)
        existing.append(encodings[0])
        # Oldest first out, so re-enrolling after growing a beard eventually
        # replaces the clean-shaven photos rather than being outvoted by them.
        existing = existing[-MAX_ENROLMENTS:]
        np.save(_face_path(user_id), np.array(existing))

        return {"success": True, "photos": len(existing), "maxPhotos": MAX_ENROLMENTS,
                "message": f"Face saved ({len(existing)} of {MAX_ENROLMENTS} photos)"}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/face/verify/{user_id}")
async def verify_face(
    user_id: int,
    file: UploadFile = File(...),
    # A second frame, taken a moment after the first. Two frames are what make a
    # held-up photograph distinguishable from a person: a person is never
    # perfectly still, a printed photo is. Optional, so an older caller that
    # sends one frame still works — it is told that no liveness check was made
    # rather than being quietly passed as if one had been.
    file2: UploadFile = File(None),
):
    try:
        v = require_vision()
        np = v["np"]; face_recognition = v["face"]

        known = _load_encodings(np, user_id)
        if not known:
            raise HTTPException(status_code=400,
                                detail="No face is enrolled for you yet. Register your face first.")

        live_image = face_recognition.load_image_file(file.file)
        boxes = face_recognition.face_locations(live_image)
        if len(boxes) == 0:
            raise HTTPException(status_code=400, detail="No face in the camera. Face the camera and try again.")
        if len(boxes) > 1:
            raise HTTPException(status_code=400,
                                detail=f"{len(boxes)} faces in the frame. Only you should be in front of the camera.")

        live_encodings = face_recognition.face_encodings(live_image, known_face_locations=boxes)
        if len(live_encodings) == 0:
            raise HTTPException(status_code=400, detail="Could not read that face. Try again in better light.")

        # The closest enrolment decides. Returned as well as the verdict, because
        # "it matched" hides whether it barely matched.
        distances = face_recognition.face_distance(np.array(known), live_encodings[0])
        best = float(np.min(distances))
        matched = best <= FACE_TOLERANCE

        box = boxes[0]
        quality = _frame_quality(np, live_image, box)

        # Blink, smile and head turn, from the landmarks of the frame just taken.
        expressions = {}
        try:
            marks = face_recognition.face_landmarks(live_image, face_locations=[box])
            if marks:
                expressions = _analyse_landmarks(marks[0])
        except Exception:
            expressions = {}

        # ---- liveness ----
        liveness = {"checked": False, "passed": None, "motion": None}
        if file2 is not None:
            try:
                second = face_recognition.load_image_file(file2.file)
                a = live_image.astype("float32")
                b = second.astype("float32")
                if a.shape == b.shape:
                    motion = float(np.mean(np.abs(a - b)))
                    # A live person moves; a photograph held to the lens does not.
                    # The floor is low on purpose — the aim is to reject a still
                    # image, not to demand that somebody wave at the camera.
                    liveness = {"checked": True, "passed": motion >= 0.8,
                                "motion": round(motion, 3)}

                    # If the challenge was to blink or smile or turn, the second
                    # frame is where the answer is, so it is measured too and the
                    # difference reported. A photograph cannot change expression.
                    try:
                        marks2 = face_recognition.face_landmarks(second)
                        if marks2:
                            after = _analyse_landmarks(marks2[0])
                            liveness["second"] = after
                            if "eyeOpenness" in expressions and "eyeOpenness" in after:
                                liveness["blinked"] = (
                                    expressions.get("eyesClosed") is not after.get("eyesClosed"))
                            if "mouthRatio" in expressions and "mouthRatio" in after:
                                liveness["expressionChanged"] = (
                                    abs(after["mouthRatio"] - expressions["mouthRatio"]) > 0.35)
                            if "headOffset" in expressions and "headOffset" in after:
                                liveness["headMoved"] = (
                                    abs(after["headOffset"] - expressions["headOffset"]) > 0.05)
                    except Exception:
                        pass
                else:
                    liveness = {"checked": False, "passed": None, "motion": None,
                                "note": "The two frames were different sizes."}
            except Exception as e:
                liveness = {"checked": False, "passed": None, "motion": None, "note": str(e)}

        # Everything measured, whether it changes the verdict or not, so an admin
        # reading a punch afterwards sees what was actually checked.
        detail = {
            "success": True,
            "match": bool(matched),
            "score": round(best, 4),
            "confidence": _confidence(best),
            "tolerance": FACE_TOLERANCE,
            "enrolledPhotos": len(known),
            "facesInFrame": len(boxes),
            "boundingBox": {"top": box[0], "right": box[1], "bottom": box[2], "left": box[3]},
            "quality": quality,
            "expressions": expressions,
            "liveness": liveness,
            # Named so nobody reads a silence as a pass.
            "notChecked": ["deepfake", "mask", "sunglasses", "video replay"],
        }

        if matched and liveness["checked"] and liveness["passed"] is False:
            detail["match"] = False
            detail["message"] = ("That looks like a photograph rather than a person. "
                                 "Face the camera yourself and try again.")
            return detail

        if not matched:
            detail["message"] = "Face does not match the one enrolled for you"
            if quality.get("blurred"):
                detail["message"] += " — the frame is blurred, so try holding still"
            elif quality.get("lighting") in ("too dark", "too bright", "flat"):
                detail["message"] += f" — the lighting is {quality['lighting']}"
            elif quality.get("tooFar"):
                detail["message"] += " — move closer to the camera"
            return detail

        detail["message"] = "Face verified"
        return detail
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/ocr")
async def extract_number_from_image(file: UploadFile = File(...)):
    try:
        # Load image with PIL to handle EXIF rotation
        v = require_vision()
        np = v["np"]; face_recognition = v["face"]
        Image = v["Image"]; ImageOps = v["ImageOps"]
        cv2 = v["cv2"]; pytesseract = v["pytesseract"]
        ocr_reader = v["ocr_reader"]
        image = Image.open(file.file)
        image = ImageOps.exif_transpose(image)
        
        # Convert to numpy array for EasyOCR & OpenCV
        img_array = np.array(image.convert('RGB'))
        gray = cv2.cvtColor(img_array, cv2.COLOR_RGB2GRAY)
        
        candidates = []
        
        # --- METHOD 1: EasyOCR ---
        if ocr_reader is not None:
            try:
                results = ocr_reader.readtext(
                    img_array, 
                    rotation_info=[90, 180, 270],
                    text_threshold=0.5,
                    low_text=0.3,
                    link_threshold=0.6,
                    width_ths=15.0,
                    mag_ratio=1.5
                )
                
                # Sort bounding boxes left-to-right to ensure correct order
                results.sort(key=lambda x: x[0][0][0])
                
                easyocr_digits = ""
                for (bbox, text, prob) in results:
                    text = text.upper()
                    text = text.replace('O', '0').replace('S', '5').replace('Z', '2').replace('B', '8').replace('G', '6')
                    digits_only = re.sub(r'\D', '', text)
                    easyocr_digits += digits_only
                
                if easyocr_digits:
                    candidates.append(easyocr_digits)
            except Exception as e:
                print("EasyOCR failed:", e)

        # --- METHOD 2: Tesseract OCR (with OpenCV preprocessing) ---
        try:
            # Apply Gaussian Blur to reduce noise
            blurred = cv2.GaussianBlur(gray, (5, 5), 0)
            
            # Apply Otsu's thresholding
            _, thresh = cv2.threshold(blurred, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
            
            # Invert the image (odometers are usually white text on black background)
            thresh_inv = cv2.bitwise_not(thresh)
            
            custom_config = r'--oem 3 -c tessedit_char_whitelist=0123456789'
            
            for processed_img in [thresh, thresh_inv]:
                pil_img = Image.fromarray(processed_img)
                
                # Try rotations
                for angle in [0, 90, 180, 270]:
                    rotated = pil_img.rotate(angle, expand=True)
                    
                    # Try different PSM modes
                    for psm in [7, 11, 8, 6]:
                        cfg = f'{custom_config} --psm {psm}'
                        text = pytesseract.image_to_string(rotated, config=cfg)
                        
                        digits_only = re.sub(r'\D', '', text)
                        if digits_only:
                            candidates.append(digits_only)
        except pytesseract.TesseractNotFoundError:
            print("Tesseract not installed, skipping Method 2.")
        except Exception as e:
            print("Tesseract failed:", e)
            
        # --- PICK THE BEST CANDIDATE ---
        # The best candidate is usually the longest digit sequence
        if candidates:
            best_digits = max(candidates, key=len)
            return {"success": True, "value": int(best_digits)}
        else:
            return {"success": False, "message": "Could not confidently read numbers. Please ensure the image is clear."}
    except Exception as e:
        error_msg = str(e)
        raise HTTPException(status_code=500, detail=error_msg)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8082, reload=True)
