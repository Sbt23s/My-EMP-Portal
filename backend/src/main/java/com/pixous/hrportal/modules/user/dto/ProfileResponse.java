package com.pixous.hrportal.modules.user.dto;

import java.time.LocalDate;
import java.util.List;

/** Full self/admin profile view including address block and employment metadata. */
public record ProfileResponse(
        Long id,
        String employeeCode,
        /** Login identifier. The password is a one-way hash and is never returned. */
        String username,
        String name,
        LocalDate dob,
        String gender,
        String aadhar,
        String phone,
        String email,
        String photoPath,
        AddressDto address,
        Long departmentId,
        Long designationId,
        Long officeLocationId,
        Long reportingManagerId,
        String industry,
        String employmentType,
        LocalDate dateOfJoining,
        /** When probation ends. Null until it is set. */
        LocalDate probationEndDate,
        String profileStatus,
        String pan,
        String pfNumber,
        String alternatePhone,
        String emergencyContact,
        String emergencyContactRelation,
        String bloodGroup,
        String personalEmail,
        String designationTitle,
        String departmentTitle,
        String positionTitle,
        List<String> roles,
        /** Comma-separated upload paths: the employee's paperwork. */
        String documents,
        /**
         * The face enrolment, for HR to look at. The photo exists so whoever
         * registered somebody else's face can confirm it was the right person.
         */
        String facePhotoPath,
        java.time.LocalDateTime faceRegisteredAt,
        String faceRegisteredByName
) {
    public record AddressDto(
            String careOf, String house, String street, String locality, String vtc,
            String district, String state, String country, String pincode, String postOffice
    ) {}
}
