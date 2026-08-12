package com.pixous.hrportal.modules.auth;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.config.AppProperties;
import com.pixous.hrportal.modules.auth.dto.ChangePasswordRequest;
import com.pixous.hrportal.modules.auth.dto.CreateEmployeeRequest;
import com.pixous.hrportal.modules.auth.dto.LoginRequest;
import com.pixous.hrportal.modules.auth.dto.LoginResponse;
import com.pixous.hrportal.modules.auth.dto.RefreshRequest;
import com.pixous.hrportal.modules.auth.dto.SignupRequest;
import com.pixous.hrportal.modules.auth.dto.TokenPair;
import com.pixous.hrportal.modules.user.Role;
import com.pixous.hrportal.modules.user.RoleRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.security.JwtService;

import lombok.extern.slf4j.Slf4j;

/** All authentication flows: login (with lockout), signup, admin create-user, token refresh, password change. */
@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordVault passwordVault;
    private final JwtService jwtService;
    private final AppProperties props;
    private final com.pixous.hrportal.modules.community.CommunityGroupRepository communityGroupRepository;
    private final com.pixous.hrportal.modules.community.CommunityMemberRepository communityMemberRepository;
    private final com.pixous.hrportal.modules.org.CompanyRepository companyRepository;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       LoginHistoryRepository loginHistoryRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordVault passwordVault,
                       JwtService jwtService,
                       AppProperties props,
                       com.pixous.hrportal.modules.community.CommunityGroupRepository communityGroupRepository,
                       com.pixous.hrportal.modules.community.CommunityMemberRepository communityMemberRepository,
                       com.pixous.hrportal.modules.org.CompanyRepository companyRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordVault = passwordVault;
        this.jwtService = jwtService;
        this.props = props;
        this.communityGroupRepository = communityGroupRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.companyRepository = companyRepository;
    }

    /** Every employee must receive company announcements — add them to that channel on creation. */
    private void joinAnnouncementsChannel(User user) {
        communityGroupRepository.findByName("Company Announcements").ifPresent(group -> {
            com.pixous.hrportal.modules.community.CommunityMember member =
                    new com.pixous.hrportal.modules.community.CommunityMember();
            member.setCommunity(group);
            member.setUser(user);
            communityMemberRepository.save(member);
        });
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String username = request.username() == null ? "" : request.username().trim();
        User user = userRepository.findByUsername(username).orElse(null);

        // Fallback: allow login by full employee name (case-insensitive) when the
        // input isn't a known username and exactly one employee has that name.
        // Existing username logins (admin, managers, …) are unaffected.
        if (user == null && !username.isBlank()) {
            java.util.List<User> byName = userRepository.findByNameIgnoreCase(username);
            if (byName.size() == 1) {
                user = byName.get(0);
            }
        }

        if (user == null) {
            recordLogin(null, username, false, ip, userAgent);
            throw new ApiException(ErrorCode.BAD_CREDENTIALS, "Invalid username or password");
        }

        if (user.getCompanyId() == null) {
            // Auto-assign legacy accounts to Pixous Technologies so they can log in
            java.util.Optional<com.pixous.hrportal.modules.org.Company> pixousCompany = companyRepository.findAll().stream()
                    .filter(c -> "Pixous Technologies".equals(c.getCompanyName()))
                    .findFirst();
            if (pixousCompany.isPresent()) {
                user.setCompanyId(pixousCompany.get().getId());
                userRepository.save(user);
            } else {
                recordLogin(user.getId(), username, false, ip, userAgent);
                throw new ApiException(ErrorCode.ACCESS_DENIED, "No companies available in the system.");
            }
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            recordLogin(user.getId(), username, false, ip, userAgent);
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "Account locked due to failed attempts. Try again later.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedAttempt(user);
            recordLogin(user.getId(), username, false, ip, userAgent);
            throw new ApiException(ErrorCode.BAD_CREDENTIALS, "Invalid username or password");
        }

        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Account is disabled. Contact HR.");
        }

        // success
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        recordLogin(user.getId(), username, true, ip, userAgent);

        return buildLoginResponse(user);
    }

    private void handleFailedAttempt(User user) {
        int attempts = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(attempts);
        if (attempts >= props.security().maxFailedLoginAttempts()) {
            user.setLockedUntil(LocalDateTime.now()
                    .plusMinutes(props.security().accountLockMinutes()));
            user.setFailedLoginCount(0);
            log.warn("Account {} locked after repeated failures", user.getUsername());
        }
        userRepository.save(user);
    }

    @Transactional
    public LoginResponse signup(SignupRequest req) {
        if (userRepository.existsByUsername(req.username().trim())) {
            throw ApiException.conflict("An account with this username already exists");
        }
        if (req.aadhar() != null && !req.aadhar().isBlank()
                && userRepository.existsByAadhar(req.aadhar())) {
            throw ApiException.conflict("An account with this Aadhaar already exists");
        }
        if (req.phone() != null && !req.phone().isBlank()
                && userRepository.existsByPhone(req.phone())) {
            throw ApiException.conflict("An account with this phone already exists");
        }

        User user = new User();
        user.setUsername(req.username().trim());
        user.setName(req.name());
        if (req.dob() != null && !req.dob().isBlank()) {
            user.setDob(LocalDate.parse(req.dob()));
        }
        user.setGender(
            req.gender() != null && !req.gender().isBlank()
                ? Character.toUpperCase(req.gender().trim().charAt(0))
                : null
        );
        user.setAadhar(blankToNull(req.aadhar()));
        user.setPhone(blankToNull(req.phone()));
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setPasswordVault(passwordVault.seal(req.password()));
        user.setCareOf(req.careOf());
        user.setHouse(req.house());
        user.setStreet(req.street());
        user.setLocality(req.locality());
        user.setVtc(req.vtc());
        user.setDistrict(req.district());
        user.setState(req.state());
        if (req.country() != null) {
            user.setCountry(req.country());
        }
        user.setPincode(req.pincode());
        user.setPostOffice(req.postOffice());
        user.setIndustry(req.industry() == null ? "IT" : req.industry());
        user.setDepartmentId(req.departmentId());
        user.setDesignationId(req.designationId());
        user.setOfficeLocationId(req.officeLocationId());
        user.setProfileStatus("ACTIVE");
        user.setDateOfJoining(LocalDate.now());

        // default self-service role by industry
        String defaultRole = "CIVIL".equalsIgnoreCase(user.getIndustry()) ? "CV_EMP" : "IT_EMP";
        roleRepository.findByCode(defaultRole).ifPresent(r -> user.getRoles().add(r));

        userRepository.save(user);
        user.setEmployeeCode(generateEmployeeCode(user));
        userRepository.save(user);

        return buildLoginResponse(user);
    }

    /**
     * Admin / HR create-employee flow. Unlike {@link #signup}, this does NOT
     * log the new user in — it returns the created user summary so HR can hand
     * the username + password to the employee. The caller must hold USER_MANAGE.
     */
    @Transactional
    public LoginResponse.AuthUser createEmployee(CreateEmployeeRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        if (username.isBlank()) {
            throw ApiException.business("Username is required");
        }
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("Username '" + username + "' is already taken");
        }
        if (req.aadhar() != null && !req.aadhar().isBlank()
                && userRepository.existsByAadhar(req.aadhar())) {
            throw ApiException.conflict("An account with this Aadhaar already exists");
        }
        if (req.phone() != null && !req.phone().isBlank()
                && userRepository.existsByPhone(req.phone())) {
            throw ApiException.conflict("An account with this phone already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setName(req.name());
        
        Long targetCompanyId = com.pixous.hrportal.security.SecurityUtils.currentCompanyId();
        if (targetCompanyId == null && req.companyId() != null && !req.companyId().isBlank()) {
            targetCompanyId = companyRepository.findAll().stream()
                    .filter(c -> req.companyId().equals(c.getCompanyId()))
                    .findFirst()
                    .map(com.pixous.hrportal.modules.org.Company::getId)
                    .orElseThrow(() -> ApiException.business("Invalid company ID: " + req.companyId()));
        }
        if (targetCompanyId == null) {
            throw ApiException.business("Company ID is required to create a user");
        }
        user.setCompanyId(targetCompanyId);
        
        if (req.dob() != null && !req.dob().isBlank()) {
            user.setDob(LocalDate.parse(req.dob()));
        }
        user.setGender(
            req.gender() != null && !req.gender().isBlank()
                ? Character.toUpperCase(req.gender().trim().charAt(0))
                : null
        );
        user.setAadhar(blankToNull(req.aadhar()));
        user.setPhone(blankToNull(req.phone()));
        user.setEmail(blankToNull(req.email()));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setPasswordVault(passwordVault.seal(req.password()));
        user.setCareOf(req.careOf());
        user.setHouse(req.house());
        user.setStreet(req.street());
        user.setLocality(req.locality());
        user.setVtc(req.vtc());
        user.setDistrict(req.district());
        user.setState(req.state());
        if (req.country() != null && !req.country().isBlank()) {
            user.setCountry(req.country());
        }
        user.setPincode(req.pincode());
        user.setPostOffice(req.postOffice());
        user.setIndustry(normalizeIndustry(req.industry()));
        user.setDepartmentId(req.departmentId());
        user.setDesignationId(req.designationId());
        user.setOfficeLocationId(req.officeLocationId());
        user.setReportingManagerId(req.reportingManagerId());

        // extra profile detail
        user.setPan(blankToNull(req.pan()));
        user.setPfNumber(blankToNull(req.pfNumber()));
        user.setAlternatePhone(blankToNull(req.alternatePhone()));
        user.setEmergencyContact(blankToNull(req.emergencyContact()));
        user.setEmergencyContactRelation(blankToNull(req.emergencyContactRelation()));
        user.setBloodGroup(blankToNull(req.bloodGroup()));
        user.setPersonalEmail(blankToNull(req.personalEmail()));
        user.setDocuments(blankToNull(req.documents()));
        user.setDesignationTitle(blankToNull(req.designationTitle()));
        user.setDepartmentTitle(blankToNull(req.departmentTitle()));
        user.setPositionTitle(blankToNull(req.positionTitle()));

        String status = (req.profileStatus() != null && !req.profileStatus().isBlank())
                ? req.profileStatus().trim().toUpperCase()
                : "ACTIVE";
        user.setProfileStatus(status);
        user.setEnabled(!"OFFBOARDED".equals(status));
        user.setDateOfJoining(req.dateOfJoining() != null && !req.dateOfJoining().isBlank()
                ? LocalDate.parse(req.dateOfJoining())
                : LocalDate.now());

        // role: explicit if provided & valid, otherwise a self-service role by industry
        String roleCode = (req.roleCode() != null && !req.roleCode().isBlank())
                ? req.roleCode().trim()
                : ("CIVIL".equalsIgnoreCase(user.getIndustry()) ? "CV_EMP" : "IT_EMP");
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> ApiException.business("Unknown role: " + roleCode));
        user.getRoles().add(role);

        userRepository.save(user);
        // The company's own ID when one was supplied, which an Excel import always
        // has. Generating one instead meant somebody who is PIX-E057 everywhere in
        // the company appeared throughout the portal as EMP0061.
        user.setEmployeeCode(chooseEmployeeCode(req.employeeCode(), user));
        userRepository.save(user);
        joinAnnouncementsChannel(user);

        return toAuthUser(user);
    }

    /**
     * The supplied employee ID if it is usable, otherwise a generated one.
     *
     * <p>A duplicate is refused rather than silently replaced: two people sharing
     * an employee ID is worse than an import failing on the row that would have
     * caused it, because the collision is invisible afterwards.
     */
    private String chooseEmployeeCode(String supplied, User user) {
        String code = supplied == null ? "" : supplied.trim().toUpperCase();
        if (code.isEmpty()) return generateEmployeeCode(user);
        if (userRepository.existsByEmployeeCodeIgnoreCase(code)) {
            throw ApiException.conflict("Employee ID '" + code + "' is already in use");
        }
        return code;
    }

    @Transactional
    public TokenPair refresh(RefreshRequest req) {
        RefreshToken stored = refreshTokenRepository.findByToken(req.refreshToken())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_EXPIRED, "Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Refresh token expired, please log in again");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.notFound("User"));

        // rotate
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.BAD_CREDENTIALS, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        // Keep the readable copy current, so what HR sees is what the employee
        // actually signs in with rather than the one they were handed.
        user.setPasswordVault(passwordVault.seal(req.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId); // force re-login elsewhere
    }

    @Transactional(readOnly = true)
    public boolean phoneExists(String phone) {
        return userRepository.existsByPhone(phone);
    }

    @Transactional(readOnly = true)
    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username == null ? "" : username.trim());
    }

    // ---- helpers ----

    @Transactional(readOnly = true)
    public LoginResponse.AuthUser currentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        return toAuthUser(user);
    }

    private LoginResponse.AuthUser toAuthUser(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getCode).toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .toList();
        
        String companyName = "Company";
        if (user.getCompanyId() != null) {
            companyName = companyRepository.findById(user.getCompanyId())
                    .map(com.pixous.hrportal.modules.org.Company::getCompanyName)
                    .orElse("Company");
        }
        
        return new LoginResponse.AuthUser(
                user.getId(), user.getEmployeeCode(), user.getUsername(), user.getName(),
                user.getAadhar(), user.getEmail(), user.getPhone(), user.getIndustry(),
                user.getPhotoPath(), companyName, roles, permissions);
    }

    private LoginResponse buildLoginResponse(User user) {
        TokenPair tokens = issueTokens(user);
        return new LoginResponse(tokens, toAuthUser(user));
    }

    private TokenPair issueTokens(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getCode).toList();
        String access = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);

        RefreshToken refresh = new RefreshToken();
        refresh.setUserId(user.getId());
        refresh.setToken(UUID.randomUUID() + "-" + UUID.randomUUID());
        refresh.setExpiresAt(LocalDateTime.now()
                .plusSeconds(props.jwt().refreshTokenTtlSeconds()));
        refreshTokenRepository.save(refresh);

        return new TokenPair(access, refresh.getToken(), "Bearer", jwtService.getAccessTtlSeconds());
    }

    private void recordLogin(Long userId, String username, boolean success, String ip, String ua) {
        LoginHistory history = new LoginHistory();
        history.setUserId(userId);
        history.setUsername(username);
        history.setSuccess(success);
        history.setIpAddress(ip);
        history.setUserAgent(ua != null && ua.length() > 250 ? ua.substring(0, 250) : ua);
        loginHistoryRepository.save(history);
    }

    private String generateEmployeeCode(User user) {
        String prefix = "EMP";
        String max = userRepository.findMaxEmployeeCode(prefix);
        int next = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                next = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                next = (int) (userRepository.count() + 1);
            }
        }
        return prefix + String.format("%04d", next);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * Normalize an industry value to the canonical code stored in the DB.
     * The UI renders "DIGITAL"/"INFRA" but the system stores "IT"/"CIVIL";
     * accept either spelling so display labels never leak into the data.
     */
    private String normalizeIndustry(String industry) {
        if (industry == null || industry.isBlank()) {
            return "IT";
        }
        String v = industry.trim().toUpperCase();
        return switch (v) {
            case "CIVIL", "INFRA" -> "CIVIL";
            case "IT", "DIGITAL" -> "IT";
            default -> v;
        };
    }
}
