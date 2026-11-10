-- V3: Row Level Security for tenant isolation, plus non-owner login role app_rw.
--
-- New tenant tables (V4 conversation history and anything after it) must copy this checklist.
-- DEFAULT PRIVILEGES below grant DML to app_rw for later tables created by this owner.
-- RLS is NOT inherited. Each new tenant table still needs all of the following:
--   1. tenant_id NOT NULL
--   2. ENABLE ROW LEVEL SECURITY
--   3. FORCE ROW LEVEL SECURITY  (without FORCE the table owner bypasses the policy)
--   4. CREATE POLICY ... FOR ALL TO PUBLIC
--        USING (tenant_id = current_setting('app.tenant_id', true))
--        WITH CHECK (tenant_id = current_setting('app.tenant_id', true))
--   5. GRANT SELECT, INSERT, UPDATE, DELETE to app_rw if the table is not covered by DEFAULT PRIVILEGES
--
-- current_setting(..., true) returns NULL when app.tenant_id is unset. The comparison
-- fails, so SELECT yields 0 rows and INSERT/UPDATE is rejected by WITH CHECK.
-- Do not add a policy that allows access when the setting is missing.
--
-- Explicit WHERE tenant_id = ? in application SQL stays. RLS is defense in depth.
-- Superusers bypass FORCE RLS. The application datasource must be app_rw
-- (LOGIN, NOBYPASSRLS, not the table owner). Flyway runs as the owner.
--
-- The role password is a Flyway placeholder named appRwPassword, injected from
-- DATABASE_APP_RW_PASSWORD. This file must not contain a real password.
-- V3 is the only place that creates app_rw. Do not also CREATE ROLE in an init script.
-- Rotating the password after V3 has been applied is an owner ALTER ROLE, because
-- Flyway will not run this script again.

DO $body$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_rw') THEN
        CREATE ROLE app_rw
            LOGIN
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOINHERIT
            NOBYPASSRLS;
    END IF;
END
$body$;

-- Correct attributes even if the role already existed, and set the password from the placeholder.
ALTER ROLE app_rw WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT
    NOBYPASSRLS
    PASSWORD '${appRwPassword}';

DO $body$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO app_rw', current_database());
END
$body$;

GRANT USAGE ON SCHEMA public TO app_rw;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE document, chunk TO app_rw;
GRANT USAGE ON TYPE public.vector TO app_rw;

-- chunk.id uses gen_random_uuid(); there is no sequence today. Cover later sequences anyway.
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO app_rw;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_rw;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO app_rw;

-- app_rw can read migration history (the IT checks V1 from the app connection)
-- but must not insert, update, delete, or truncate it.
REVOKE ALL ON TABLE flyway_schema_history FROM PUBLIC;
REVOKE ALL ON TABLE flyway_schema_history FROM app_rw;
GRANT SELECT ON TABLE flyway_schema_history TO app_rw;

ALTER TABLE document ENABLE ROW LEVEL SECURITY;
ALTER TABLE document FORCE ROW LEVEL SECURITY;
ALTER TABLE chunk ENABLE ROW LEVEL SECURITY;
ALTER TABLE chunk FORCE ROW LEVEL SECURITY;

DO $body$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename = 'document'
          AND policyname = 'document_tenant_isolation'
    ) THEN
        EXECUTE $policy$
            CREATE POLICY document_tenant_isolation ON document
                FOR ALL
                TO PUBLIC
                USING (tenant_id = current_setting('app.tenant_id', true))
                WITH CHECK (tenant_id = current_setting('app.tenant_id', true))
        $policy$;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename = 'chunk'
          AND policyname = 'chunk_tenant_isolation'
    ) THEN
        EXECUTE $policy$
            CREATE POLICY chunk_tenant_isolation ON chunk
                FOR ALL
                TO PUBLIC
                USING (tenant_id = current_setting('app.tenant_id', true))
                WITH CHECK (tenant_id = current_setting('app.tenant_id', true))
        $policy$;
    END IF;
END
$body$;

COMMENT ON POLICY document_tenant_isolation ON document IS
    'Tenant isolation. Unset app.tenant_id yields NULL and denies the row. Applies to PUBLIC, including the table owner when FORCE is set. Superusers still bypass.';
COMMENT ON POLICY chunk_tenant_isolation ON chunk IS
    'Tenant isolation. Unset app.tenant_id yields NULL and denies the row. Applies to PUBLIC, including the table owner when FORCE is set. Superusers still bypass.';
