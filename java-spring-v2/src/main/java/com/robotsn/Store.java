package com.robotsn;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
@Service
public class Store {
 public final JdbcTemplate db; public final ObjectMapper json;
 public Store(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 public static void fail(int code,String msg){throw new ResponseStatusException(HttpStatus.valueOf(code),msg);}
 public String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException(e);}}
 public Map<String,Object> map(Object o){if(o instanceof Map<?,?> m){var out=new LinkedHashMap<String,Object>();m.forEach((k,v)->out.put(k.toString(),v));return out;}try{return json.readValue(o.toString(),new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalArgumentException(e);}}
 public Object decode(Object o){if(o==null)return null;try{return json.readValue(o.toString(),Object.class);}catch(Exception e){throw new IllegalArgumentException(e);}}
 public String actor(){var a=SecurityContextHolder.getContext().getAuthentication();return a==null?"SYSTEM":a.getName();}
 public Map<String,Object> account(){return db.queryForMap("select u.username,u.user_id,u.role,u.active,u.sn_permission,u.scope_sns,u.scope_customer,u.auth_version,case when r.deleted then null else b.robot_id end bound_robot_id,r.sn bound_sn,coalesce((select sum(revision+1) from field_def),0) definition_version from sys_user u left join user_robot_binding b on b.user_id=u.user_id left join robot r on r.robot_id=b.robot_id where u.username=?",actor());}
 public String role(){return account().get("role").toString();}
 public void admin(){if(!role().equals("ADMIN"))fail(403,"仅管理员可以执行此操作");}
 public void technical(){if(role().equals("USER"))fail(403,"仅管理员和技术人员可以执行此操作");}
 public void snAccess(){var a=account();if(!a.get("role").equals("ADMIN")&&!(a.get("role").equals("TECHNICIAN")&&Boolean.TRUE.equals(a.get("sn_permission"))))fail(403,"未获得 SN 业务操作授权");}
 public String text(Map<String,Object> b,String k){Object v=b.get(k);return v==null?"":v.toString().trim();}
 public String required(Map<String,Object>b,String k){String s=text(b,k);if(s.isEmpty()||s.length()>4000)fail(400,"请填写有效的 "+k);return s;}
 public int integer(Map<String,Object>b,String k){return Integer.parseInt(required(b,k));}
 public void reason(Map<String,Object>b){required(b,"reason");}
 public Map<String,Object> one(String sql,Object...args){var rows=db.queryForList(sql,args);if(rows.isEmpty())fail(404,"记录不存在");return rows.get(0);}
 public void audit(String type,String id,String action,String field,Object old,Object value,String reason){db.update("insert into audit_log(object_type,object_id,action,field_name,old_value,new_value,actor,reason) values (?,?,?,?,?::jsonb,?::jsonb,?,?)",type,id,action,field,encode(old),encode(value),actor(),reason);}
 public Map<String,Object> setting(String key){return map(one("select value from settings where key=?",key).get("value"));}
 public List<?> arraySetting(String key){return (List<?>)decode(one("select value from settings where key=?",key).get("value"));}
 public List<Map<String,Object>> definitions(boolean all){return db.queryForList("select * from field_def where active"+(all?"":" and customer_visible")+" order by ordinal,key");}
 public String resolve(String sn){var rows=db.queryForList("select sn from robot where sn=?",sn);if(!rows.isEmpty())return sn;var alias=db.queryForList("select robot_sn from sn_history where old_sn=?",sn);return alias.isEmpty()?sn:alias.get(0).get("robot_sn").toString();}
 public boolean allowed(Map<String,Object> row){var a=account();String role=a.get("role").toString();if(role.equals("ADMIN"))return true;
   if(role.equals("USER"))return a.get("bound_robot_id")!=null&&a.get("bound_robot_id").equals(row.get("robot_id"));
   var sns=(List<?>)decode(a.get("scope_sns"));return sns.isEmpty()||sns.stream().anyMatch(x->resolve(x.toString()).equals(row.get("sn")));
 }
 public Map<String,Object> robot(String sn,boolean lock){String current=resolve(sn);var r=one("select * from robot where sn=? and not deleted"+(lock?" for update":""),current);if(!allowed(r))fail(403,"该机器人不在您的数据范围内");return r;}
 public Map<String,Object> visible(Map<String,Object> r){var f=map(r.get("fields"));var out=new LinkedHashMap<String,Object>();out.put("sn",r.get("sn"));out.put("robotId",r.get("robot_id"));out.put("revision",r.get("revision"));var clean=new LinkedHashMap<String,Object>();for(var d:definitions(!role().equals("USER"))){Object value=f.get(d.get("key").toString());if("SN".equals(d.get("storage_kind")))value=r.get("sn");if("MODULE".equals(d.get("storage_kind")))value=db.queryForList("select module_sn from module_installation where robot_sn=? and slot=? and removed_at is null order by id",r.get("sn"),d.get("module_slot")).stream().map(m->m.get("module_sn").toString()).collect(java.util.stream.Collectors.joining(" / "));clean.put(d.get("key").toString(),value);}out.put("fields",clean);return out;}
 public Map<String,Object> snapshot(String sn){var r=one("select sn,fields,revision from robot where sn=?",sn);r.put("fields",map(r.get("fields")));r.put("modules",db.queryForList("select slot,module_sn,version from module_installation where robot_sn=? and removed_at is null order by slot",sn));return r;}
 public void revision(Map<String,Object>b,Map<String,Object>r){if(!Objects.equals(integer(b,"revision"),((Number)r.get("revision")).intValue()))fail(409,"记录已被其他用户修改，请刷新后重试");}
 public Map<String,Object> validateFields(Map<String,Object> values){var found=new LinkedHashMap<String,Map<String,Object>>();for(var d:definitions(true))found.put(d.get("key").toString(),d);var result=new LinkedHashMap<String,Object>();
   for(var e:values.entrySet()){var d=found.get(e.getKey());if(d==null||!"FIELD".equals(d.get("storage_kind")))fail(400,"字段不存在、已归档或由系统关联管理："+e.getKey());Object raw=e.getValue();Object value=raw;
     boolean empty=raw==null||raw instanceof String st&&st.isBlank();if(empty){if(Boolean.TRUE.equals(d.get("required")))fail(400,d.get("label")+"为必填项");result.put(e.getKey(),null);continue;}
     String type=d.get("data_type").toString();if(type.equals("number")){try{if(!(raw instanceof Number)&&!(raw instanceof String))throw new IllegalArgumentException();value=new java.math.BigDecimal(raw.toString());}catch(Exception ex){fail(400,d.get("label")+"必须是数字");}}
     else if(type.equals("boolean")){if(raw instanceof Boolean)value=raw;else if(raw.equals("true")||raw.equals("false"))value=Boolean.valueOf(raw.toString());else fail(400,d.get("label")+"必须是是或否");}
     else {if(!(raw instanceof String))fail(400,"字段必须为文本");String v=raw.toString().trim();if(v.length()>4000)fail(400,"字段过长");if(e.getKey().equals("model")&&v.length()>80)fail(400,"产品型号最长 80 个字符");
       if(type.equals("date")){try{LocalDate.parse(v);}catch(Exception ex){fail(400,"日期应为 YYYY-MM-DD");}}if(type.equals("mac")&&!v.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))fail(400,"MAC 地址格式应为 AA:BB:CC:DD:EE:FF");if(e.getKey().equals("status")&&!arraySetting("statuses").contains(v))fail(400,"状态不在字典中");value=v;
     }result.put(e.getKey(),value);
   }return result;
 }
 public void requiredOnCreate(Map<String,Object> fields){for(var d:definitions(true))if(Boolean.TRUE.equals(d.get("required"))&&"FIELD".equals(d.get("storage_kind"))&&!fields.containsKey(d.get("key")))fail(400,d.get("label")+"为必填项");}
 public void qcGuard(String sn,String status){if(Set.of("已出库","已交付").contains(status)){var f=map(one("select fields from robot where sn=?",sn).get("fields"));if(!"通过".equals(f.get("qcResult")))fail(409,"请先完成结果为“通过”的质检，再出库或交付");}}
 public void status(String sn,String next,String why){qcGuard(sn,next);var r=one("select fields from robot where sn=?",sn);var f=map(r.get("fields"));Object old=f.get("status");if(Objects.equals(old,next))return;
   if("已报废".equals(old))fail(409,"已报废设备不可恢复；请使用维修替换或新建主档流程");
   f.put("status",next);db.update("update robot set fields=?::jsonb,revision=revision+1 where sn=?",encode(f),sn);
   db.update("insert into lifecycle_history(robot_sn,old_status,new_status,actor,reason) values (?,?,?,?,?)",sn,old,next,actor(),why);audit("robot",sn,"STATUS","status",old,next,why);
 }
 public String month(Map<String,Object>b){String m=required(b,"month");if(!m.matches("[0-9]{2}(0[1-9]|1[0-2])"))fail(400,"年月必须是有效的 YYMM，如 2609");return m;}
 public String type(Map<String,Object>b){String t=required(b,"type");if(!List.of("S","P","M","R").contains(t)||!((List<?>)setting("sn_rule").get("types")).contains(t))fail(400,"该生产类型不可用");return t;}
 /** Caller holds a transaction. Counter row serializes reservations across all app instances. */
 public List<String> allocate(Map<String,Object>b,boolean range){String m=month(b),t=type(b);int count=integer(b,"count");if(count<1||count>1000)fail(400,"一次生成数量为 1–1000");
   db.update("insert into sn_counter(month,type,last_seq) values (?,?,0) on conflict do nothing",m,t);
   int last=((Number)one("select last_seq from sn_counter where month=? and type=? for update",m,t).get("last_seq")).intValue();int max=((Number)setting("sn_rule").get("maxSequence")).intValue();if(last+count>max)fail(409,"当月该类型流水号已超出上限");
   String why=required(b,"reason");String assignee=text(b,"assignee");if(assignee.isEmpty())assignee=actor();if(db.queryForObject("select count(*) from sys_user where username=? and active",Integer.class,assignee)==0)fail(400,"号段负责人不存在或已禁用");
   Long id=null;if(range)id=db.queryForObject("insert into sn_range(month,type,start_seq,end_seq,assignee,actor,reason) values (?,?,?,?,?,?,?) returning id",Long.class,m,t,last+1,last+count,assignee,actor(),why);
   var out=new ArrayList<String>();for(int i=last+1;i<=last+count;i++){String sn="LBR-"+m+"-"+t+"-"+String.format("%04d",i);db.update("insert into sn_registry(sn,status,range_id,actor) values (?,'UNUSED',?,?)",sn,id,actor());out.add(sn);}
   db.update("update sn_counter set last_seq=? where month=? and type=?",last+count,m,t);audit("sn",m+"-"+t,range?"RANGE":"GENERATE","sn",null,out,why);return out;
 }
 public void claim(String sn){int n=db.update("update sn_registry set status='USED' where sn=? and status='UNUSED'",sn);if(n!=1)fail(409,"SN 不存在、已使用或已作废");}
}
