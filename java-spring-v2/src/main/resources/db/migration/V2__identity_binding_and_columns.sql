-- Add stable identities without changing existing SN primary keys or history FKs.
ALTER TABLE sys_user ADD COLUMN user_id uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE;
ALTER TABLE sys_user ADD COLUMN auth_version bigint NOT NULL DEFAULT 0;
ALTER TABLE robot ADD COLUMN robot_id uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE;
CREATE TABLE user_robot_binding (
 user_id uuid PRIMARY KEY REFERENCES sys_user(user_id),
 robot_id uuid NOT NULL UNIQUE REFERENCES robot(robot_id),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE binding_history (
 id bigserial PRIMARY KEY, user_id uuid NOT NULL REFERENCES sys_user(user_id),
 old_robot_id uuid REFERENCES robot(robot_id), new_robot_id uuid REFERENCES robot(robot_id),
 old_customer text, new_customer text, actor_id uuid REFERENCES sys_user(user_id),
 actor text NOT NULL, reason text NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now()
);
-- Legacy scopes remain stored for historical compatibility, but never auto-bind by name.
INSERT INTO audit_log(object_type,object_id,action,field_name,old_value,new_value,actor,reason)
 SELECT 'user',username,'BINDING_POLICY_UPGRADE','scope',
 jsonb_build_object('scope_sns',scope_sns,'scope_customer',scope_customer),
 '{"binding":null,"unboundAccess":"NONE"}'::jsonb,'MIGRATION',
 '单机绑定权限升级：不从客户名称或旧范围猜测绑定，请管理员确认后建立稳定身份关联'
 FROM sys_user WHERE role='USER';
ALTER TABLE field_def DROP CONSTRAINT field_def_group_name_check;
ALTER TABLE field_def ADD CHECK(group_name IN ('GREEN','YELLOW','BLUE','ORANGE'));
ALTER TABLE field_def DROP CONSTRAINT field_def_data_type_check;
ALTER TABLE field_def ADD CHECK(data_type IN ('text','date','mac','number','boolean'));
ALTER TABLE field_def ADD COLUMN required boolean NOT NULL DEFAULT false;
ALTER TABLE field_def ADD COLUMN customer_visible boolean NOT NULL DEFAULT false;
ALTER TABLE field_def ADD COLUMN storage_kind text NOT NULL DEFAULT 'FIELD' CHECK(storage_kind IN ('FIELD','SN','MODULE'));
ALTER TABLE field_def ADD COLUMN module_slot text;
ALTER TABLE field_def ADD COLUMN revision integer NOT NULL DEFAULT 0;
UPDATE field_def SET customer_visible=(group_name='GREEN');
INSERT INTO field_def(key,label,group_name,data_type,builtin,ordinal,customer_visible,storage_kind)
 VALUES ('sn','SN码','GREEN','text',true,0,true,'SN');
INSERT INTO field_def(key,label,group_name,data_type,builtin,ordinal,storage_kind,module_slot)
 SELECT 'module_'||v.n,v.slot||'SN','ORANGE','text',true,22+v.n,'MODULE',v.slot
 FROM (VALUES(0,'主控板'),(1,'舵机控制器'),(2,'头部舵机'),(3,'左臂舵机'),(4,'右臂舵机'),(5,'腰部舵机'),(6,'左腿舵机'),(7,'右腿舵机'),(8,'麦克风阵列'),(9,'扬声器'),(10,'摄像头'),(11,'电池'),(12,'传感器模组')) v(n,slot);
CREATE INDEX binding_robot ON binding_history(new_robot_id,id);
