package com.robotsn;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class BindingService {
 final Store s;
 public BindingService(Store s){this.s=s;}
 /** The caller is transactional; the common advisory lock orders account/binding mutations. */
 public void update(String username,Map<String,Object> b,String reason){
   var user=s.one("select user_id,role from sys_user where username=? for update",username);
   var oldRows=s.db.queryForList("select b.robot_id,r.sn,r.fields from user_robot_binding b join robot r on r.robot_id=b.robot_id where b.user_id=?",user.get("user_id"));
   Object oldId=oldRows.isEmpty()?null:oldRows.get(0).get("robot_id");
   boolean supplied=b.containsKey("boundRobotId")||b.containsKey("customerName");
   if(!supplied&&user.get("role").equals("USER"))return;
   String requested=b.containsKey("boundRobotId")?s.text(b,"boundRobotId"):supplied&&oldId!=null?oldId.toString():"";
   if(b.containsKey("customerName")&&!b.containsKey("boundRobotId")&&oldId==null&&!s.text(b,"customerName").isBlank())Store.fail(400,"请先指定绑定机器人，再保存客户名称");
   if(!user.get("role").equals("USER")&&!requested.isBlank())Store.fail(400,"只有普通用户可建立客户机器人绑定");
   UUID newId=requested.isBlank()?null:UUID.fromString(requested);
   String customer=newId==null?null:s.required(b,"customerName");
   if(customer!=null&&customer.length()>200)Store.fail(400,"客户名称最长 200 个字符");
   // Stable ordered locks serialize rebinds with SN migration and robot business writes.
   var ids=new ArrayList<String>();if(oldId!=null)ids.add(oldId.toString());if(newId!=null&&!ids.contains(newId.toString()))ids.add(newId.toString());Collections.sort(ids);
   for(String id:ids)s.one("select robot_id from robot where robot_id=?::uuid for update",id);
   Map<String,Object> target=null;
   if(newId!=null){target=s.one("select * from robot where robot_id=? and not deleted",newId);var occupants=s.db.queryForList("select user_id from user_robot_binding where robot_id=? and user_id<>?",newId,user.get("user_id"));if(!occupants.isEmpty())Store.fail(409,"该机器人已绑定其他客户账号，请先明确解绑");}
   Object oldCustomer=oldRows.isEmpty()?null:s.map(s.one("select fields from robot where robot_id=?",oldId).get("fields")).get("customer");
   if(oldId==null&&newId==null)return;
   if(Objects.equals(oldId,newId)&&Objects.equals(oldCustomer,customer))return;
   if(oldId!=null&&!Objects.equals(oldId,newId))setCustomer(oldId,null,reason);
   s.db.update("delete from user_robot_binding where user_id=?",user.get("user_id"));
   if(newId!=null){s.db.update("insert into user_robot_binding(user_id,robot_id) values (?,?)",user.get("user_id"),newId);setCustomer(newId,customer,reason);}
   s.db.update("insert into binding_history(user_id,old_robot_id,new_robot_id,old_customer,new_customer,actor_id,actor,reason) values (?,?,?,?,?,?,?,?)",user.get("user_id"),oldId,newId,oldCustomer,customer,s.account().get("user_id"),s.actor(),reason);
   var old=new LinkedHashMap<String,Object>();old.put("robotId",oldId);old.put("customerName",oldCustomer);
   var next=new LinkedHashMap<String,Object>();next.put("robotId",newId);next.put("customerName",customer);
   s.audit("user",username,"BINDING","boundRobotId",old,next,reason);
 }
 void setCustomer(Object id,String name,String reason){var row=s.one("select sn,fields from robot where robot_id=?",id);var f=s.map(row.get("fields"));Object before=f.put("customer",name);s.db.update("update robot set fields=?::jsonb,revision=revision+1 where robot_id=?",s.encode(f),id);s.audit("robot",row.get("sn").toString(),"BINDING_CUSTOMER","customer",before,name,reason);}
}
