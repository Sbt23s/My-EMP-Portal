import 'directory_person.dart';

/// The signed-in employee's team: its name and the active members in it.
///
/// Mirrors `MyTeamResponse`. The members are the same `UserSummary` rows the
/// directory parses, so [DirectoryPerson] is reused rather than declared twice.
class MyTeam {
  const MyTeam({required this.teamName, required this.members});

  final String teamName;
  final List<DirectoryPerson> members;

  static MyTeam fromJson(Map<String, dynamic> json) => MyTeam(
        teamName: json['teamName']?.toString() ?? 'My team',
        members: (json['members'] as List?)
                ?.whereType<Map>()
                .map((m) => DirectoryPerson.fromJson(Map<String, dynamic>.from(m)))
                .toList() ??
            const [],
      );
}
