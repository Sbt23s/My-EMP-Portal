import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../providers/app_providers.dart';

/// Punching in with your face.
///
/// The same sequence the browser follows, for the same reason: take a selfie,
/// send it to the face service, and only a match becomes a punch. A failed match
/// does not quietly fall back to an ordinary punch — the company's rule is that
/// a punch means a verified face, and the server enforces it too.
///
/// Nothing is matched on the phone. The photo goes to the service the web client
/// uses, so a face that passes at a desk passes on a phone and neither device is
/// the one deciding.
class FacePunchSheet extends ConsumerStatefulWidget {
  const FacePunchSheet({required this.punchIn, super.key});

  final bool punchIn;

  @override
  ConsumerState<FacePunchSheet> createState() => _FacePunchSheetState();
}

enum _Stage { idle, capturing, verifying, punching, done, failed }

class _FacePunchSheetState extends ConsumerState<FacePunchSheet> {
  _Stage _stage = _Stage.idle;
  String? _error;
  double? _score;
  File? _photo;

  String get _verb => widget.punchIn ? 'Punch in' : 'Punch out';

  Future<void> _run() async {
    final user = ref.read(currentUserProvider);
    if (user == null) return;

    setState(() {
      _stage = _Stage.capturing;
      _error = null;
    });

    final picked = await ImagePicker().pickImage(
      source: ImageSource.camera,
      preferredCameraDevice: CameraDevice.front,
      // Resized before it leaves the phone. A modern camera produces a
      // four-megabyte JPEG, the face service needs nothing like that, and on a
      // site with one bar of signal the difference is the punch working or not.
      maxWidth: 720,
      imageQuality: 85,
    );

    if (picked == null) {
      // Cancelled. Not a failure — say nothing and go back to the start.
      if (mounted) setState(() => _stage = _Stage.idle);
      return;
    }

    final photo = File(picked.path);
    if (!mounted) return;
    setState(() {
      _photo = photo;
      _stage = _Stage.verifying;
    });

    try {
      final face = ref.read(faceRepositoryProvider);
      final verdict = await face.verify(userId: user.id, photo: photo);

      if (!verdict.match) {
        if (!mounted) return;
        setState(() {
          _stage = _Stage.failed;
          _score = verdict.score;
          _error = verdict.message ??
              'That face does not match the one enrolled for you.';
        });
        return;
      }

      if (!mounted) return;
      setState(() {
        _stage = _Stage.punching;
        _score = verdict.score;
      });

      // Location travels with it, exactly as an ordinary punch does — a face
      // punch is still checked against the site's geofence.
      final where = await ref.read(punchLocationServiceProvider).current();

      await face.facePunch(
        punchIn: widget.punchIn,
        photo: photo,
        verdict: verdict,
        latitude: where.latitude,
        longitude: where.longitude,
      );

      if (!mounted) return;
      setState(() => _stage = _Stage.done);
      // Left on screen for a moment so the confirmation is actually seen,
      // rather than the sheet vanishing the instant the request returns.
      await Future<void>.delayed(const Duration(milliseconds: 900));
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _stage = _Stage.failed;
        _error = e.toString();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final busy = _stage == _Stage.verifying ||
        _stage == _Stage.punching ||
        _stage == _Stage.capturing;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 20, 24, 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              '$_verb with your face',
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 6),
            Text(
              'Take a selfie. It is checked against the photo enrolled for you '
              'and kept with the punch.',
              style: Theme.of(context)
                  .textTheme
                  .bodySmall
                  ?.copyWith(color: scheme.onSurfaceVariant),
            ),
            const SizedBox(height: 22),

            Center(
              child: SizedBox(
                height: 132,
                width: 132,
                child: _Preview(
                  photo: _photo,
                  stage: _stage,
                ),
              ),
            ),
            const SizedBox(height: 18),

            _StatusLine(stage: _stage, score: _score, error: _error),
            const SizedBox(height: 20),

            FilledButton.icon(
              onPressed: busy ? null : _run,
              icon: Icon(
                _stage == _Stage.failed
                    ? Icons.refresh_rounded
                    : Icons.camera_alt_rounded,
              ),
              label: Text(
                switch (_stage) {
                  _Stage.idle => 'Take selfie',
                  _Stage.capturing => 'Opening camera…',
                  _Stage.verifying => 'Checking your face…',
                  _Stage.punching => 'Recording…',
                  _Stage.done => 'Done',
                  _Stage.failed => 'Try again',
                },
              ),
            ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: busy ? null : () => Navigator.of(context).pop(false),
              child: const Text('Cancel'),
            ),
          ],
        ),
      ),
    );
  }
}

class _Preview extends StatelessWidget {
  const _Preview({required this.photo, required this.stage});

  final File? photo;
  final _Stage stage;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final ring = switch (stage) {
      _Stage.done => Colors.green,
      _Stage.failed => scheme.error,
      _ => scheme.primary,
    };

    return Container(
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: scheme.surfaceContainerHighest,
        border: Border.all(color: ring, width: 3),
      ),
      clipBehavior: Clip.antiAlias,
      child: photo == null
          ? Icon(Icons.person_outline_rounded, size: 56, color: scheme.outline)
          : Image.file(photo!, fit: BoxFit.cover),
    );
  }
}

class _StatusLine extends StatelessWidget {
  const _StatusLine({required this.stage, this.score, this.error});

  final _Stage stage;
  final double? score;
  final String? error;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    if (stage == _Stage.failed) {
      return Text(
        error ?? 'Could not verify your face.',
        textAlign: TextAlign.center,
        style: Theme.of(context)
            .textTheme
            .bodySmall
            ?.copyWith(color: scheme.error, height: 1.4),
      );
    }

    if (stage == _Stage.done) {
      return Text(
        score == null
            ? 'Face matched. Punch recorded.'
            : 'Face matched · score ${score!.toStringAsFixed(3)}',
        textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Colors.green.shade700,
              fontWeight: FontWeight.w600,
            ),
      );
    }

    if (stage == _Stage.verifying || stage == _Stage.punching) {
      return const Center(
        child: SizedBox(
          height: 20,
          width: 20,
          child: CircularProgressIndicator(strokeWidth: 2.2),
        ),
      );
    }

    return const SizedBox(height: 20);
  }
}
