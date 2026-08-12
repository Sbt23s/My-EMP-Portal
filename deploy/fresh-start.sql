-- =====================================================================
--  FRESH START — clears the entries made while testing, keeps the people
-- =====================================================================
--
--  Run this ONCE, by hand, when you want to begin working for real on a
--  clean slate. It is deliberately NOT a Flyway migration: nothing here
--  should ever run by itself, on a deploy or anywhere else.
--
--  WHAT IT REMOVES — the entries, the day-to-day records:
--    attendance punches, leave requests, permission requests,
--    support tickets and their replies, complaints, expense claims,
--    tasks, work reports, assets and their allocations, payslips and
--    payroll runs, notifications, chat messages, announcements,
--    safety incidents, performance goals and reviews, onboarding
--    checklists, login history, the audit log and any pending OTPs.
--
--  WHAT IT KEEPS — everybody and everything they are:
--    users, their logins, roles and permissions,
--    teams, departments, designations, positions, office locations,
--    branches, companies, sites, shifts, employment statuses,
--    bank details, education, experience, family members, skills,
--    employee documents, salary structures, investment declarations,
--    offboarding records, leave types and their entitlements,
--    holidays, chat channels and who is in them, system settings and
--    the assistant's knowledge base.
--
--  Leave entitlements are kept and only the "used" figure is set back to
--  zero, so nobody loses the days they were allotted.
--
--  Signed-in sessions are left alone, so no one is thrown out mid-work.
--
-- ---------------------------------------------------------------------
--  BEFORE YOU RUN IT — take a backup. This cannot be undone:
--
--    mysqldump -u root -p hr > hr-before-fresh-start.sql
--
--  THEN:
--
--    mysql -u root -p hr < deploy/fresh-start.sql
--
-- =====================================================================

START TRANSACTION;

-- ---- Support desk: replies before the tickets they belong to ----------
DELETE FROM ticket_comments;
DELETE FROM tickets;

-- ---- Complaints ------------------------------------------------------
DELETE FROM complaints_needs;

-- ---- Expense claims --------------------------------------------------
DELETE FROM ta_expenses;
DELETE FROM expense_claims;

-- ---- Tasks and work logs ---------------------------------------------
DELETE FROM tasks;
DELETE FROM work_reports;

-- ---- Attendance ------------------------------------------------------
DELETE FROM attendance;

-- ---- Leave and permission -------------------------------------------
DELETE FROM leave_requests;
DELETE FROM permission_requests;
-- The allotment stays; only what was consumed by the deleted requests goes.
UPDATE leave_balances SET used = 0;

-- ---- Assets: allocations and service history before the assets -------
DELETE FROM asset_allocations;
DELETE FROM asset_maintenance;
DELETE FROM assets;
DELETE FROM software_licenses;

-- ---- Payroll: payslips before the runs that produced them -------------
DELETE FROM payslips;
DELETE FROM payslip_requests;
DELETE FROM payroll_runs;

-- ---- Onboarding progress: tasks before their checklists ---------------
DELETE FROM onboarding_tasks;
DELETE FROM onboarding_checklists;

-- ---- Safety and performance ------------------------------------------
DELETE FROM safety_incidents;
DELETE FROM performance_goals;
DELETE FROM performance_reviews;

-- ---- Chat: the messages, not the channels or their members -----------
DELETE FROM community_messages;
DELETE FROM announcements;

-- ---- Notifications and trails ----------------------------------------
DELETE FROM notifications;
DELETE FROM login_history;
DELETE FROM audit_log;
DELETE FROM otp_codes;

