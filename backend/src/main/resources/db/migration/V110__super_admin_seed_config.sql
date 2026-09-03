-- ============================================================
-- V110 — Seed the configuration store.
--
-- Platform-wide rows (company_id NULL) so every tenant inherits a working set
-- and the Super Admin screens open with real content rather than empty tables.
-- Values match what the application already does today, so seeding changes no
-- behaviour — it makes the current behaviour visible and editable.
--
-- Idempotent throughout: every insert is guarded by NOT EXISTS.
-- ============================================================

-- ---------- Settings ----------
INSERT INTO app_settings (company_id, setting_key, setting_value, value_type, category, label, description, platform_only)
SELECT * FROM (
  SELECT NULL AS company_id, 'security.password.min_length' AS setting_key, '8' AS setting_value, 'INT' AS value_type,
         'SECURITY' AS category, 'Minimum password length' AS label,
         'Enforced on the server when a password is set or changed.' AS description, FALSE AS platform_only
  UNION ALL SELECT NULL,'security.password.require_mixed_case','true','BOOLEAN','SECURITY','Require upper and lower case',NULL,FALSE
  UNION ALL SELECT NULL,'security.password.require_digit','true','BOOLEAN','SECURITY','Require a digit',NULL,FALSE
  UNION ALL SELECT NULL,'security.password.require_symbol','true','BOOLEAN','SECURITY','Require a symbol',NULL,FALSE
  UNION ALL SELECT NULL,'security.login.max_failed_attempts','5','INT','SECURITY','Failed logins before lockout',
         'Counted per account. The rate limiter also counts per IP address.',FALSE
  UNION ALL SELECT NULL,'security.login.lockout_minutes','15','INT','SECURITY','Lockout duration (minutes)',NULL,FALSE
  UNION ALL SELECT NULL,'security.session.idle_timeout_minutes','60','INT','SECURITY','Idle session timeout (minutes)',NULL,FALSE
  UNION ALL SELECT NULL,'attendance.geofence_default_metres','200','INT','ATTENDANCE','Default geofence radius (m)',
         'Applied to a new office location when none is given.',FALSE
  UNION ALL SELECT NULL,'attendance.late_grace_minutes','15','INT','ATTENDANCE','Late arrival grace (minutes)',NULL,FALSE
  UNION ALL SELECT NULL,'attendance.half_day_hours','4','INT','ATTENDANCE','Hours below which a day is half',NULL,FALSE
  UNION ALL SELECT NULL,'attendance.full_day_hours','8','INT','ATTENDANCE','Hours for a full day',NULL,FALSE
  UNION ALL SELECT NULL,'leave.min_notice_days','1','INT','LEAVE','Default notice required (days)',NULL,FALSE
  UNION ALL SELECT NULL,'leave.tl_approval_max_days','3','INT','LEAVE','Days a team lead may approve',
         'Above this the request goes to HR. Matches the control matrix.',FALSE
  UNION ALL SELECT NULL,'leave.allow_negative_balance','false','BOOLEAN','LEAVE','Allow a negative balance',NULL,FALSE
  UNION ALL SELECT NULL,'payroll.cutoff_day','25','INT','PAYROLL','Monthly cut-off day',NULL,FALSE
  UNION ALL SELECT NULL,'payroll.require_finance_approval','true','BOOLEAN','PAYROLL','Require finance approval',
         'A run must be approved before payslips are released.',FALSE
  UNION ALL SELECT NULL,'payroll.currency','INR','STRING','PAYROLL','Currency',NULL,FALSE
  UNION ALL SELECT NULL,'notification.absence_digest_hour','10','INT','NOTIFICATION','Absence digest hour',
         'Hour of day the leave and absence digest is sent to HR and the CTO.',FALSE
  UNION ALL SELECT NULL,'notification.email_enabled','true','BOOLEAN','NOTIFICATION','Send email notifications',NULL,FALSE
  UNION ALL SELECT NULL,'notification.sms_enabled','false','BOOLEAN','NOTIFICATION','Send SMS notifications',
         'Off because the configured gateway is rejecting the API key.',FALSE
  UNION ALL SELECT NULL,'system.backup.retention_days','30','INT','SYSTEM','Backup retention (days)',NULL,TRUE
  UNION ALL SELECT NULL,'system.audit.retention_days','365','INT','SYSTEM','Audit log retention (days)',NULL,TRUE
  UNION ALL SELECT NULL,'system.maintenance_mode','false','BOOLEAN','SYSTEM','Maintenance mode',
         'When on, only platform administrators may sign in.',TRUE
) AS want
WHERE NOT EXISTS (
  SELECT 1 FROM app_settings s
  WHERE s.setting_key = want.setting_key AND s.company_id IS NULL
);

