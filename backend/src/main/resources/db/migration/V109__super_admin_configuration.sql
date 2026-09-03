-- ============================================================
-- V109 — Super Admin configuration store, and the full role set.
--
-- Two things, both additive:
--
--   1. A configuration store the Super Admin screens read and write, so the
--      dropdowns, feature flags and settings the specification asks for live in
--      the database rather than in the client. Three tables:
--        app_settings         — typed key/value, company-scoped or global
--        config_option_sets   — a named dropdown (e.g. "leave.reason")
--        config_options       — the values inside one dropdown, ordered
--
--   2. The roles named in the specification that do not exist yet, each granted
--      from a role already carrying the right set. Same idiom as V96: rows are
--      inserted, never deleted, and only where missing, so this is idempotent
--      and no existing role loses anything.
--
-- NOTHING IS REMOVED. No existing table, column, role or grant is altered. A
-- portal that never opens the Super Admin screens behaves exactly as before.
-- ============================================================

-- ---------- 1. Typed settings ----------
-- company_id NULL means platform-wide; a company row overrides the global one.
CREATE TABLE app_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,

    company_id   BIGINT NULL,
    setting_key  VARCHAR(120) NOT NULL,
    setting_value TEXT,
    value_type   VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    category     VARCHAR(60)  NOT NULL DEFAULT 'GENERAL',
    label        VARCHAR(200),
    description  VARCHAR(500),
    -- A setting the platform owner controls; a company admin may read it but
    -- not change it. Enforced server-side, not by hiding the field.
    platform_only BOOLEAN NOT NULL DEFAULT FALSE,
    editable     BOOLEAN NOT NULL DEFAULT TRUE,

    UNIQUE KEY uq_app_settings_scope (company_id, setting_key),
    KEY idx_app_settings_cat (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 2. Dropdown definitions ----------
CREATE TABLE config_option_sets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,

    company_id  BIGINT NULL,
    set_code    VARCHAR(80)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    module      VARCHAR(60),
    description VARCHAR(500),
    -- A set the application depends on by code. Values may be added and
    -- relabelled; the set itself may not be deleted.
    system_set  BOOLEAN NOT NULL DEFAULT FALSE,

    UNIQUE KEY uq_option_set_scope (company_id, set_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE config_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,

    option_set_id BIGINT NOT NULL,
    option_code   VARCHAR(80)  NOT NULL,
    label         VARCHAR(200) NOT NULL,
    sort_order    INT NOT NULL DEFAULT 0,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    is_default    BOOLEAN NOT NULL DEFAULT FALSE,
    -- Free-form extras for options that carry data (a colour, a day count).
    metadata      TEXT,

    UNIQUE KEY uq_config_option (option_set_id, option_code),
    KEY idx_config_option_order (option_set_id, sort_order),
    CONSTRAINT fk_config_option_set FOREIGN KEY (option_set_id)
        REFERENCES config_option_sets (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 3. Permission for the configuration screens ----------
-- Distinct from USER_MANAGE on purpose: HR manages people, and that is not the
-- same authority as changing how the application behaves for everyone.
INSERT INTO permissions (code, name)
SELECT 'CONFIG_MANAGE', 'Manage application configuration'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CONFIG_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'CONFIG_MANAGE'
WHERE r.code IN ('SUPER_ADMIN', 'COMPANY_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.role_id = r.id AND x.permission_id = p.id);

-- ---------- 4. The roles from the specification that are missing ----------
-- Codes are global (roles.code lost its UNIQUE constraint for per-company
-- roles), so create only where absent by code with a NULL company.
INSERT INTO roles (code, name, industry, description)
SELECT * FROM (
    SELECT 'FINANCE_MANAGER' AS code, 'Finance Manager' AS name, 'BOTH' AS industry,
           'Payroll and expense approvals' AS description
    UNION ALL SELECT 'AUDITOR', 'Auditor', 'BOTH', 'Read-only access for audit and compliance'
    UNION ALL SELECT 'INTERN', 'Intern', 'BOTH', 'Self-service, reduced entitlements'
    UNION ALL SELECT 'CONTRACTOR', 'Contractor', 'BOTH', 'Self-service for contract staff'
) AS want
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.code = want.code);

-- Finance Manager mirrors the seeded finance officer.
INSERT INTO role_permissions (role_id, permission_id)
SELECT tgt.id, rp.permission_id
FROM roles tgt
JOIN roles src           ON src.code = 'IT_FIN'
JOIN role_permissions rp ON rp.role_id = src.id
WHERE tgt.code = 'FINANCE_MANAGER'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.role_id = tgt.id AND x.permission_id = rp.permission_id);

-- Interns and contractors are self-service, same as an employee.
INSERT INTO role_permissions (role_id, permission_id)
SELECT tgt.id, rp.permission_id
FROM roles tgt
JOIN roles src           ON src.code = 'IT_EMP'
JOIN role_permissions rp ON rp.role_id = src.id
WHERE tgt.code IN ('INTERN', 'CONTRACTOR')
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.role_id = tgt.id AND x.permission_id = rp.permission_id);

-- Auditor looks and does not touch — the same restraint as BOARD_ADMIN in V96.
INSERT INTO role_permissions (role_id, permission_id)
SELECT tgt.id, p.id
FROM roles tgt
JOIN permissions p ON p.code IN ('REPORT_VIEW', 'DASHBOARD_EXEC')
WHERE tgt.code = 'AUDITOR'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.role_id = tgt.id AND x.permission_id = p.id);