-- ---- Numbering starts again at one -----------------------------------
-- So the first claim is CLM-...-000001 and the first ticket TKT-...-00001
-- rather than carrying on from the test data.
ALTER TABLE ticket_comments      AUTO_INCREMENT = 1;
ALTER TABLE tickets              AUTO_INCREMENT = 1;
ALTER TABLE complaints_needs     AUTO_INCREMENT = 1;
ALTER TABLE ta_expenses          AUTO_INCREMENT = 1;
ALTER TABLE expense_claims       AUTO_INCREMENT = 1;
ALTER TABLE tasks                AUTO_INCREMENT = 1;
ALTER TABLE work_reports         AUTO_INCREMENT = 1;
ALTER TABLE attendance           AUTO_INCREMENT = 1;
ALTER TABLE leave_requests       AUTO_INCREMENT = 1;
ALTER TABLE permission_requests  AUTO_INCREMENT = 1;
ALTER TABLE asset_allocations    AUTO_INCREMENT = 1;
ALTER TABLE asset_maintenance    AUTO_INCREMENT = 1;
ALTER TABLE assets               AUTO_INCREMENT = 1;
ALTER TABLE software_licenses    AUTO_INCREMENT = 1;
ALTER TABLE payslips             AUTO_INCREMENT = 1;
ALTER TABLE payslip_requests     AUTO_INCREMENT = 1;
ALTER TABLE payroll_runs         AUTO_INCREMENT = 1;
ALTER TABLE onboarding_tasks     AUTO_INCREMENT = 1;
ALTER TABLE onboarding_checklists AUTO_INCREMENT = 1;
ALTER TABLE safety_incidents     AUTO_INCREMENT = 1;
ALTER TABLE performance_goals    AUTO_INCREMENT = 1;
ALTER TABLE performance_reviews  AUTO_INCREMENT = 1;
ALTER TABLE community_messages   AUTO_INCREMENT = 1;
ALTER TABLE announcements        AUTO_INCREMENT = 1;
ALTER TABLE notifications        AUTO_INCREMENT = 1;
ALTER TABLE login_history        AUTO_INCREMENT = 1;
ALTER TABLE audit_log            AUTO_INCREMENT = 1;

COMMIT;

-- =====================================================================
--  CHECK IT DID WHAT IT SAID
--  The "cleared" rows should all read 0; the "kept" rows should not.
-- =====================================================================

SELECT 'cleared: attendance'      AS what, COUNT(*) AS rows_left FROM attendance
UNION ALL SELECT 'cleared: leave requests',    COUNT(*) FROM leave_requests
UNION ALL SELECT 'cleared: permission requests', COUNT(*) FROM permission_requests
UNION ALL SELECT 'cleared: tickets',           COUNT(*) FROM tickets
UNION ALL SELECT 'cleared: complaints',        COUNT(*) FROM complaints_needs
UNION ALL SELECT 'cleared: claims',            COUNT(*) FROM ta_expenses
UNION ALL SELECT 'cleared: tasks',             COUNT(*) FROM tasks
UNION ALL SELECT 'cleared: work reports',      COUNT(*) FROM work_reports
UNION ALL SELECT 'cleared: assets',            COUNT(*) FROM assets
UNION ALL SELECT 'cleared: asset allocations', COUNT(*) FROM asset_allocations
UNION ALL SELECT 'cleared: payslips',          COUNT(*) FROM payslips
UNION ALL SELECT 'cleared: notifications',     COUNT(*) FROM notifications
UNION ALL SELECT 'cleared: chat messages',     COUNT(*) FROM community_messages
UNION ALL SELECT '--------------------',       NULL
UNION ALL SELECT 'KEPT: employees',            COUNT(*) FROM users
UNION ALL SELECT 'KEPT: employee roles',       COUNT(*) FROM user_roles
UNION ALL SELECT 'KEPT: designations (teams)', COUNT(*) FROM designations
UNION ALL SELECT 'KEPT: departments',          COUNT(*) FROM departments
UNION ALL SELECT 'KEPT: leave types',          COUNT(*) FROM leave_types
UNION ALL SELECT 'KEPT: leave entitlements',   COUNT(*) FROM leave_balances
UNION ALL SELECT 'KEPT: holidays',             COUNT(*) FROM holidays
UNION ALL SELECT 'KEPT: bank details',         COUNT(*) FROM bank_details
UNION ALL SELECT 'KEPT: salary structures',    COUNT(*) FROM salary_structures
UNION ALL SELECT 'KEPT: chat channels',        COUNT(*) FROM communities
UNION ALL SELECT 'KEPT: system settings',      COUNT(*) FROM system_settings;
