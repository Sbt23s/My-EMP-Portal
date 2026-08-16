import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// A scrollable inside a scrollable throws during layout.
///
/// `LoadingList` is a `ListView`. Dropping one into another `ListView`'s
/// `children` gives it unbounded height, and Flutter throws
/// "Vertical viewport was given unbounded height" — a crash, on every visit,
/// for as long as that screen is loading.
///
/// It happened twice: the Leave screen and then the Leave policies screen, both
/// written the same way an hour apart. `flutter analyze` cannot see it, and a
/// widget test only catches the screen it happens to pump. This reads the source
/// instead, so a third one cannot be written without the suite objecting.
///
/// Deliberately a source scan rather than a render test. Rendering every screen
/// would need every provider stubbed, and the failure being guarded against is
/// visible in the text: an unbounded LoadingList inside a children list.
void main() {
  test('no LoadingList sits unbounded inside a ListView children list', () {
    final offenders = <String>[];

    for (final file in Directory('lib/features')
        .listSync(recursive: true)
        .whereType<File>()
        .where((f) => f.path.endsWith('.dart'))) {
      final lines = file.readAsLinesSync();

      // Depth of an open `ListView(` / `GridView(` whose children we are inside.
      var insideChildren = false;

      for (var i = 0; i < lines.length; i++) {
        final line = lines[i];

        if (RegExp(r'(ListView|GridView|Column|CustomScrollView)\s*\(').hasMatch(line)) {
          insideChildren = true;
        }
        // A bounded box resets the concern: whatever follows has a height.
        if (line.contains('SizedBox(') && line.contains('height:')) {
          insideChildren = false;
        }
        if (RegExp(r'^\s*\)[,;]?\s*$').hasMatch(line)) {
          insideChildren = false;
        }

        if (!line.contains('LoadingList(')) continue;

        // Bounded on this line or the one above it — the usual shapes are
        // `SizedBox(height: 300, child: LoadingList(...))` split across lines.
        final context = (i > 0 ? lines[i - 1] : '') + line;
        final bounded = context.contains('SizedBox') && context.contains('height:');
        if (bounded) continue;

        // The direct body of a RefreshIndicator or a `when(loading:)` that is
        // itself the body — those are bounded by the Scaffold.
        final isChildOfList = insideChildren &&
            !context.contains('body:') &&
            !(i > 0 && lines[i - 1].contains('child:'));

        if (isChildOfList) {
          offenders.add('${file.path.replaceAll(r'\', '/')}:${i + 1}');
        }
      }
    }

    expect(
      offenders,
      isEmpty,
      reason: 'LoadingList is a ListView. Inside another list it needs a bounded '
          'parent — wrap it in SizedBox(height: ..., child: ...).\n'
          'Unbounded at:\n  ${offenders.join('\n  ')}',
    );
  });
}
