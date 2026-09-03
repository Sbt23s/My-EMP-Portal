package com.pixous.hrportal.modules.config;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.user.RoleRepository;
import com.pixous.hrportal.security.UserPrincipal;
import com.pixous.hrportal.modules.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two rules that matter in the configuration store: a company override
 * never overwrites the platform default, and a platform-only setting is refused
 * to a company administrator regardless of what the client shows them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigServiceTest {

    @Mock AppSettingRepository settingRepository;
    @Mock ConfigOptionSetRepository optionSetRepository;
    @Mock ConfigOptionRepository optionRepository;
    @Mock RoleRepository roleRepository;

    @InjectMocks ConfigService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Signs in a principal with the given company and authorities. */
    private void signIn(Long companyId, String... authorities) {
        User user = new User();
        user.setId(7L);
        user.setCompanyId(companyId);
        user.setUsername("tester");
        user.setPasswordHash("x");
        user.setEnabled(true);
        UserPrincipal principal = new UserPrincipal(user);
        var auths = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(a -> (org.springframework.security.core.GrantedAuthority) a)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, auths));
    }

    private AppSetting platformDefault(String key, String value, boolean platformOnly) {
        AppSetting s = new AppSetting();
        s.setId(1L);
        s.setCompanyId(null);
        s.setSettingKey(key);
        s.setSettingValue(value);
        s.setValueType("INT");
        s.setCategory("LEAVE");
        s.setPlatformOnly(platformOnly);
        s.setEditable(true);
        return s;
    }

    @Test
    void companyEditCreatesItsOwnRowAndLeavesTheDefaultAlone() {
        signIn(42L, "CONFIG_MANAGE");
        AppSetting def = platformDefault("leave.tl_approval_max_days", "3", false);
        when(settingRepository.findPlatformDefault("leave.tl_approval_max_days"))
                .thenReturn(Optional.of(def));
        when(settingRepository.findBySettingKeyAndCompanyId("leave.tl_approval_max_days", 42L))
                .thenReturn(Optional.empty());
        when(settingRepository.save(any(AppSetting.class))).thenAnswer(i -> i.getArgument(0));

        var saved = service.updateSetting(
                new ConfigDTOs.UpdateSettingRequest("leave.tl_approval_max_days", "5"));

        // The company gets its own row...
        assertThat(saved.value()).isEqualTo("5");
        assertThat(saved.inherited()).isFalse();
        // ...and the shared default is untouched, so other tenants still read 3.
        assertThat(def.getSettingValue()).isEqualTo("3");
    }

    @Test
    void platformAdminWithoutCompanyEditsTheDefaultItself() {
        signIn(null, "CONFIG_MANAGE", "ROLE_SUPER_ADMIN");
        AppSetting def = platformDefault("leave.min_notice_days", "1", false);
        when(settingRepository.findPlatformDefault("leave.min_notice_days"))
                .thenReturn(Optional.of(def));
        when(settingRepository.save(any(AppSetting.class))).thenAnswer(i -> i.getArgument(0));

        var saved = service.updateSetting(
                new ConfigDTOs.UpdateSettingRequest("leave.min_notice_days", "2"));

        assertThat(saved.inherited()).isTrue();
        assertThat(def.getSettingValue()).isEqualTo("2");
    }

    @Test
    void companyAdminIsRefusedAPlatformOnlySetting() {
        signIn(42L, "CONFIG_MANAGE");
        when(settingRepository.findPlatformDefault("system.maintenance_mode"))
                .thenReturn(Optional.of(platformDefault("system.maintenance_mode", "false", true)));

        assertThatThrownBy(() -> service.updateSetting(
                new ConfigDTOs.UpdateSettingRequest("system.maintenance_mode", "true")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("platform administrator");

        verify(settingRepository, never()).save(any());
    }

    @Test
    void aValueThatDoesNotMatchItsTypeIsRefused() {
        signIn(42L, "CONFIG_MANAGE");
        when(settingRepository.findPlatformDefault("leave.min_notice_days"))
                .thenReturn(Optional.of(platformDefault("leave.min_notice_days", "1", false)));

        assertThatThrownBy(() -> service.updateSetting(
                new ConfigDTOs.UpdateSettingRequest("leave.min_notice_days", "soon")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("type INT");

        verify(settingRepository, never()).save(any());
    }

    @Test
    void anOverrideHidesTheDefaultOfTheSameKeyInTheListing() {
        signIn(42L, "CONFIG_MANAGE");
        AppSetting def = platformDefault("payroll.cutoff_day", "25", false);
        AppSetting override = platformDefault("payroll.cutoff_day", "28", false);
        override.setId(2L);
        override.setCompanyId(42L);
        // Default first, so the listing has to prefer the override deliberately
        // rather than by relying on the order rows come back in.
        when(settingRepository.findVisibleTo(42L)).thenReturn(List.of(def, override));

        var listed = service.listSettings(null);

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).value()).isEqualTo("28");
        assertThat(listed.get(0).inherited()).isFalse();
    }

    @Test
    void deactivatingAnOptionKeepsTheRowSoExistingRecordsStillReadIt() {
        signIn(42L, "CONFIG_MANAGE");
        ConfigOptionSet set = new ConfigOptionSet();
        set.setId(9L);
        set.setSetCode("leave.request_reason");
        when(optionSetRepository.findByCodeForCompany("leave.request_reason", 42L))
                .thenReturn(List.of(set));

        ConfigOption option = new ConfigOption();
        option.setId(3L);
        option.setOptionSetId(9L);
        option.setOptionCode("EXAM");
        option.setActive(true);
        option.setDefault(true);
        when(optionRepository.findByOptionSetIdAndOptionCode(9L, "EXAM"))
                .thenReturn(Optional.of(option));

        service.deactivateOption("leave.request_reason", "EXAM");

        assertThat(option.isActive()).isFalse();
        // A deactivated value must not stay the default for new records.
        assertThat(option.isDefault()).isFalse();
        verify(optionRepository).save(option);
        verify(optionRepository, never()).delete(any());
    }

    @Test
    void markingAnOptionDefaultClearsThePreviousOne() {
        signIn(42L, "CONFIG_MANAGE");
        ConfigOptionSet set = new ConfigOptionSet();
        set.setId(9L);
        set.setSetCode("helpdesk.priority");
        when(optionSetRepository.findByCodeForCompany("helpdesk.priority", 42L))
                .thenReturn(List.of(set));

        ConfigOption wasDefault = new ConfigOption();
        wasDefault.setId(1L);
        wasDefault.setOptionSetId(9L);
        wasDefault.setOptionCode("MEDIUM");
        wasDefault.setDefault(true);

        ConfigOption becoming = new ConfigOption();
        becoming.setId(2L);
        becoming.setOptionSetId(9L);
        becoming.setOptionCode("HIGH");

        when(optionRepository.findByOptionSetIdAndOptionCode(9L, "HIGH"))
                .thenReturn(Optional.of(becoming));
        when(optionRepository.findByOptionSetIdOrderBySortOrderAscLabelAsc(9L))
                .thenReturn(List.of(wasDefault, becoming));
        when(optionRepository.save(any(ConfigOption.class))).thenAnswer(i -> i.getArgument(0));

        service.saveOption("helpdesk.priority",
                new ConfigDTOs.SaveOptionRequest("HIGH", "High", 3, true, true, null));

        assertThat(becoming.isDefault()).isTrue();
        assertThat(wasDefault.isDefault()).isFalse();
    }
}
