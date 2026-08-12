package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.modules.auth.PasswordVault;
import com.pixous.hrportal.modules.org.Company;
import com.pixous.hrportal.modules.user.Role;
import com.pixous.hrportal.modules.user.RoleRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@org.springframework.context.annotation.Profile("!prod")
public class MultiTenantSeeder implements CommandLineRunner {

    /*
     * Local development only, from here on.
     *
     * This runs on every start and writes: it creates two companies if they are
     * missing, deletes ten named accounts, and adds four roles. That is
     * reasonable against a throwaway local database and indefensible against the
     * live one, where it had already added a company nobody asked for and would
     * delete a real account any time somebody happened to share one of those
     * usernames.
     *
     * Setting up a production tenant is a deliberate act, not something a
     * restart should do quietly.
     */

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CompanyService companyService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordVault passwordVault;

    public MultiTenantSeeder(UserRepository userRepository,
                             RoleRepository roleRepository,
                             CompanyService companyService,
                             PasswordEncoder passwordEncoder,
                             PasswordVault passwordVault) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.companyService = companyService;
        this.passwordEncoder = passwordEncoder;
        this.passwordVault = passwordVault;
    }

    @Override
    public void run(String... args) throws Exception {
        Company sethu = getOrCreateCompany("SETHU-8F42K7", "Sethu Technologies", "IT Services");
        Company pixous = getOrCreateCompany("PIX-MASTER", "Pixous Technologies", "IT Services");

        // Delete fake seeded users that the user hates
        String[] fakeUsernames = {
            "sethu_admin", "sethu_hr", "sethu_tl", "sethu_emp",
            "bala_admin", "bala_hr", "bala_tl", "bala_emp",
            "master_admin", "master_emp"
        };
        for (String un : fakeUsernames) {
            userRepository.findByUsername(un).ifPresent(u -> userRepository.delete(u));
        }

        // ALWAYS fix legacy orphan users, even if already seeded
        for (User u : userRepository.findAll()) {
            if (u.getCompanyId() == null) {
                u.setCompanyId(pixous.getId());
                userRepository.save(u);
            }
            // Explicitly move legacy users out of Sethu if they were accidentally assigned
            String un = u.getUsername() != null ? u.getUsername().toLowerCase() : "";
            String empCode = u.getEmployeeCode() != null ? u.getEmployeeCode().toUpperCase() : "";
            
            if ((un.contains("sethubala") || un.equals("admin") || un.equals("system admin") || empCode.equals("PIX-E057")) 
                && u.getCompanyId() != null 
                && !u.getCompanyId().equals(pixous.getId())) {
                u.setCompanyId(pixous.getId());
                userRepository.save(u);
            }
        }

        if (userRepository.findByUsername("sethu_admin").isPresent()) {
            return; // Already seeded
        }

        // Seed Roles
        String[] requiredRoles = {"COMPANY_ADMIN", "HR_MANAGER", "TEAM_LEAD", "EMPLOYEE"};
        for (String rc : requiredRoles) {
            if (roleRepository.findByCode(rc).isEmpty()) {
                Role role = new Role();
                role.setCode(rc);
                role.setName(rc.replace("_", " "));
                role.setIndustry("BOTH");
                role.setDescription(rc);
                roleRepository.save(role);
            }
        }
    }

    private Company getOrCreateCompany(String companyId, String name, String industry) {
        List<Company> list = companyService.getAllCompanies();
        for (Company c : list) {
            if (companyId.equals(c.getCompanyId())) return c;
        }
        Company c = new Company();
        c.setCompanyId(companyId);
        c.setCompanyName(name);
        c.setIndustry(industry);
        c.setStatus("ACTIVE");
        c.setEmployeeCount(100);
        return companyService.createCompany(c);
    }

    private void seedUser(String username, String name, String email, String roleCode, Company company, String hash, String vault) {
        if (userRepository.findByUsername(username).isPresent()) return;

        User user = new User();
        user.setUsername(username);
        user.setName(name);
        user.setEmail(email);
        user.setCompanyId(company.getId());
        user.setPasswordHash(hash);
        user.setPasswordVault(vault);
        user.setProfileStatus("ACTIVE");

        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        user.setRoles(Set.of(role));

        userRepository.save(user);
    }
}
