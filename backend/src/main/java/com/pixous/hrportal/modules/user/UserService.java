package com.pixous.hrportal.modules.user;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.common.StorageService;
import com.pixous.hrportal.modules.user.dto.BankRequest;
import com.pixous.hrportal.modules.user.dto.BankResponse;
import com.pixous.hrportal.modules.user.dto.OffboardingRequest;
import com.pixous.hrportal.modules.user.dto.ProfileResponse;
import com.pixous.hrportal.modules.user.dto.UpdateProfileRequest;
import com.pixous.hrportal.modules.user.dto.UpdateEmployeeRequest;
import com.pixous.hrportal.modules.user.dto.UserSummary;
import com.pixous.hrportal.modules.community.CommunityGroup;
import com.pixous.hrportal.modules.community.CommunityGroupRepository;
import com.pixous.hrportal.modules.community.CommunityMessageRepository;
import com.pixous.hrportal.modules.community.CommunityService;
import org.springframework.context.annotation.Lazy;

/** Profile read/update, photo upload, employee directory, and bank-detail CRUD. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final BankDetailRepository bankDetailRepository;
    private final StorageService storageService;
    private final OffboardingRecordRepository offboardingRecordRepository;
    private final CommunityGroupRepository groupRepository;
    private final CommunityMessageRepository messageRepository;
    private final CommunityService communityService;
    private final jakarta.persistence.EntityManager entityManager;
    private final RoleRepository roleRepository;
    private final com.pixous.hrportal.modules.org.DesignationRepository designationRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.pixous.hrportal.modules.auth.PasswordVault passwordVault;

    public UserService(UserRepository userRepository,
                       BankDetailRepository bankDetailRepository,
                       StorageService storageService,
                       OffboardingRecordRepository offboardingRecordRepository,
                       CommunityGroupRepository groupRepository,
                       CommunityMessageRepository messageRepository,
                       @Lazy CommunityService communityService,
                       jakarta.persistence.EntityManager entityManager,
                       RoleRepository roleRepository,
                       com.pixous.hrportal.modules.org.DesignationRepository designationRepository,
                       org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                       com.pixous.hrportal.modules.auth.PasswordVault passwordVault,
                       com.pixous.hrportal.modules.org.CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordVault = passwordVault;
        this.userRepository = userRepository;
        this.bankDetailRepository = bankDetailRepository;
        this.storageService = storageService;
        this.offboardingRecordRepository = offboardingRecordRepository;
        this.groupRepository = groupRepository;
        this.messageRepository = messageRepository;
        this.communityService = communityService;
        this.entityManager = entityManager;
        this.roleRepository = roleRepository;
        this.designationRepository = designationRepository;
    }

    @jakarta.annotation.PostConstruct
    public void copyChatbotImage() {
        try {
            java.io.File source = new java.io.File("C:/Users/balas/.gemini/antigravity/brain/17080dc6-1d50-41e0-9743-bebef22143f3/media__1784004964603.png");
            java.io.File targetDir = new java.io.File("c:/Users/balas/OneDrive/java bharu/correct/hmass/hr-portal-fixed-v5/hr-portal/web/public");
            if (source.exists() && targetDir.exists()) {
                java.io.File targetFile = new java.io.File(targetDir, "chatbot.png");
                java.nio.file.Files.copy(source.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Chatbot image copied successfully to " + targetFile.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return toProfile(findUser(userId));
    }

    /** Remove an employee from their team by clearing their designation link. */
    @Transactional
    public void clearDesignation(Long userId) {
        User u = findUser(userId);
        u.setDesignationId(null);
        userRepository.save(u);
    }

    /** The signed-in employee's team (their designation) and its active members. */
    @Transactional(readOnly = true)
    public com.pixous.hrportal.modules.user.dto.MyTeamResponse getMyTeam(Long userId) {
        User me = findUser(userId);
        Long designationId = me.getDesignationId();

        // Resolve the team's display name: prefer the user's own designation
        // title, else the linked designation record's name.
        String title = me.getDesignationTitle();
        if ((title == null || title.isBlank()) && designationId != null) {
            title = designationRepository.findById(designationId)
                    .map(com.pixous.hrportal.modules.org.Designation::getName)
                    .filter(s -> s != null && !s.isBlank())
                    .orElse(null);
        }

        String teamName;
        java.util.List<User> members;
        if (title != null && !title.isBlank()) {
            // Match teammates by title (what employees actually carry) OR the FK,
            // so title-only members appear too — consistent with the Teams page.
            teamName = title;
            members = userRepository.findTeammatesByTitleOrDesignation(title, designationId);
            if (members.isEmpty()) {
                members = java.util.List.of(me);
            }
        } else {
            teamName = "My Team";
            members = java.util.List.of(me);
        }
        java.util.List<UserSummary> memberSummaries = members.stream().map(this::toSummary).toList();
        return new com.pixous.hrportal.modules.user.dto.MyTeamResponse(teamName, memberSummaries);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = findUser(userId);
        if (req.name() != null) user.setName(req.name());
        if (req.dob() != null && !req.dob().isBlank()) user.setDob(LocalDate.parse(req.dob()));
        if (req.gender() != null && !req.gender().isBlank()) {
    user.setGender(Character.toUpperCase(req.gender().trim().charAt(0)));
}
        if (req.email() != null) user.setEmail(req.email());
        if (req.careOf() != null) user.setCareOf(req.careOf());
        if (req.house() != null) user.setHouse(req.house());
        if (req.street() != null) user.setStreet(req.street());
        if (req.locality() != null) user.setLocality(req.locality());
        if (req.vtc() != null) user.setVtc(req.vtc());
        if (req.district() != null) user.setDistrict(req.district());
        if (req.state() != null) user.setState(req.state());
        if (req.country() != null) user.setCountry(req.country());
        if (req.pincode() != null) user.setPincode(req.pincode());
        if (req.postOffice() != null) user.setPostOffice(req.postOffice());
        userRepository.save(user);
        return toProfile(user);
    }

    @Transactional
    public ProfileResponse updateEmployee(Long id, UpdateEmployeeRequest req) {
        User user = findUser(id);
        if (req.name() != null) user.setName(req.name());
        if (req.dob() != null && !req.dob().isBlank()) user.setDob(LocalDate.parse(req.dob()));
        if (req.gender() != null && !req.gender().isBlank()) {
            user.setGender(Character.toUpperCase(req.gender().trim().charAt(0)));
        }
        if (req.email() != null) user.setEmail(blankToNull(req.email()));
        if (req.phone() != null) user.setPhone(blankToNull(req.phone()));
        if (req.aadhar() != null) user.setAadhar(blankToNull(req.aadhar()));
        if (req.pan() != null) user.setPan(blankToNull(req.pan()));
        if (req.pfNumber() != null) user.setPfNumber(blankToNull(req.pfNumber()));
        if (req.alternatePhone() != null) user.setAlternatePhone(blankToNull(req.alternatePhone()));
        if (req.emergencyContact() != null) user.setEmergencyContact(blankToNull(req.emergencyContact()));
        if (req.emergencyContactRelation() != null) user.setEmergencyContactRelation(blankToNull(req.emergencyContactRelation()));
        if (req.bloodGroup() != null) user.setBloodGroup(blankToNull(req.bloodGroup()));
        if (req.documents() != null) user.setDocuments(blankToNull(req.documents()));
        if (req.personalEmail() != null) user.setPersonalEmail(blankToNull(req.personalEmail()));
        if (req.designationTitle() != null) user.setDesignationTitle(blankToNull(req.designationTitle()));
        if (req.departmentTitle() != null) user.setDepartmentTitle(blankToNull(req.departmentTitle()));
        if (req.positionTitle() != null) user.setPositionTitle(blankToNull(req.positionTitle()));
        if (req.techStack() != null) user.setTechStack(blankToNull(req.techStack()));
        if (req.careOf() != null) user.setCareOf(req.careOf());
        if (req.house() != null) user.setHouse(req.house());
        if (req.street() != null) user.setStreet(req.street());
        if (req.locality() != null) user.setLocality(req.locality());
        if (req.vtc() != null) user.setVtc(req.vtc());
        if (req.district() != null) user.setDistrict(req.district());
        if (req.state() != null) user.setState(req.state());
        if (req.country() != null && !req.country().isBlank()) user.setCountry(req.country());
        if (req.pincode() != null) user.setPincode(req.pincode());
        if (req.postOffice() != null) user.setPostOffice(req.postOffice());
        if (req.industry() != null) user.setIndustry(req.industry().trim().toUpperCase());
        if (req.departmentId() != null) user.setDepartmentId(req.departmentId());
        if (req.designationId() != null) user.setDesignationId(req.designationId());
        if (req.officeLocationId() != null) user.setOfficeLocationId(req.officeLocationId());
        if (req.reportingManagerId() != null) user.setReportingManagerId(req.reportingManagerId());
        if (req.employmentType() != null) user.setEmploymentType(req.employmentType());
        if (req.dateOfJoining() != null && !req.dateOfJoining().isBlank()) {
            user.setDateOfJoining(LocalDate.parse(req.dateOfJoining()));
        }
        // Blank clears it, so a confirmed employee can have the date taken off.
        if (req.probationEndDate() != null) {
            user.setProbationEndDate(req.probationEndDate().isBlank()
                    ? null : parseDate(req.probationEndDate()));
        }
        if (req.profileStatus() != null) {
            String status = req.profileStatus().trim().toUpperCase();
            user.setProfileStatus(status);
            user.setEnabled(!"OFFBOARDED".equals(status));
        }
        if (req.employeeCode() != null) user.setEmployeeCode(req.employeeCode());
        if (req.roles() != null) {
            Set<Role> roles = roleRepository.findByCodeIn(new java.util.HashSet<>(req.roles()));
            /*
             * A code that matches nothing must not quietly empty the set.
             *
             * findByCodeIn returns what it found, so a request naming roles that
             * do not exist — a typo, or a code from a build that has moved on —
             * produced an empty set, and setRoles stripped every role the person
             * had. The account survives, can still sign in, and can then reach
             * nothing: no sidebar, no permissions. Nothing on screen says why,
             * because as far as the request was concerned it succeeded.
             *
             * Refusing is the only safe answer. Clearing every role stays
             * possible by sending an empty list, which says so deliberately.
             */
            if (!req.roles().isEmpty() && roles.isEmpty()) {
                throw ApiException.business("Unknown role: " + String.join(", ", req.roles()));
            }
            user.setRoles(roles);
        }
        userRepository.save(user);
        return toProfile(user);
    }

    /** Admin: change an employee's login username and/or reset their password. */
    @Transactional
    public void setCredentials(Long userId, String username, String password) {
        User user = findUser(userId);
        boolean changed = false;
        if (username != null && !username.isBlank()) {
            String uname = username.trim();
            /*
             * Checked across tenants, because that is how the index is built.
             * A tenant-scoped check passes for a username already held in
             * another company, and the insert then fails on the constraint --
             * so the person renaming an account is shown a database error
             * instead of "that username is taken", and login, which resolves a
             * username globally, could not have told the two accounts apart
             * anyway.
             */
            userRepository.findByUsernameAcrossTenants(uname)
                    .filter(other -> !other.getId().equals(userId))
                    .ifPresent(other -> {
                        throw ApiException.business("Username \"" + uname + "\" is already taken");
                    });
            user.setUsername(uname);
            changed = true;
        }
        if (password != null && !password.isBlank()) {
            if (password.trim().length() < 4) {
                throw ApiException.business("Password must be at least 4 characters");
            }
            user.setPasswordHash(passwordEncoder.encode(password.trim()));
            user.setPasswordVault(passwordVault.seal(password.trim()));
            changed = true;
        }
        if (!changed) {
            throw ApiException.business("Provide a username or password to update");
        }
        userRepository.save(user);
    }

    /**
     * The employee's current password, for HR and the admin to read off the
     * record.
     *
     * <p>Accounts whose password was set through the portal since V71 have it
     * kept for exactly this. For the older ones there is no stored copy, but most
     * of them were issued a password the portal itself chose — the joining form
     * and the Excel import both use {@code Firstname@123} — and a guess can be
     * confirmed against the BCrypt hash. So each of those patterns is checked
     * once; a match is the real password, is recorded for next time, and is
     * returned. Nothing is guessed blindly: only the handful of defaults this
     * portal issues are tried, and one either matches the hash or it does not.
     *
     * <p>Null means the password is genuinely unknown — it was changed to
     * something of the employee's own before the portal began keeping a copy —
     * and Reset Login is the only way forward.
     */
    @Transactional
    public String currentPassword(Long userId) {
        User user = findUser(userId);

        String stored = passwordVault.open(user.getPasswordVault());
        if (stored != null) return stored;

        for (String candidate : issuedDefaults(user)) {
            if (passwordEncoder.matches(candidate, user.getPasswordHash())) {
                // Confirmed by the hash, so record it and stop re-deriving it.
                user.setPasswordVault(passwordVault.seal(candidate));
                userRepository.save(user);
                return candidate;
            }
        }
        return null;
    }

    /**
     * The passwords this portal hands out by default, for the account in
     * question. Kept deliberately short: these are the patterns used when an
     * employee is created, not an attempt to work out an arbitrary password.
     */
    private static java.util.List<String> issuedDefaults(User u) {
        String first = (u.getName() == null ? "" : u.getName()).trim().split("\\s+")[0]
                .replaceAll("[^A-Za-z]", "");
        if (first.isEmpty()) return java.util.List.of();
        String cap = first.substring(0, 1).toUpperCase() + first.substring(1).toLowerCase();
        return java.util.List.of(
                cap + "@123",       // the joining form and the import both use this
                cap + "@2025",      // the import's fallback when @123 is under 8 characters
                cap.toLowerCase() + "@123"
        );
    }

    /** Stores one employee document and hands back its path. */
    public String storeDocument(MultipartFile file) {
        return storageService.store(file, "employee-docs");
    }

    @Transactional
    public String updatePhoto(Long userId, MultipartFile file) {
        User user = findUser(userId);
        String path = storageService.store(file, "photos");
        user.setPhotoPath(path);
        userRepository.save(user);
        return path;
    }

    /** Drops the profile photo; the initials avatar takes its place again. */
    @Transactional
    public void removePhoto(Long userId) {
        User user = findUser(userId);
        user.setPhotoPath(null);
        userRepository.save(user);
    }

    /**
     * Stores the banner image for this person's own dashboard.
     *
     * Kept in its own folder rather than beside profile photos, so the two are
     * distinguishable on disk and a cover can be cleared without any chance of
     * touching the picture of the person.
     */
    @Transactional
    public String updateCoverPhoto(Long userId, MultipartFile file) {
        User user = findUser(userId);
        String path = storageService.store(file, "covers");
        user.setCoverPhotoPath(path);
        userRepository.save(user);
        return path;
    }

    /** Clears the banner image; the plain colour returns. */
    @Transactional
    public void removeCoverPhoto(Long userId) {
        User user = findUser(userId);
        user.setCoverPhotoPath(null);
        userRepository.save(user);
    }

    /**
     * Records that this employee's face has been registered, keeping one photo of
     * it and who did it.
     *
     * <p>The photo is only ever looked at — so that whoever registered somebody
     * else's face can confirm afterwards that it was the right person. Matching
     * uses encodings held by the analytics service and is untouched by this.
     */
    @Transactional
    public java.util.Map<String, Object> saveFacePhoto(Long userId, MultipartFile file, Long actorId) {
        User user = findUser(userId);
        if (file == null || file.isEmpty()) {
            throw ApiException.business("No photo was received.");
        }
        String path = storageService.store(file, "face-enrolment");
        user.setFacePhotoPath(path);
        user.setFaceRegisteredAt(LocalDateTime.now());
        user.setFaceRegisteredBy(actorId);
        userRepository.save(user);

        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("facePhotoPath", path);
        out.put("faceRegisteredAt", user.getFaceRegisteredAt());
        out.put("faceRegisteredBy", actorId == null ? null
                : userRepository.findById(actorId).map(User::getName).orElse(null));
        return out;
    }

    /**
     * Forgets the enrolment photo. The encodings are the analytics service's to
     * remove, and the caller does that separately — a face is biometric data and
     * both halves have to be erasable.
     */
    @Transactional
    public void clearFacePhoto(Long userId) {
        User user = findUser(userId);
        user.setFacePhotoPath(null);
        user.setFaceRegisteredAt(null);
        user.setFaceRegisteredBy(null);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> directory(String q, String industry, Long departmentId,
                                               String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<UserSummary> result = userRepository
                .search(blankToNull(q), blankToNull(industry), departmentId, blankToNull(status), pageable)
                .map(this::toSummary);
        return PageResponse.from(result);
    }

    /**
     * The directory narrowed by team, role, department and a joining-date window.
     * Every filter is optional; leaving them all out gives the same answer as
     * {@link #directory}, so the plain listing is unaffected.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserSummary> directoryFiltered(String q, String industry, Long departmentId,
                                                       Long designationId, String designationTitle,
                                                       String roleCode, String joinedFrom, String joinedTo,
                                                       String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<UserSummary> result = userRepository
                .searchFiltered(blankToNull(q), blankToNull(industry), departmentId,
                        designationId, blankToNull(designationTitle), blankToNull(roleCode),
                        parseDate(joinedFrom), parseDate(joinedTo), blankToNull(status), pageable)
                .map(this::toSummary);
        return PageResponse.from(result);
    }

    /** A yyyy-MM-dd string, or null when it is missing or unreadable. */
    private static java.time.LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public ProfileResponse getById(Long id) {
        return toProfile(findUser(id));
    }

    @Transactional
    public void offboardUser(Long userId, OffboardingRequest req) {
        User user = findUser(userId);
        if ("OFFBOARDED".equals(user.getProfileStatus())) {
            throw new  ApiException(ErrorCode.BAD_CREDENTIALS,"User is already offboarded");
        }
        user.setProfileStatus("OFFBOARDED");
        user.setEnabled(false);
        userRepository.save(user);

        OffboardingRecord record = new OffboardingRecord();
        record.setUserId(userId);
        record.setRelievingDate(req.relievingDate());
        record.setReason(req.reason());
        record.setNotes(req.notes());
        record.setFnfStatus("PENDING");
        offboardingRecordRepository.save(record);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = findUser(userId);

        // Clean up references to this user that are NOT backed by an ON DELETE
        // CASCADE foreign key. CRITICAL: each statement is existence-checked
        // against information_schema BEFORE it runs. A native statement that
        // fails (e.g. a missing table/column) marks the whole transaction
        // rollback-only — even when the exception is caught — which then aborts
        // the delete with UnexpectedRollbackException. Checking first means we
        // never execute a statement that could fail, so the transaction stays
        // clean and the final delete always commits.

        // Legacy tables that may or may not exist in a given environment.
        nullifyColumn("projects", "manager_id", userId);
        nullifyColumn("teams", "team_lead_id", userId);
        // community_* have no ON DELETE rule (V16). Deleting the communities the
        // user created cascades to their members and messages; deleting the
        // user's own messages clears sender references in communities they only
        // belong to. Remaining membership rows cascade on the user delete.
        deleteRows("community_messages", "sender_id", userId);
        deleteRows("communities", "created_by", userId);
        // Helpdesk columns reference users without a FK (V7).
        nullifyColumn("tickets", "assigned_to", userId);
        nullifyColumn("ticket_comments", "author_id", userId);

        // Reassign this user's subordinates (the users table always exists).
        userRepository.findByReportingManagerId(userId).forEach(sub -> {
            sub.setReportingManagerId(null);
            userRepository.save(sub);
        });

        // Flush queued changes, then delete. DB-level cascades remove the rest
        // (attendance, payroll, documents, roles, etc.).
        entityManager.flush();
        userRepository.delete(user);
    }

    /** True if column `col` exists on table `table` in the current schema. */
    private boolean columnExists(String table, String col) {
        Number n = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = :t AND column_name = :c")
                .setParameter("t", table).setParameter("c", col)
                .getSingleResult();
        return n != null && n.intValue() > 0;
    }

    /** UPDATE table SET col = NULL WHERE col = userId — only if the column exists.
     *  Table/column names are hard-coded constants (no user input), so string
     *  concatenation here is safe from injection. */
    private void nullifyColumn(String table, String col, Long userId) {
        if (columnExists(table, col)) {
            entityManager.createNativeQuery(
                    "UPDATE " + table + " SET " + col + " = NULL WHERE " + col + " = :id")
                    .setParameter("id", userId).executeUpdate();
        }
    }

    /** DELETE FROM table WHERE col = userId — only if the column exists. */
    private void deleteRows(String table, String col, Long userId) {
        if (columnExists(table, col)) {
            entityManager.createNativeQuery(
                    "DELETE FROM " + table + " WHERE " + col + " = :id")
                    .setParameter("id", userId).executeUpdate();
        }
    }

    // ---- bank details (mirrors legacy bank/index.php add|update|delete|view) ----

    @Transactional(readOnly = true)
    public List<BankResponse> listBanks(Long userId) {
        return bankDetailRepository.findByUserId(userId).stream().map(this::toBank).toList();
    }

    @Transactional
    public BankResponse addBank(Long userId, BankRequest req) {
        BankDetail bank = new BankDetail();
        bank.setUserId(userId);
        applyBank(bank, req);
        if (Boolean.TRUE.equals(req.primary())) {
            demoteOtherPrimaries(userId);
        }
        bankDetailRepository.save(bank);
        return toBank(bank);
    }

    @Transactional
    public BankResponse updateBank(Long userId, Long bankId, BankRequest req) {
        BankDetail bank = bankDetailRepository.findByIdAndUserId(bankId, userId)
                .orElseThrow(() -> ApiException.notFound("Bank detail"));
        applyBank(bank, req);
        if (Boolean.TRUE.equals(req.primary())) {
            demoteOtherPrimaries(userId);
            bank.setPrimary(true);
        }
        bankDetailRepository.save(bank);
        return toBank(bank);
    }

    @Transactional
    public void deleteBank(Long userId, Long bankId) {
        BankDetail bank = bankDetailRepository.findByIdAndUserId(bankId, userId)
                .orElseThrow(() -> ApiException.notFound("Bank detail"));
        bankDetailRepository.delete(bank);
    }

    // ---- helpers ----

    private void demoteOtherPrimaries(Long userId) {
        bankDetailRepository.findByUserId(userId).forEach(b -> {
            if (b.isPrimary()) {
                b.setPrimary(false);
                bankDetailRepository.save(b);
            }
        });
    }

    private void applyBank(BankDetail bank, BankRequest req) {
        bank.setBankName(req.bankName());
        bank.setBranchName(req.branchName());
        bank.setAccountNumber(req.accountNumber());
        bank.setIfscCode(req.ifscCode().toUpperCase());
        bank.setAccountHolderName(req.accountHolderName());
        if (req.primary() != null) {
            bank.setPrimary(req.primary());
        }
    }

    /**
     * Load a user by id, and refuse it if they belong to a different company.
     *
     * Hibernate's tenant filter applies to queries, not to findById — so the
     * employee *list* was correctly scoped while fetching one person by id was
     * not. An HR account in company 1 could read company 4's admin simply by
     * asking for /users/6, and because every write path here goes through this
     * same method, it could also edit that account, reset its password or delete
     * it. Verified against a copy of production before this check existed.
     *
     * "Not found" rather than "forbidden", deliberately: a 403 would confirm that
     * the id exists and belongs to somebody else, which is itself worth knowing to
     * an attacker walking the range.
     *
     * When the caller has no company of their own — a technical admin, who works
     * across tenants by design, or a legacy account not yet assigned — the check
     * stands aside. That is the same condition TenantFilterAspect uses to decide
     * whether to switch the filter on at all, so the two agree.
     */
    private User findUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        Long callerCompany = com.pixous.hrportal.security.SecurityUtils.currentCompanyId();
        if (callerCompany != null
                && user.getCompanyId() != null
                && !callerCompany.equals(user.getCompanyId())) {
            throw ApiException.notFound("User");
        }
        return user;
    }

    private ProfileResponse toProfile(User u) {
        return new ProfileResponse(
                u.getId(), u.getEmployeeCode(), u.getUsername(), u.getName(), u.getDob(),
                 u.getGender() != null ? String.valueOf(u.getGender()) : null,
                u.getAadhar(), u.getPhone(), u.getEmail(), u.getPhotoPath(), u.getCoverPhotoPath(),
                new ProfileResponse.AddressDto(u.getCareOf(), u.getHouse(), u.getStreet(),
                        u.getLocality(), u.getVtc(), u.getDistrict(), u.getState(),
                        u.getCountry(), u.getPincode(), u.getPostOffice()),
                u.getDepartmentId(), u.getDesignationId(), u.getOfficeLocationId(),
                u.getReportingManagerId(), u.getIndustry(), u.getEmploymentType(),
                u.getDateOfJoining(), u.getProbationEndDate(), u.getProfileStatus(),
                u.getPan(), u.getPfNumber(), u.getAlternatePhone(), u.getEmergencyContact(),
                u.getEmergencyContactRelation(), u.getBloodGroup(), u.getPersonalEmail(),
                u.getDesignationTitle(), u.getDepartmentTitle(), u.getPositionTitle(),
                u.getRoles().stream().map(Role::getCode).toList(),
                // Flattened across every role and de-duplicated, exactly as
                // sign-in builds it, so a restored session and a fresh one
                // grant the same screens.
                u.getRoles().stream()
                        .flatMap(r -> r.getPermissions().stream())
                        .map(pm -> pm.getCode())
                        .distinct()
                        .toList(),
                u.getDocuments(),
                u.getFacePhotoPath(), u.getFaceRegisteredAt(),
                u.getFaceRegisteredBy() == null ? null
                        : userRepository.findById(u.getFaceRegisteredBy())
                                .map(User::getName).orElse(null));
    }

    private final com.pixous.hrportal.modules.org.CompanyRepository companyRepository;

    private UserSummary toSummary(User u) {
        String plainPassword = passwordVault != null ? passwordVault.open(u.getPasswordVault()) : null;
        return new UserSummary(u.getId(), u.getEmployeeCode(), u.getName(), u.getUsername(), u.getEmail(),
                u.getPhone(), u.getIndustry(), u.getDepartmentId(), u.getProfileStatus(),
                u.getPhotoPath(), u.getDob(), u.getRoles().stream().map(Role::getCode).toList(),
                u.getDesignationId(), u.getDesignationTitle(), u.getTechStack(), plainPassword,
                u.getCompanyId(), companyNameOf(u.getCompanyId()));
    }

    /**
     * The company a row belongs to, by name.
     *
     * <p>This field was the literal string "Company Name" for every user. The
     * technical-admin directory filters its rows by matching this against the
     * company being viewed, so nothing ever matched and the table showed no
     * accounts and all three role counts as zero — while the dashboard beside it
     * correctly reported sixty-one employees.
     *
     * <p>Looked up through a small per-request cache rather than joined, because
     * this runs once per row and a directory page asks for three hundred of them.
     * Three hundred lookups against a database that allows twenty connections in
     * total is not a trade worth making for a name that repeats.
     */
    private String companyNameOf(Long companyId) {
        if (companyId == null) return null;
        return companyNameCache.computeIfAbsent(companyId, id ->
                companyRepository.findById(id)
                        .map(com.pixous.hrportal.modules.org.Company::getCompanyName)
                        .orElse(null));
    }

    /**
     * Cleared on every directory call, so a company renamed while the
     * application is running shows its new name rather than a remembered one.
     */
    private final java.util.Map<Long, String> companyNameCache = new java.util.concurrent.ConcurrentHashMap<>();

    private BankResponse toBank(BankDetail b) {
        return new BankResponse(b.getId(), b.getBankName(), b.getBranchName(),
                b.getAccountNumber(), b.getIfscCode(), b.getAccountHolderName(), b.isPrimary());
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
