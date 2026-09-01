package com.pixous.hrportal.modules.auth;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.config.AppProperties;
import com.pixous.hrportal.modules.auth.dto.ChangePasswordRequest;
import com.pixous.hrportal.modules.auth.dto.LoginRequest;
import com.pixous.hrportal.modules.auth.dto.LoginResponse;
import com.pixous.hrportal.modules.auth.dto.RefreshRequest;
import com.pixous.hrportal.modules.auth.dto.SignupRequest;
import com.pixous.hrportal.modules.auth.dto.TokenPair;
import com.pixous.hrportal.modules.community.CommunityGroup;
import com.pixous.hrportal.modules.community.CommunityGroupRepository;
import com.pixous.hrportal.modules.community.CommunityMemberRepository;
import com.pixous.hrportal.modules.org.Company;
import com.pixous.hrportal.modules.org.CompanyRepository;
import com.pixous.hrportal.modules.user.Role;
import com.pixous.hrportal.modules.user.RoleRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService} — the login/lockout/refresh/password flows.
 *
 * <p>All collaborators are mocked; these tests pin down the security behaviour:
 * wrong-password lockout, disabled-account rejection, refresh-token rotation,
 * and password-change session invalidation.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock LoginHistoryRepository loginHistoryRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordVault passwordVault;
    @Mock JwtService jwtService;
    @Mock CommunityGroupRepository communityGroupRepository;
    @Mock CommunityMemberRepository communityMemberRepository;
    @Mock CompanyRepository companyRepository;

    private AuthService authService;

    private static AppProperties props() {
        return new AppProperties(
                new AppProperties.Jwt("test-secret-test-secret-test-secret-test-secret-test-secret", 3600, 3600, "hr-portal"),
                new AppProperties.Cors(List.of()),
                new AppProperties.Storage("local", "/tmp/hr-files"),
                new AppProperties.Attendance(200, 0, 8, "09:00", "18:00"),
                new AppProperties.Security(5, 15),
                new AppProperties.Twilio(false, "", "", "", "+91"),
                new AppProperties.Fast2sms(false, "", "q", "")
        );
    }

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, refreshTokenRepository,
                loginHistoryRepository, passwordEncoder, passwordVault, jwtService, props(),
                communityGroupRepository, communityMemberRepository, companyRepository);
    }

    private User enabledUser(String username, String hash) {
        User user = new User();
        user.setUsername(username);
        user.setName("Test User");
        user.setPasswordHash(hash);
        user.setEnabled(true);
        user.setCompanyId(1L);
        return user;
    }

    // ---------- login ----------

    @Test
    void loginSuccessIssuesTokensAndClearsFailures() {
        User user = enabledUser("admin", "hash");
        when(userRepository.findByUsernameAcrossTenants("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass1234", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), anyString(), any())).thenReturn("access-token");
        when(jwtService.getAccessTtlSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("admin", "pass1234"), "1.2.3.4", "curl");

        assertThat(response.tokens().accessToken()).isEqualTo("access-token");
        assertThat(response.tokens().tokenType()).isEqualTo("Bearer");
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginWithUnknownUserThrowsBadCredentialsAndRecordsFailure() {
        when(userRepository.findByUsernameAcrossTenants("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByNameAcrossTenants("ghost")).thenReturn(List.of());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "x"), "1.2.3.4", "curl"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(ErrorCode.BAD_CREDENTIALS));

        ArgumentCaptor<LoginHistory> history = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(history.capture());
        assertThat(history.getValue().isSuccess()).isFalse();
        assertThat(history.getValue().getIpAddress()).isEqualTo("1.2.3.4");
    }

    @Test
    void loginByFullNameWorksWhenSingleMatch() {
        User user = enabledUser("sethu", "hash");
        when(userRepository.findByUsernameAcrossTenants("Sethu Kumar")).thenReturn(Optional.empty());
        when(userRepository.findByNameAcrossTenants("Sethu Kumar")).thenReturn(List.of(user));
        when(passwordEncoder.matches("pass", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), anyString(), any())).thenReturn("t");
        when(jwtService.getAccessTtlSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("Sethu Kumar", "pass"), "1.2.3.4", "curl");
        assertThat(response.tokens().accessToken()).isEqualTo("t");
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() {
        User user = enabledUser("admin", "hash");
        when(userRepository.findByUsernameAcrossTenants("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong"), "1.2.3.4", "curl"))
                    .isInstanceOf(ApiException.class);
        }

        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getFailedLoginCount()).isZero();

        // The lock check runs BEFORE the password check, so even a correct
        // password is refused while locked (and the password encoder is never
        // consulted — which is exactly the point).
        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "right"), "1.2.3.4", "curl"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(ErrorCode.ACCOUNT_LOCKED));
        verify(passwordEncoder, never()).matches("right", "hash");
    }

    @Test
    void disabledAccountRejectedEvenWithCorrectPassword() {
        User user = enabledUser("left", "hash");
        user.setEnabled(false);
        when(userRepository.findByUsernameAcrossTenants("left")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("left", "right"), "1.2.3.4", "curl"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void legacyUserWithoutCompanyIsAutoAssignedToPixous() {
        User user = enabledUser("old", "hash");
        user.setCompanyId(null);
        Company pixous = new Company();
        pixous.setId(9L);
        pixous.setCompanyName("Pixous Technologies");
        when(userRepository.findByUsernameAcrossTenants("old")).thenReturn(Optional.of(user));
        when(companyRepository.findAll()).thenReturn(List.of(pixous));
        when(passwordEncoder.matches("pass", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), anyString(), any())).thenReturn("t");
        when(jwtService.getAccessTtlSeconds()).thenReturn(3600L);

        authService.login(new LoginRequest("old", "pass"), "1.2.3.4", "curl");

        assertThat(user.getCompanyId()).isEqualTo(9L);
        // Saved once. The company repair now happens after the password has
        // been checked and rides along with the success bookkeeping, rather
        // than being written on its own beforehand -- which had let an
        // unauthenticated request cause a database write by typing a username
        // that happened to exist.
        verify(userRepository, org.mockito.Mockito.times(1)).save(user);
    }

    @Test
    void loginFindsAnAccountBelongingToAnotherCompany() {
        /*
         * The Sethu Technologies case. Login has to resolve a username before
         * it can know the tenant, so it must not use the tenant-filtered
         * lookup -- with the filter active that returns nothing for anyone
         * outside the company already in context, and the sign-in fails as
         * though the username did not exist.
         */
        User user = enabledUser("admin1234", "hash");
        user.setCompanyId(42L);
        when(userRepository.findByUsernameAcrossTenants("admin1234")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin1234", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), anyString(), any())).thenReturn("t");
        when(jwtService.getAccessTtlSeconds()).thenReturn(3600L);

        LoginResponse res = authService.login(
                new LoginRequest("admin1234", "admin1234"), "1.2.3.4", "curl");

        assertThat(res.tokens().accessToken()).isEqualTo("t");
        // Their own company, untouched -- not reassigned to anyone else's.
        assertThat(user.getCompanyId()).isEqualTo(42L);
    }

    @Test
    void loginIsRefusedBeforeAnyCompanyRepairWhenThePasswordIsWrong() {
        // The repair is a write. It must never happen for a request that has
        // not proved who it is.
        User user = enabledUser("old", "hash");
        user.setCompanyId(null);
        when(userRepository.findByUsernameAcrossTenants("old")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("old", "wrong"), "1.2.3.4", "curl"))
                .isInstanceOf(ApiException.class);

        assertThat(user.getCompanyId()).isNull();
        verify(companyRepository, never()).findAll();
    }

    // ---------- refresh ----------

    @Test
    void refreshRotatesTheTokenAndRevokesTheOldOne() {
        User user = enabledUser("admin", "hash");
        RefreshToken stored = new RefreshToken();
        stored.setUserId(1L);
        stored.setToken("old-token");
        stored.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(any(), anyString(), any())).thenReturn("new-access");
        when(jwtService.getAccessTtlSeconds()).thenReturn(3600L);

        TokenPair pair = authService.refresh(new RefreshRequest("old-token"));

        assertThat(pair.accessToken()).isEqualTo("new-access");
        assertThat(stored.isRevoked()).isTrue();
        // Two saves: the revoked old token and the newly issued one.
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refreshWithRevokedTokenIsRejected() {
        RefreshToken stored = new RefreshToken();
        stored.setToken("old-token");
        stored.setRevoked(true);
        stored.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("old-token")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(ErrorCode.TOKEN_EXPIRED));
    }

    @Test
    void refreshWithExpiredTokenIsRejected() {
        RefreshToken stored = new RefreshToken();
        stored.setToken("old-token");
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("old-token")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(ErrorCode.TOKEN_EXPIRED));
    }

    // ---------- change password ----------

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        User user = enabledUser("admin", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1L,
                new ChangePasswordRequest("wrong", "newpassword1")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(ErrorCode.BAD_CREDENTIALS));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordSuccessRevokesAllSessions() {
        User user = enabledUser("admin", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-hash");
        when(passwordVault.seal("newpassword1")).thenReturn("sealed");

        authService.changePassword(1L, new ChangePasswordRequest("old", "newpassword1"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getPasswordVault()).isEqualTo("sealed");
        verify(refreshTokenRepository).revokeAllForUser(1L);
    }

    // ---------- signup ----------

    /** Builds a signup with sensible defaults; override what a test needs. */
    private SignupRequest signup(String username, String name, String password) {
        return new SignupRequest(
                username, name,          // username, name
                null, null, null, null, null, // dob, gender, aadhar, phone, email
                password,                // password
                null, null, null, null, null, null, null, null, null, null, // careOf..postOffice
                "IT",                    // industry
                null, null, null         // departmentId, designationId, officeLocationId
        );
    }

    @Test
    void signupRejectsDuplicateUsername() {
        // Counted against the table, not the entity: the unique index is
        // global and the entity is filtered by company.
        when(userRepository.countByUsernameAcrossTenants("taken")).thenReturn(1L);

        SignupRequest req = signup("taken", "Name", "pass1234");
        assertThatThrownBy(() -> authService.signup(req))
                .isInstanceOf(ApiException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void signupAssignsDefaultRoleAndEncodesPassword() {
        when(userRepository.countByUsernameAcrossTenants("newbie")).thenReturn(0L);
        Role role = new Role();
        role.setCode("IT_EMP");
        when(roleRepository.findByCode("IT_EMP")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("pass1234")).thenReturn("enc");
        when(passwordVault.seal("pass1234")).thenReturn("vault");
        // Allocation reads the table rather than the entity, because the
        // entity carries the tenant filter and the unique index does not.
        when(userRepository.findMaxEmployeeCodeAcrossTenants("EMP")).thenReturn(null);
        when(jwtService.generateAccessToken(any(), anyString(), any())).thenReturn("t");
        when(jwtService.getAccessTtlSeconds()).thenReturn(3600L);

        SignupRequest req = signup("newbie", "New Person", "pass1234");
        LoginResponse response = authService.signup(req);

        // One insert. The employee ID is set before the row is written, so
        // there is no follow-up update to save it.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(1)).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("newbie");
        assertThat(saved.getPasswordHash()).isEqualTo("enc");
        assertThat(saved.getRoles()).containsExactly(role);
        assertThat(saved.getEmployeeCode()).isEqualTo("EMP0001");
        assertThat(response.tokens().accessToken()).isEqualTo("t");
    }
}
