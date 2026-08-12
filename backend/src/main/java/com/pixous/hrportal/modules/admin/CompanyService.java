package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.modules.org.Company;
import com.pixous.hrportal.modules.org.CompanyRepository;
import com.pixous.hrportal.modules.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public CompanyService(CompanyRepository companyRepository, UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    public List<Company> getAllCompanies() {
        List<Company> companies = companyRepository.findAll();
        for (Company c : companies) {
            c.setEmployeeCount((int) userRepository.countByCompanyId(c.getId()));
        }
        return companies;
    }

    public Optional<Company> getCompanyById(Long id) {
        return companyRepository.findById(id).map(c -> {
            c.setEmployeeCount((int) userRepository.countByCompanyId(c.getId()));
            return c;
        });
    }

    @Transactional
    public Company createCompany(Company company) {
        if (company.getCompanyId() == null || company.getCompanyId().isBlank()) {
            company.setCompanyId(generateUniqueCompanyId(company.getCompanyName()));
        }
        if (company.getCode() == null || company.getCode().isBlank()) {
            company.setCode(company.getCompanyId());
        }
        return companyRepository.save(company);
    }

    @Transactional
    public Company updateCompany(Long id, Company updated) {
        Company existing = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        existing.setCompanyName(updated.getCompanyName());
        existing.setLegalName(updated.getLegalName());
        existing.setEmail(updated.getEmail());
        existing.setPhone(updated.getPhone());
        existing.setWebsite(updated.getWebsite());
        existing.setAddress(updated.getAddress());
        existing.setCountry(updated.getCountry());
        existing.setState(updated.getState());
        existing.setCity(updated.getCity());
        existing.setTimezone(updated.getTimezone());
        existing.setCurrency(updated.getCurrency());
        existing.setDateFormat(updated.getDateFormat());
        existing.setLanguage(updated.getLanguage());
        existing.setIndustry(updated.getIndustry());
        existing.setOrganizationType(updated.getOrganizationType());
        existing.setEmployeeCount(updated.getEmployeeCount());
        existing.setStatus(updated.getStatus());
        existing.setLogoPath(updated.getLogoPath());
        existing.setPrimaryColor(updated.getPrimaryColor());
        existing.setSecondaryColor(updated.getSecondaryColor());

        return companyRepository.save(existing);
    }

    @Transactional
    public void deleteCompany(Long id) {
        // We typically use soft delete by setting status to ARCHIVED, but if physical delete is requested:
        companyRepository.deleteById(id);
    }

    @Transactional
    public void suspendCompany(Long id) {
        Company existing = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        existing.setStatus("SUSPENDED");
        companyRepository.save(existing);
    }

    private String generateUniqueCompanyId(String companyName) {
        String prefix = companyName != null ? companyName.replaceAll("[^A-Za-z0-9]", "").toUpperCase() : "COMP";
        if (prefix.length() > 6) {
            prefix = prefix.substring(0, 6);
        } else if (prefix.length() < 3) {
            prefix = prefix + "XXX".substring(0, 3 - prefix.length());
        }

        String uniqueSuffix;
        String generatedId;
        int attempts = 0;
        do {
            uniqueSuffix = generateRandomString(6);
            generatedId = prefix + "-" + uniqueSuffix;
            attempts++;
            if (attempts > 10) {
                throw new RuntimeException("Could not generate a unique Company ID");
            }
        } while (companyRepository.findByCompanyId(generatedId).isPresent());

        return generatedId;
    }

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}