-- ---------- Dropdown sets ----------
INSERT INTO config_option_sets (company_id, set_code, name, module, description, system_set)
SELECT * FROM (
  SELECT NULL AS company_id,'leave.request_reason' AS set_code,'Leave Reasons' AS name,'leave' AS module,
         'Offered when applying for leave.' AS description, FALSE AS system_set
  UNION ALL SELECT NULL,'leave.rejection_reason','Leave Rejection Reasons','leave','Offered when a request is declined.',FALSE
  UNION ALL SELECT NULL,'attendance.regularisation_reason','Attendance Regularisation Reasons','attendance',NULL,FALSE
  UNION ALL SELECT NULL,'wfh.reason','Work From Home Reasons','wfh',NULL,FALSE
  UNION ALL SELECT NULL,'expense.category','Expense Categories','expense',NULL,FALSE
  UNION ALL SELECT NULL,'expense.payment_mode','Expense Payment Modes','expense',NULL,FALSE
  UNION ALL SELECT NULL,'complaint.category','Complaint Categories','complaint',NULL,FALSE
  UNION ALL SELECT NULL,'complaint.severity','Complaint Severity','complaint',NULL,TRUE
  UNION ALL SELECT NULL,'discipline.type','Disciplinary Action Types','discipline',NULL,FALSE
  UNION ALL SELECT NULL,'appreciation.type','Appreciation Types','appreciation',NULL,FALSE
  UNION ALL SELECT NULL,'helpdesk.category','Helpdesk Categories','helpdesk',NULL,FALSE
  UNION ALL SELECT NULL,'helpdesk.priority','Helpdesk Priorities','helpdesk',NULL,TRUE
  UNION ALL SELECT NULL,'asset.condition','Asset Conditions','asset',NULL,FALSE
  UNION ALL SELECT NULL,'offboarding.reason','Offboarding Reasons','user',NULL,FALSE
  UNION ALL SELECT NULL,'task.priority','Task Priorities','task',NULL,TRUE
  UNION ALL SELECT NULL,'performance.rating','Performance Ratings','performance',NULL,FALSE
  UNION ALL SELECT NULL,'onboarding.document_type','Onboarding Document Types','onboarding',NULL,FALSE
  UNION ALL SELECT NULL,'safety.incident_type','Safety Incident Types','safety',NULL,FALSE
) AS want
WHERE NOT EXISTS (
  SELECT 1 FROM config_option_sets s
  WHERE s.set_code = want.set_code AND s.company_id IS NULL
);

