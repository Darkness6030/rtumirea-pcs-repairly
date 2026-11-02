CREATE TABLE app_user (
                          id UUID PRIMARY KEY,
                          email VARCHAR(254) NOT NULL UNIQUE,
                          display_name VARCHAR(160) NOT NULL,
                          password_hash VARCHAR(100) NOT NULL,
                          enabled BOOLEAN NOT NULL DEFAULT TRUE,
                          password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
                          failed_login_attempts INTEGER NOT NULL DEFAULT 0,
                          locked_until TIMESTAMPTZ,
                          created_at TIMESTAMPTZ NOT NULL,
                          updated_at TIMESTAMPTZ NOT NULL,
                          version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE app_user_role (
                               user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                               role VARCHAR(32) NOT NULL,
                               PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_session (
                                 id UUID PRIMARY KEY,
                                 user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                                 token_hash VARCHAR(64) NOT NULL UNIQUE,
                                 expires_at TIMESTAMPTZ NOT NULL,
                                 revoked_at TIMESTAMPTZ,
                                 replaced_by UUID,
                                 user_agent VARCHAR(500),
                                 ip_address VARCHAR(64),
                                 created_at TIMESTAMPTZ NOT NULL,
                                 last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_user
    ON refresh_session(user_id);

CREATE INDEX idx_refresh_expires
    ON refresh_session(expires_at);



ALTER TABLE clients ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE;
ALTER TABLE clients ADD COLUMN code VARCHAR(50);
UPDATE clients SET code='C-'||id;
ALTER TABLE clients ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX idx_clients_code ON clients(lower(code));
ALTER TABLE clients ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE clients ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE clients ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE clients ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_clients_name ON clients(lower(name));

ALTER TABLE technicians ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE;
ALTER TABLE technicians ADD COLUMN code VARCHAR(50);
UPDATE technicians SET code='T-'||id;
ALTER TABLE technicians ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX idx_technicians_code ON technicians(lower(code));
ALTER TABLE technicians ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE technicians ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE technicians ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE technicians ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_technicians_name ON technicians(lower(name));

ALTER TABLE repair_requests ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE;
ALTER TABLE repair_requests ADD COLUMN external_id VARCHAR(100);
UPDATE repair_requests SET external_id='R-'||id;
ALTER TABLE repair_requests ALTER COLUMN external_id SET NOT NULL;
-- Defaults keep the desktop client's INSERT compatible; backend supplies its explicit business code.
ALTER TABLE repair_requests ALTER COLUMN external_id SET DEFAULT ('R-'||gen_random_uuid()::text);
CREATE UNIQUE INDEX idx_repairs_external_id ON repair_requests(lower(external_id));
ALTER TABLE repair_requests ALTER COLUMN version TYPE BIGINT;
ALTER TABLE repair_requests ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Europe/Moscow';
ALTER TABLE repair_requests ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'Europe/Moscow';
ALTER TABLE repair_requests ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
UPDATE repair_requests SET updated_at=created_at;
ALTER TABLE repair_requests ADD COLUMN urgent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE repair_requests ADD COLUMN priority_level VARCHAR(32) NOT NULL DEFAULT 'LOW';
UPDATE repair_requests SET priority_level=CASE WHEN status IN ('COMPLETED','CANCELLED') THEN 'NOT_APPLICABLE'
 WHEN status='WAITING_PARTS' OR cost>=10000 THEN 'MEDIUM' ELSE 'LOW' END;
ALTER TABLE repair_requests ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE repair_requests ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE repair_requests ADD COLUMN archived_by UUID REFERENCES app_user(id);
ALTER TABLE repair_requests ADD COLUMN created_by UUID REFERENCES app_user(id);
ALTER TABLE repair_requests ADD COLUMN updated_by UUID REFERENCES app_user(id);
ALTER TABLE repair_requests ADD CONSTRAINT chk_repair_priority CHECK(priority_level IN ('NOT_APPLICABLE','LOW','MEDIUM','HIGH','CRITICAL'));
CREATE INDEX idx_repairs_archive ON repair_requests(archived);
CREATE INDEX idx_repairs_priority ON repair_requests(priority_level);
CREATE INDEX idx_repairs_cost ON repair_requests(cost);
CREATE INDEX idx_repairs_urgent ON repair_requests(urgent);
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_repairs_search ON repair_requests USING gin ((lower(appliance_type||' '||brand||' '||model||' '||problem)) gin_trgm_ops);
CREATE TABLE repair_tag (
 repair_id UUID NOT NULL REFERENCES repair_requests(public_id) ON DELETE CASCADE,
 tag VARCHAR(50) NOT NULL CHECK(length(trim(tag)) BETWEEN 1 AND 50),PRIMARY KEY(repair_id,tag));
CREATE INDEX idx_repair_tag_value ON repair_tag(tag);
CREATE TABLE repair_note (
 id UUID PRIMARY KEY, repair_id UUID NOT NULL REFERENCES repair_requests(public_id) ON DELETE CASCADE,
 author_id UUID NOT NULL REFERENCES app_user(id),body VARCHAR(4000) NOT NULL CHECK(length(trim(body))>0),
 pinned BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,version BIGINT NOT NULL DEFAULT 0);
CREATE INDEX idx_note_repair ON repair_note(repair_id,pinned DESC,created_at DESC);
CREATE TABLE audit_event (
                             id UUID PRIMARY KEY,
                             actor_id UUID,
                             actor_email VARCHAR(254),
                             event_type VARCHAR(80) NOT NULL,
                             entity_type VARCHAR(80),
                             entity_id UUID,
                             summary VARCHAR(1000) NOT NULL,
                             correlation_id VARCHAR(100),
                             ip_address VARCHAR(64),
                             user_agent VARCHAR(500),
                             occurred_at TIMESTAMPTZ NOT NULL,
                             details_json TEXT
);

CREATE INDEX idx_audit_occurred
    ON audit_event(occurred_at DESC);

CREATE INDEX idx_audit_actor
    ON audit_event(actor_email);

CREATE INDEX idx_audit_entity
    ON audit_event(entity_type, entity_id);

CREATE FUNCTION repairly_derived_fields() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 NEW.priority_level := CASE WHEN NEW.status IN ('COMPLETED','CANCELLED') THEN 'NOT_APPLICABLE'
  WHEN NEW.urgent AND NEW.status='WAITING_PARTS' THEN 'CRITICAL' WHEN NEW.urgent THEN 'HIGH'
  WHEN NEW.status='WAITING_PARTS' OR NEW.cost>=10000 THEN 'MEDIUM' ELSE 'LOW' END;
 NEW.updated_at := now();
 RETURN NEW;
END $$;
CREATE TRIGGER repairly_derived_before BEFORE INSERT OR UPDATE ON repair_requests FOR EACH ROW EXECUTE FUNCTION repairly_derived_fields();
ALTER TABLE clients ALTER COLUMN code SET DEFAULT ('C-'||gen_random_uuid()::text);
ALTER TABLE technicians ALTER COLUMN code SET DEFAULT ('T-'||gen_random_uuid()::text);

ALTER TABLE app_user ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX idx_user_email_ci ON app_user(lower(email));
