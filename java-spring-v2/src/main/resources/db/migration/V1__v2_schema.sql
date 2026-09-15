CREATE TABLE sys_user (
 username varchar(64) PRIMARY KEY, password_hash text NOT NULL,
 role varchar(16) NOT NULL CHECK (role IN ('ADMIN','TECHNICIAN','USER')),
 active boolean NOT NULL DEFAULT true, sn_permission boolean NOT NULL DEFAULT false,
 scope_sns jsonb NOT NULL DEFAULT '[]', scope_customer text NOT NULL DEFAULT '',
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE login_log (id bigserial PRIMARY KEY, username text NOT NULL, success boolean NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE audit_log (
 id bigserial PRIMARY KEY, object_type text NOT NULL, object_id text NOT NULL, action text NOT NULL,
 field_name text NOT NULL, old_value jsonb, new_value jsonb, actor text NOT NULL,
 reason text NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE settings (key text PRIMARY KEY, value jsonb NOT NULL);
INSERT INTO settings VALUES
 ('sn_rule','{"prefix":"LBR","types":["S","P","M","R"],"maxSequence":9999}'),
 ('statuses','["在库","已激活","维修中","已报废","已出库","已交付"]'),
 ('module_slots','{"主控板":1,"舵机控制器":1,"头部舵机":1,"左臂舵机":1,"右臂舵机":1,"腰部舵机":1,"左腿舵机":1,"右腿舵机":1,"麦克风阵列":1,"扬声器":1,"摄像头":1,"电池":1,"传感器模组":1}');
CREATE TABLE sn_counter (month char(4), type char(1), last_seq integer NOT NULL CHECK(last_seq BETWEEN 0 AND 9999), PRIMARY KEY(month,type));
CREATE TABLE sn_range (id bigserial PRIMARY KEY, month char(4) NOT NULL, type char(1) NOT NULL, start_seq integer NOT NULL, end_seq integer NOT NULL, assignee text NOT NULL, actor text NOT NULL, reason text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE sn_registry (sn varchar(20) PRIMARY KEY CHECK(sn ~ '^LBR-[0-9]{2}(0[1-9]|1[0-2])-[SPMR]-[0-9]{4}$' AND right(sn,4)<>'0000'), status text NOT NULL CHECK(status IN ('UNUSED','USED','VOID','RETIRED')), range_id bigint REFERENCES sn_range(id), actor text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE robot (sn varchar(20) PRIMARY KEY REFERENCES sn_registry(sn), fields jsonb NOT NULL DEFAULT '{}', revision integer NOT NULL DEFAULT 0, deleted boolean NOT NULL DEFAULT false, source text, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE field_def (key text PRIMARY KEY, label text NOT NULL, group_name text NOT NULL CHECK(group_name IN ('GREEN','YELLOW','BLUE')), data_type text NOT NULL CHECK(data_type IN ('text','date','mac')), builtin boolean NOT NULL DEFAULT false, active boolean NOT NULL DEFAULT true, ordinal integer NOT NULL DEFAULT 100);
CREATE TABLE module_history (id bigserial PRIMARY KEY, robot_sn varchar(20) NOT NULL REFERENCES robot(sn) ON UPDATE CASCADE, slot text NOT NULL, action text NOT NULL, old_sn text, new_sn text, old_version text, new_version text, actor text NOT NULL, reason text NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE module_installation (id bigserial PRIMARY KEY, robot_sn varchar(20) NOT NULL REFERENCES robot(sn) ON UPDATE CASCADE, slot text NOT NULL, module_sn text NOT NULL, version text, installed_at timestamptz NOT NULL DEFAULT now(), removed_at timestamptz);
CREATE UNIQUE INDEX module_active_sn ON module_installation(module_sn) WHERE removed_at IS NULL;
CREATE TABLE process_record (id bigserial PRIMARY KEY, robot_sn varchar(20) NOT NULL REFERENCES robot(sn) ON UPDATE CASCADE, type text NOT NULL, result text, document_no text, note text NOT NULL, operator_name text NOT NULL, actor text NOT NULL, business_date date NOT NULL, snapshot jsonb NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE lifecycle_history (id bigserial PRIMARY KEY, robot_sn varchar(20) NOT NULL REFERENCES robot(sn) ON UPDATE CASCADE, old_status text, new_status text NOT NULL, actor text NOT NULL, reason text NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE sn_history (id bigserial PRIMARY KEY, robot_sn varchar(20) NOT NULL REFERENCES robot(sn) ON UPDATE CASCADE, old_sn text NOT NULL UNIQUE, new_sn text NOT NULL, type text NOT NULL, reason text NOT NULL, evidence text NOT NULL, actor text NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE flash_task (id bigserial PRIMARY KEY, robot_sn varchar(20) NOT NULL REFERENCES robot(sn) ON UPDATE CASCADE, expected_sn text NOT NULL, device_id text NOT NULL, status text NOT NULL DEFAULT 'PENDING', actor text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE flash_readback (id bigserial PRIMARY KEY, task_id bigint NOT NULL REFERENCES flash_task(id), actual_sn text NOT NULL, matched boolean NOT NULL, evidence text NOT NULL, actor text NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE repair (id bigserial PRIMARY KEY, robot_sn varchar(20) NOT NULL REFERENCES robot(sn) ON UPDATE CASCADE, description text NOT NULL, status text NOT NULL DEFAULT '待处理', snapshot jsonb NOT NULL, actor text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE repair_event (id bigserial PRIMARY KEY, repair_id bigint NOT NULL REFERENCES repair(id), status text NOT NULL, result text NOT NULL, actor text NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE business_table (id bigserial PRIMARY KEY, name text NOT NULL, deleted boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT now());
CREATE UNIQUE INDEX business_table_name ON business_table(name) WHERE deleted=false;
CREATE TABLE business_field (id bigserial PRIMARY KEY, table_id bigint NOT NULL REFERENCES business_table(id), key text NOT NULL, label text NOT NULL, deleted boolean NOT NULL DEFAULT false, UNIQUE(table_id,key));
CREATE TABLE business_record (id bigserial PRIMARY KEY, table_id bigint NOT NULL REFERENCES business_table(id), data jsonb NOT NULL DEFAULT '{}', revision integer NOT NULL DEFAULT 0, deleted boolean NOT NULL DEFAULT false);
CREATE TABLE source_import (id bigserial PRIMARY KEY, filename text NOT NULL, sha256 text NOT NULL UNIQUE, report jsonb NOT NULL, actor text NOT NULL, imported_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE source_sheet (id bigserial PRIMARY KEY, import_id bigint NOT NULL REFERENCES source_import(id), name text NOT NULL, cells jsonb NOT NULL);
CREATE INDEX audit_object ON audit_log(object_type,object_id,id);
CREATE INDEX process_robot ON process_record(robot_sn,id);
