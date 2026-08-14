import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/core/auth/mobile_access.dart';
import 'package:hr_portal_mobile/models/auth_user.dart';

AuthUser _userWith(List<String> roles) => AuthUser.fromJson({
      'id': 1,
      'name': 'Test Person',
      'username': 'test',
      'roles': roles,
      'permissions': <String>[],
    });

void main() {
  group('MobileAccess', () {
    test('lets employees, team leads and HR in — every industry variant', () {
      // The codes people actually hold. Checking only the four generic ones
      // would turn away an entire company whose roles are the IT_ or CV_ set.
      for (final role in [
        'EMPLOYEE', 'IT_EMP', 'CV_EMP',
        'TEAM_LEAD', 'IT_TL', 'CV_SUP',
        'HR_MANAGER', 'IT_HR', 'CV_HR', 'IT_MGR',
      ]) {
        expect(
          MobileAccess.allows(_userWith([role])),
          isTrue,
          reason: '$role should be able to use the app',
        );
      }
    });

    test('turns administrators away', () {
      for (final role in [
        'SUPER_ADMIN', 'COMPANY_ADMIN', 'BOARD_ADMIN',
        'TECHNICAL_ADMIN', 'CV_ADM', 'IT_ADM',
      ]) {
        expect(
          MobileAccess.allows(_userWith([role])),
          isFalse,
          reason: '$role should be sent to the web portal',
        );
      }
    });

    test('one administrator role is enough, whatever else is held', () {
      // The case that matters: an administrator who is also on the employee
      // list. Reading only the first role would let them straight in.
      expect(MobileAccess.allows(_userWith(['IT_EMP', 'COMPANY_ADMIN'])), isFalse);
      expect(MobileAccess.allows(_userWith(['COMPANY_ADMIN', 'IT_EMP'])), isFalse);
    });

    test('is not case sensitive', () {
      // Role codes come back from the server in whatever case the row holds.
      expect(MobileAccess.allows(_userWith(['company_admin'])), isFalse);
      expect(MobileAccess.allows(_userWith(['Super_Admin'])), isFalse);
    });

    test('an account with no roles is let through, not turned away', () {
      // It is a broken account, not an administrator. Showing it an app with
      // nothing in it is truer than telling it to go and find a browser.
      expect(MobileAccess.allows(_userWith([])), isTrue);
    });

    test('the refusal says the account is fine and names where to go', () {
      // The failure this sentence prevents: an administrator reads "cannot sign
      // in", assumes a typo, and retypes a correct password forever.
      expect(MobileAccess.refusal.toLowerCase(), contains('web portal'));
      expect(MobileAccess.refusal.toLowerCase(), contains('works normally'));
      expect(MobileAccess.refusal.toLowerCase(), contains('employees'));
    });
  });
}