-- ---------- Dropdown values ----------
-- Joined to the set by code so no id is assumed.
INSERT INTO config_options (option_set_id, option_code, label, sort_order, is_default)
SELECT s.id, v.option_code, v.label, v.sort_order, v.is_default
FROM config_option_sets s
JOIN (
  SELECT 'leave.request_reason' AS set_code,'PERSONAL' AS option_code,'Personal' AS label,1 AS sort_order,TRUE AS is_default
  UNION ALL SELECT 'leave.request_reason','MEDICAL','Medical',2,FALSE
  UNION ALL SELECT 'leave.request_reason','FAMILY','Family commitment',3,FALSE
  UNION ALL SELECT 'leave.request_reason','TRAVEL','Travel',4,FALSE
  UNION ALL SELECT 'leave.request_reason','FUNCTION','Family function',5,FALSE
  UNION ALL SELECT 'leave.request_reason','EXAM','Examination',6,FALSE
  UNION ALL SELECT 'leave.request_reason','OTHER','Other',99,FALSE

  UNION ALL SELECT 'leave.rejection_reason','STAFFING','Insufficient cover',1,TRUE
  UNION ALL SELECT 'leave.rejection_reason','NO_BALANCE','No leave balance',2,FALSE
  UNION ALL SELECT 'leave.rejection_reason','SHORT_NOTICE','Insufficient notice',3,FALSE
  UNION ALL SELECT 'leave.rejection_reason','PEAK','Peak business period',4,FALSE
  UNION ALL SELECT 'leave.rejection_reason','DOCS','Supporting documents missing',5,FALSE
  UNION ALL SELECT 'leave.rejection_reason','OTHER','Other',99,FALSE

  UNION ALL SELECT 'attendance.regularisation_reason','FORGOT','Forgot to punch',1,TRUE
  UNION ALL SELECT 'attendance.regularisation_reason','GEOFENCE','Outside geofence',2,FALSE
  UNION ALL SELECT 'attendance.regularisation_reason','DEVICE','Device or network problem',3,FALSE
  UNION ALL SELECT 'attendance.regularisation_reason','CLIENT','At a client site',4,FALSE
  UNION ALL SELECT 'attendance.regularisation_reason','OFFICIAL','Official duty',5,FALSE
  UNION ALL SELECT 'attendance.regularisation_reason','OTHER','Other',99,FALSE

  UNION ALL SELECT 'wfh.reason','HEALTH','Health',1,TRUE
  UNION ALL SELECT 'wfh.reason','TRANSPORT','Transport disruption',2,FALSE
  UNION ALL SELECT 'wfh.reason','WEATHER','Weather',3,FALSE
  UNION ALL SELECT 'wfh.reason','FOCUS','Focused work',4,FALSE
  UNION ALL SELECT 'wfh.reason','CARE','Family care',5,FALSE
  UNION ALL SELECT 'wfh.reason','OTHER','Other',99,FALSE

  UNION ALL SELECT 'expense.category','TRAVEL','Travel',1,TRUE
  UNION ALL SELECT 'expense.category','ACCOMMODATION','Accommodation',2,FALSE
  UNION ALL SELECT 'expense.category','FOOD','Food and refreshments',3,FALSE
  UNION ALL SELECT 'expense.category','FUEL','Fuel',4,FALSE
  UNION ALL SELECT 'expense.category','TELECOM','Telephone and internet',5,FALSE
  UNION ALL SELECT 'expense.category','STATIONERY','Stationery',6,FALSE
  UNION ALL SELECT 'expense.category','CLIENT','Client entertainment',7,FALSE
  UNION ALL SELECT 'expense.category','TRAINING','Training',8,FALSE
  UNION ALL SELECT 'expense.category','MEDICAL','Medical',9,FALSE
  UNION ALL SELECT 'expense.category','OTHER','Other',99,FALSE

  UNION ALL SELECT 'expense.payment_mode','CASH','Cash',1,TRUE
  UNION ALL SELECT 'expense.payment_mode','CARD','Company card',2,FALSE
  UNION ALL SELECT 'expense.payment_mode','UPI','UPI',3,FALSE
  UNION ALL SELECT 'expense.payment_mode','BANK','Bank transfer',4,FALSE
  UNION ALL SELECT 'expense.payment_mode','PERSONAL','Paid personally',5,FALSE

  UNION ALL SELECT 'complaint.category','HARASSMENT','Harassment',1,FALSE
  UNION ALL SELECT 'complaint.category','DISCRIMINATION','Discrimination',2,FALSE
  UNION ALL SELECT 'complaint.category','SALARY','Salary or payroll',3,FALSE
  UNION ALL SELECT 'complaint.category','WORKLOAD','Workload',4,FALSE
  UNION ALL SELECT 'complaint.category','FACILITIES','Facilities',5,FALSE
  UNION ALL SELECT 'complaint.category','MANAGEMENT','Management conduct',6,FALSE
  UNION ALL SELECT 'complaint.category','SAFETY','Safety',7,FALSE
  UNION ALL SELECT 'complaint.category','OTHER','Other',99,TRUE

  UNION ALL SELECT 'complaint.severity','LOW','Low',1,FALSE
  UNION ALL SELECT 'complaint.severity','MEDIUM','Medium',2,TRUE
  UNION ALL SELECT 'complaint.severity','HIGH','High',3,FALSE
  UNION ALL SELECT 'complaint.severity','CRITICAL','Critical',4,FALSE

  UNION ALL SELECT 'discipline.type','VERBAL','Verbal warning',1,TRUE
  UNION ALL SELECT 'discipline.type','WRITTEN','Written warning',2,FALSE
  UNION ALL SELECT 'discipline.type','MEMO','Memo',3,FALSE
  UNION ALL SELECT 'discipline.type','SHOW_CAUSE','Show cause notice',4,FALSE
  UNION ALL SELECT 'discipline.type','SUSPENSION','Suspension',5,FALSE
  UNION ALL SELECT 'discipline.type','PIP','Performance improvement plan',6,FALSE
  UNION ALL SELECT 'discipline.type','TERMINATION','Termination',7,FALSE

  UNION ALL SELECT 'appreciation.type','STAR','Star performer',1,TRUE
  UNION ALL SELECT 'appreciation.type','TEAM','Team contribution',2,FALSE
  UNION ALL SELECT 'appreciation.type','CLIENT','Client appreciation',3,FALSE
  UNION ALL SELECT 'appreciation.type','INNOVATION','Innovation',4,FALSE
  UNION ALL SELECT 'appreciation.type','MILESTONE','Service milestone',5,FALSE
  UNION ALL SELECT 'appreciation.type','EXTRA_MILE','Going the extra mile',6,FALSE

  UNION ALL SELECT 'helpdesk.category','HARDWARE','Hardware',1,FALSE
  UNION ALL SELECT 'helpdesk.category','SOFTWARE','Software',2,TRUE
  UNION ALL SELECT 'helpdesk.category','NETWORK','Network',3,FALSE
  UNION ALL SELECT 'helpdesk.category','ACCESS','Access or account',4,FALSE
  UNION ALL SELECT 'helpdesk.category','EMAIL','Email',5,FALSE
  UNION ALL SELECT 'helpdesk.category','FACILITIES','Facilities',6,FALSE
  UNION ALL SELECT 'helpdesk.category','PAYROLL','Payroll query',7,FALSE
  UNION ALL SELECT 'helpdesk.category','OTHER','Other',99,FALSE

  UNION ALL SELECT 'helpdesk.priority','LOW','Low',1,FALSE
  UNION ALL SELECT 'helpdesk.priority','MEDIUM','Medium',2,TRUE
  UNION ALL SELECT 'helpdesk.priority','HIGH','High',3,FALSE
  UNION ALL SELECT 'helpdesk.priority','URGENT','Urgent',4,FALSE

  UNION ALL SELECT 'asset.condition','NEW','New',1,TRUE
  UNION ALL SELECT 'asset.condition','GOOD','Good',2,FALSE
  UNION ALL SELECT 'asset.condition','FAIR','Fair',3,FALSE
  UNION ALL SELECT 'asset.condition','POOR','Poor',4,FALSE
  UNION ALL SELECT 'asset.condition','REPAIR','Under repair',5,FALSE
  UNION ALL SELECT 'asset.condition','SCRAP','Scrapped',6,FALSE

  UNION ALL SELECT 'offboarding.reason','RESIGNATION','Resignation',1,TRUE
  UNION ALL SELECT 'offboarding.reason','BETTER_OFFER','Better opportunity',2,FALSE
  UNION ALL SELECT 'offboarding.reason','HIGHER_STUDIES','Higher studies',3,FALSE
  UNION ALL SELECT 'offboarding.reason','RELOCATION','Relocation',4,FALSE
  UNION ALL SELECT 'offboarding.reason','HEALTH','Health',5,FALSE
  UNION ALL SELECT 'offboarding.reason','CONTRACT_END','Contract ended',6,FALSE
  UNION ALL SELECT 'offboarding.reason','RETIREMENT','Retirement',7,FALSE
  UNION ALL SELECT 'offboarding.reason','TERMINATION','Termination',8,FALSE
  UNION ALL SELECT 'offboarding.reason','ABSCONDED','Absconded',9,FALSE

  UNION ALL SELECT 'task.priority','LOW','Low',1,FALSE
  UNION ALL SELECT 'task.priority','MEDIUM','Medium',2,TRUE
  UNION ALL SELECT 'task.priority','HIGH','High',3,FALSE
  UNION ALL SELECT 'task.priority','URGENT','Urgent',4,FALSE

  UNION ALL SELECT 'performance.rating','OUTSTANDING','Outstanding',1,FALSE
  UNION ALL SELECT 'performance.rating','EXCEEDS','Exceeds expectations',2,FALSE
  UNION ALL SELECT 'performance.rating','MEETS','Meets expectations',3,TRUE
  UNION ALL SELECT 'performance.rating','PARTIAL','Partially meets',4,FALSE
  UNION ALL SELECT 'performance.rating','BELOW','Below expectations',5,FALSE

  UNION ALL SELECT 'onboarding.document_type','AADHAAR','Aadhaar',1,FALSE
  UNION ALL SELECT 'onboarding.document_type','PAN','PAN card',2,FALSE
  UNION ALL SELECT 'onboarding.document_type','PHOTO','Passport photograph',3,FALSE
  UNION ALL SELECT 'onboarding.document_type','EDUCATION','Education certificate',4,FALSE
  UNION ALL SELECT 'onboarding.document_type','EXPERIENCE','Experience letter',5,FALSE
  UNION ALL SELECT 'onboarding.document_type','RELIEVING','Relieving letter',6,FALSE
  UNION ALL SELECT 'onboarding.document_type','PAYSLIP','Previous payslip',7,FALSE
  UNION ALL SELECT 'onboarding.document_type','BANK','Bank passbook',8,FALSE
  UNION ALL SELECT 'onboarding.document_type','ADDRESS','Address proof',9,FALSE
  UNION ALL SELECT 'onboarding.document_type','MEDICAL','Medical certificate',10,FALSE

  UNION ALL SELECT 'safety.incident_type','NEAR_MISS','Near miss',1,TRUE
  UNION ALL SELECT 'safety.incident_type','FIRST_AID','First aid',2,FALSE
  UNION ALL SELECT 'safety.incident_type','INJURY','Injury',3,FALSE
  UNION ALL SELECT 'safety.incident_type','PROPERTY','Property damage',4,FALSE
  UNION ALL SELECT 'safety.incident_type','FIRE','Fire',5,FALSE
  UNION ALL SELECT 'safety.incident_type','ENVIRONMENT','Environmental',6,FALSE
) AS v ON v.set_code = s.set_code
WHERE s.company_id IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM config_options o
    WHERE o.option_set_id = s.id AND o.option_code = v.option_code
  );
