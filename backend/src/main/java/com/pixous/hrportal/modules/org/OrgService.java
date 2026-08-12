package com.pixous.hrportal.modules.org;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.config.CacheConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Master-data reads. {@link #dropdown(String)} reproduces the legacy
 * {@code dropdown.php?type=...} contract (single type) and {@link #dropdowns(List)}
 * supports the array form the Postman collection also used.
 */
@Service
public class OrgService {

    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final PositionRepository positionRepository;
    private final BloodGroupRepository bloodGroupRepository;
    private final EmploymentStatusRepository employmentStatusRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final ShiftRepository shiftRepository;
    private final SiteRepository siteRepository;
    private final HolidayRepository holidayRepository;
    private final com.pixous.hrportal.modules.user.UserRepository userRepository;
    private final com.pixous.hrportal.modules.notification.NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;

    /**
     * This service, seen through its own proxy.
     *
     * <p>Caching is applied by that proxy, so a method calling a sibling directly
     * skips it — and the two entry points below, {@link #dropdown(String)} and
     * {@link #dropdowns(List, String)}, both do exactly that. Without this field
     * the multi-dropdown call that every page makes would have missed the cache on
     * every request while appearing to be cached. Lazy because a bean cannot be
     * handed to its own constructor.
     */
    @Autowired
    @Lazy
    private OrgService self;

    public OrgService(DepartmentRepository departmentRepository,
                      DesignationRepository designationRepository,
                      PositionRepository positionRepository,
                      BloodGroupRepository bloodGroupRepository,
                      EmploymentStatusRepository employmentStatusRepository,
                      OfficeLocationRepository officeLocationRepository,
                      ShiftRepository shiftRepository,
                      SiteRepository siteRepository,
                      HolidayRepository holidayRepository,
                      com.pixous.hrportal.modules.user.UserRepository userRepository,
                      com.pixous.hrportal.modules.notification.NotificationService notificationService,
                      com.pixous.hrportal.common.SmsService smsService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.smsService = smsService;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.positionRepository = positionRepository;
        this.bloodGroupRepository = bloodGroupRepository;
        this.employmentStatusRepository = employmentStatusRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.shiftRepository = shiftRepository;
        this.siteRepository = siteRepository;
        this.holidayRepository = holidayRepository;
    }

    @Transactional(readOnly = true)
    public List<DropdownItem> dropdown(String type) {
        return self.dropdown(type, null);
    }

    /**
     * Cached. These lists change when somebody adds a team or an office — a few
     * times a year — and are read on every page that has a form on it. The key
     * includes the industry filter, because "designation" answers differently for
     * IT and CIVIL and one answer must not be served for the other.
     */
    // The company is part of the key.
    //
    // Master data is per-company, but these entries were keyed only by what was
    // being asked for -- so whichever tenant warmed the cache first served every
    // other tenant its departments, designations, sites and holidays. The database
    // filters were correct and never got a chance to run, because the query did
    // not run either. Verified: a company-1 account was handed company-4 holidays
    // straight from the cache.
    @Cacheable(cacheNames = CacheConfig.MASTERS,
               key = "T(com.pixous.hrportal.security.SecurityUtils).currentCompanyId() + ':dropdown:' + #root.target.normalize(#type) + ':' + (#industry == null ? '-' : #industry)")
    @Transactional(readOnly = true)
    public List<DropdownItem> dropdown(String type, String industry) {
        return switch (normalize(type)) {
            case "blood_group" -> bloodGroupRepository.findByActiveTrueOrderByNameAsc()
                    .stream().map(b -> new DropdownItem(b.getId(), b.getName())).toList();
            case "department" -> departmentRepository.findByActiveTrueOrderByNameAsc()
                    .stream().map(d -> new DropdownItem(d.getId(), d.getName())).toList();
            case "designation" -> designationRepository.findActiveByIndustry(normalizeIndustry(industry))
                    .stream().map(d -> new DropdownItem(d.getId(), d.getName())).toList();
            case "employment_status" -> employmentStatusRepository.findByActiveTrueOrderByNameAsc()
                    .stream().map(e -> new DropdownItem(e.getId(), e.getName())).toList();
            case "position" -> positionRepository.findByActiveTrueOrderByNameAsc()
                    .stream().map(p -> new DropdownItem(p.getId(), p.getName())).toList();
            case "office_location" -> officeLocationRepository.findByActiveTrueOrderByNameAsc()
                    .stream().map(o -> new DropdownItem(o.getId(), o.getName())).toList();
            case "shift" -> shiftRepository.findByActiveTrueOrderByNameAsc()
                    .stream().map(s -> new DropdownItem(s.getId(), s.getName())).toList();
            case "site" -> siteRepository.findByActiveTrueOrderByNameAsc()
                    .stream().map(s -> new DropdownItem(s.getId(), s.getName())).toList();
            default -> throw ApiException.business("Unknown dropdown type: " + type);
        };
    }

    @Transactional(readOnly = true)
    public Map<String, List<DropdownItem>> dropdowns(List<String> types) {
        return dropdowns(types, null);
    }

    /**
     * Not cached itself — each dropdown inside it is, through {@link #self}, so a
     * page asking for eight lists at once takes eight cache hits rather than eight
     * queries. Caching the combination as well would store the same lists again
     * under every combination of types any page happens to ask for.
     */
    @Transactional(readOnly = true)
    public Map<String, List<DropdownItem>> dropdowns(List<String> types, String industry) {
        return types.stream().collect(Collectors.toMap(
                this::normalize, t -> self.dropdown(t, industry), (a, b) -> a, java.util.LinkedHashMap::new));
    }

    @Cacheable(cacheNames = CacheConfig.MASTERS,
               key = "T(com.pixous.hrportal.security.SecurityUtils).currentCompanyId() + ':sites'")
    @Transactional(readOnly = true)
    public List<Site> sites() {
        return siteRepository.findByActiveTrueOrderByNameAsc();
    }

    /**
     * Cached, and read on every single punch: naming the place a punch was made
     * compares its coordinates against this list. Offices move about once, so this
     * is the clearest win in the application.
     */
    @Cacheable(cacheNames = CacheConfig.MASTERS,
               key = "T(com.pixous.hrportal.security.SecurityUtils).currentCompanyId() + ':officeLocations'")
    @Transactional(readOnly = true)
    public List<OfficeLocation> officeLocations() {
        return officeLocationRepository.findByActiveTrueOrderByNameAsc();
    }

    /**
     * Adds an office at a set of coordinates, or moves one that already exists.
     *
     * <p>Attendance stores true coordinates and needs something to compare them
     * against; until an office is on record, a punch made inside it can only be
     * reported as somewhere unrecognised. Passing an id moves that office rather
     * than adding a second copy of the same building.
     */
    // Moving an office changes how every punch in the company is named, so the
    // whole master cache goes rather than one key: the office list, the office
    // dropdown and the sites list are all built from this.
    @CacheEvict(cacheNames = CacheConfig.MASTERS, allEntries = true)
    @Transactional
    public OfficeLocation saveOfficeLocation(java.util.Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) {
            throw ApiException.business("A name for this office is required");
        }
        java.math.BigDecimal lat = decimal(body.get("latitude"));
        java.math.BigDecimal lng = decimal(body.get("longitude"));
        if (lat == null || lng == null) {
            throw ApiException.business(
                    "Coordinates are required. Stand in the office and use your current location.");
        }
        // Beyond these the value is not a coordinate at all, and a punch would then
        // be compared against nonsense forever after.
        if (lat.doubleValue() < -90 || lat.doubleValue() > 90
                || lng.doubleValue() < -180 || lng.doubleValue() > 180) {
            throw ApiException.business("Those are not valid coordinates.");
        }

        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        OfficeLocation office = id == null
                ? new OfficeLocation()
                : officeLocationRepository.findById(id)
                        .orElseThrow(() -> ApiException.notFound("Office location"));

        if (id == null) {
            // Adding: refuse a duplicate name, which is how one building ends up on
            // the list three times.
            for (OfficeLocation existing : officeLocationRepository.findAll()) {
                if (existing.getName() != null && existing.getName().equalsIgnoreCase(name.trim())) {
                    throw ApiException.conflict("An office named '" + name.trim() + "' already exists");
                }
            }
        }

        office.setName(name.trim());
        office.setAddress(str(body.get("address")));
        office.setLatitude(lat);
        office.setLongitude(lng);
        Integer radius = body.get("geofenceRadiusMetres") == null ? null
                : Integer.valueOf(String.valueOf(body.get("geofenceRadiusMetres")));
        // A radius under fifty metres turns away people standing in the building,
        // because a phone's own fix is rarely better than that indoors.
        office.setGeofenceRadiusMetres(radius == null ? 200 : Math.max(50, Math.min(5000, radius)));
        office.setActive(true);
        return officeLocationRepository.save(office);
    }

    @CacheEvict(cacheNames = CacheConfig.MASTERS, allEntries = true)
    @Transactional
    public void deleteOfficeLocation(Long id) {
        OfficeLocation office = officeLocationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Office location"));
        // Deactivated rather than deleted: attendance rows point at it, and a punch
        // that can no longer say where it was made is worse than a stale name.
        office.setActive(false);
        officeLocationRepository.save(office);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static java.math.BigDecimal decimal(Object o) {
        if (o == null) return null;
        try {
            return new java.math.BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Cacheable(cacheNames = CacheConfig.HOLIDAYS,
               key = "T(com.pixous.hrportal.security.SecurityUtils).currentCompanyId() + ':' + (#year == null ? 'all' : #year)")
    @Transactional(readOnly = true)
    public List<Holiday> holidays(Integer year) {
        List<Holiday> all = (year == null)
                ? holidayRepository.findAllByOrderByHolidayDateAsc()
                : holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        return onlyMyCompany(all, Holiday::getCompanyId);
    }

    /**
     * Keep the rows that belong to the caller's company.
     *
     * Written out here rather than left to the Hibernate tenant filter, because
     * the filter did not apply on this path and the leak survived two attempts to
     * fix it indirectly — first by declaring the filter on the entity, then by
     * putting the company into the cache key. A company-1 account was still handed
     * company-4 holidays each time. This is checked in the one place the list is
     * produced, where it can be read and tested.
     *
     * Rows with no company are treated as shared. Nothing is stamped that way
     * today (V94 filled every existing row), but a row inserted outside a request
     * — a migration, a scheduled job — would otherwise disappear for everybody.
     *
     * A caller with no company of their own, such as a technical admin, sees
     * everything; that is their job.
     */
    private <T> List<T> onlyMyCompany(List<T> rows, java.util.function.Function<T, Long> companyOf) {
        Long mine = com.pixous.hrportal.security.SecurityUtils.currentCompanyId();
        if (mine == null) return rows;
        return rows.stream()
                .filter(r -> companyOf.apply(r) == null || mine.equals(companyOf.apply(r)))
                .toList();
    }

    // A new holiday must show on the calendar immediately — everybody is told about
    // it by SMS in the same call, and they will go and look.
    @CacheEvict(cacheNames = CacheConfig.HOLIDAYS, allEntries = true)
    @Transactional
    public Holiday createHoliday(com.pixous.hrportal.modules.org.dto.HolidayRequest req) {
        Holiday h = new Holiday();
        h.setName(req.name());
        h.setHolidayDate(req.holidayDate());
        Holiday saved = holidayRepository.save(h);

        // Tell every active employee about the new calendar entry — in-app and by SMS.
        String when = saved.getHolidayDate() == null ? "" : saved.getHolidayDate().toString();
        var recipients = userRepository.findByEnabledTrue().stream()
                .filter(u -> !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus()))
                .toList();
        recipients.forEach(u -> notificationService.createAndPush(u.getId(),
                "New calendar entry: " + saved.getName(),
                saved.getName() + " on " + when,
                "CALENDAR", "/calendar"));
        smsService.sendBulk(
                recipients.stream()
                        .map(com.pixous.hrportal.modules.user.User::getPhone)
                        .filter(p -> p != null && !p.isBlank())
                        .toList(),
                "Pixous HR: New calendar entry — " + saved.getName() + " on " + when + ".");
        return saved;
    }

    @CacheEvict(cacheNames = CacheConfig.HOLIDAYS, allEntries = true)
    @Transactional
    public void deleteHoliday(Long id) {
        holidayRepository.deleteById(id);
    }

    /** Create a new designation ("team"). Rejects blank or duplicate names. */
    @CacheEvict(cacheNames = CacheConfig.MASTERS, allEntries = true)
    @Transactional
    public DropdownItem createDesignation(String name, String industry) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw ApiException.business("Team name is required");
        }
        for (Designation existing : designationRepository.findByActiveTrueOrderByNameAsc()) {
            if (existing.getName().equalsIgnoreCase(clean)) {
                throw ApiException.conflict("A team named '" + clean + "' already exists");
            }
        }
        String ind = normalizeIndustry(industry);
        Designation d = new Designation();
        d.setName(clean);
        d.setIndustry(ind == null ? "IT" : ind);
        d.setActive(true);
        String code = clean.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        d.setCode(code.length() > 40 ? code.substring(0, 40) : code);
        d = designationRepository.save(d);
        return new DropdownItem(d.getId(), d.getName());
    }

    /**
     * Delete a team (designation) by name: clears the designation from every
     * member (title + id) so they move to "No designation", then removes the
     * designation record(s) with that name.
     */
    @CacheEvict(cacheNames = CacheConfig.MASTERS, allEntries = true)
    @Transactional
    public void deleteTeamByName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw ApiException.business("Team name is required");
        }
        java.util.List<Designation> matches = designationRepository.findAll().stream()
                .filter(d -> d.getName() != null && d.getName().trim().equalsIgnoreCase(clean))
                .toList();
        java.util.Set<Long> desigIds = matches.stream().map(Designation::getId)
                .collect(java.util.stream.Collectors.toSet());

        // Detach every member from this team.
        for (com.pixous.hrportal.modules.user.User u : userRepository.findAll()) {
            boolean touched = false;
            if (u.getDesignationTitle() != null
                    && u.getDesignationTitle().trim().equalsIgnoreCase(clean)) {
                u.setDesignationTitle(null);
                touched = true;
            }
            if (u.getDesignationId() != null && desigIds.contains(u.getDesignationId())) {
                u.setDesignationId(null);
                touched = true;
            }
            if (touched) userRepository.save(u);
        }
        matches.forEach(designationRepository::delete);
    }

    /**
     * Public because the cache key expression above calls it: "blood-group" and
     * "blood_group" are the same dropdown and must not occupy two cache entries
     * that can then disagree with each other.
     */
    public String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase().replace('-', '_');
    }

    /**
     * Canonicalise an industry filter to the codes stored in the DB.
     * Accepts the UI labels too ("DIGITAL"/"INFRA"). {@code null}/blank means
     * "no filter" so every active designation is returned.
     */
    private String normalizeIndustry(String industry) {
        if (industry == null || industry.isBlank()) {
            return null;
        }
        String v = industry.trim().toUpperCase();
        return switch (v) {
            case "CIVIL", "INFRA" -> "CIVIL";
            case "IT", "DIGITAL" -> "IT";
            default -> v;
        };
    }
}
