package com.pixous.hrportal.modules.biometric.dto;

/**
 * One person on the terminal, and who they are in this portal.
 *
 * <p>Both sides in one row, because the screen's whole job is to let somebody
 * look at them together and say yes or no. Splitting them across two calls
 * would make the comparison the browser's problem.
 *
 * @param hikPersonId   Hikvision's own identifier, and the key a punch arrives
 *                      with. Not shown prominently — it means nothing to a
 *                      reader — but it is what a change is applied against.
 * @param personCode    the employee number as the terminal holds it, e.g.
 *                      "001". Cannot be changed: Hikvision refuses
 *                      {@code /persons/update} on this field.
 * @param terminalName  the name as the terminal holds it, which is usually how
 *                      a person recognises the row
 * @param userId        the employee this identity is mapped to, or null
 * @param employeeCode  that employee's code here, e.g. "PIX-E001"
 * @param employeeName  that employee's name here
 * @param manual        whether somebody matched this by hand, in which case the
 *                      sync leaves it alone
 * @param faceEnrolled  whether a face is enrolled on the terminal
 * @param fingerEnrolled whether a fingerprint is enrolled
 * @param punchesLast30Days how many punches this identity has produced, which
 *                      is the quickest way to see whether a mapping is actually
 *                      being used
 */
public record PersonMappingRow(
        String hikPersonId,
        String personCode,
        String terminalName,
        Long userId,
        String employeeCode,
        String employeeName,
        boolean manual,
        boolean faceEnrolled,
        boolean fingerEnrolled,
        long punchesLast30Days
) {}
