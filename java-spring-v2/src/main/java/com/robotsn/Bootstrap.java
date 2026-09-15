package com.robotsn;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class Bootstrap implements CommandLineRunner {
 final Store s;final PasswordEncoder passwords;final String file;
 public Bootstrap(Store s,PasswordEncoder passwords,@Value("${valenbot.bootstrap-file}")String file){this.s=s;this.passwords=passwords;this.file=file;}
 @Override @Transactional public void run(String...args)throws Exception{
   for(int i=1;i<Fields.KEYS.length;i++){if(i>=22&&i<=34)continue;String key=Fields.KEYS[i],group=i<=12?"GREEN":i<=21?"YELLOW":"BLUE";String type=(key.endsWith("Date")||key.equals("warrantyEnd"))?"date":key.endsWith("Mac")?"mac":"text";s.db.update("insert into field_def(key,label,group_name,data_type,builtin,ordinal,customer_visible) values (?,?,?,?,true,?,?) on conflict do nothing",key,Fields.LABELS[i],group,type,i,group.equals("GREEN"));}
   for(var f:List.of(new String[]{"activationDate","激活日期","GREEN","date"},new String[]{"orinVersion","Orin 版本","YELLOW","text"},new String[]{"headVersion","头部版本","YELLOW","text"},new String[]{"qcResult","质检结果","BLUE","text"}))s.db.update("insert into field_def(key,label,group_name,data_type,builtin,ordinal,customer_visible) values (?,?,?,?,true,90,?) on conflict do nothing",f[0],f[1],f[2],f[3],f[2].equals("GREEN"));
   if(s.db.queryForObject("select count(*) from sys_user",Integer.class)>0)return;
   if(file.isBlank())throw new IllegalStateException("首次启动请设置 VALENBOT_BOOTSTRAP_FILE，见 README");
   var input=s.map(Files.readString(Path.of(file)));for(Object user:(List<?>)input.get("accounts")){var u=s.map(user);String password=s.required(u,"password");if(password.length()<12)throw new IllegalArgumentException("Bootstrap password too short");s.db.update("insert into sys_user(username,password_hash,role,sn_permission) values (?,?,?,?)",s.required(u,"username"),passwords.encode(password),s.required(u,"role"),Boolean.TRUE.equals(u.get("snPermission")));s.audit("user",u.get("username").toString(),"CREATE","role",null,u.get("role"),"初始化试用账号");}
   Object source=input.get("workbook");if(source==null)return;var w=s.map(source);
   long id=s.db.queryForObject("insert into source_import(filename,sha256,report,actor) values (?,?,?::jsonb,'IMPORT') returning id",Long.class,w.get("filename"),w.get("sha256"),s.encode(w.get("report")));
   for(Object sheet:(List<?>)w.get("sheets")){var sh=s.map(sheet);s.db.update("insert into source_sheet(import_id,name,cells) values (?,?,?::jsonb)",id,sh.get("name"),s.encode(sh.get("cells")));}
   for(Object entry:(List<?>)w.get("robots")){var item=s.map(entry);String sn=s.required(item,"sn");if(!sn.matches("LBR-[0-9]{2}(0[1-9]|1[0-2])-[SPMR]-[0-9]{4}")||sn.endsWith("0000"))throw new IllegalArgumentException("Invalid imported SN");String month=sn.substring(4,8),type=sn.substring(9,10);int seq=Integer.parseInt(sn.substring(11));
     var f=s.validateFields(s.map(item.get("fields")));s.db.update("insert into sn_registry(sn,status,actor) values (?,'USED','IMPORT')",sn);s.db.update("insert into sn_counter(month,type,last_seq) values (?,?,?) on conflict(month,type) do update set last_seq=greatest(sn_counter.last_seq,excluded.last_seq)",month,type,seq);
     s.db.update("insert into robot(sn,fields,source) values (?,?::jsonb,?)",sn,s.encode(f),w.get("filename")+" / 机器人信息管理 / 第 "+item.get("row")+" 行");
     for(Object module:(List<?>)item.get("modules")){var m=s.map(module);s.db.update("insert into module_installation(robot_sn,slot,module_sn,version) values (?,?,?,null)",sn,m.get("slot"),m.get("sn"));s.db.update("insert into module_history(robot_sn,slot,action,new_sn,actor,reason) values (?,?,'IMPORT',?,'IMPORT','从 Excel 建立当前装配关系，实际安装时间未提供')",sn,m.get("slot"),m.get("sn"));}
     for(var pair:List.of(new String[]{"装配","assemblyPerson","assemblyDate"},new String[]{"SN烧录","snEntryPerson","snEntryDate"},new String[]{"调试","debugPerson","debugDate"},new String[]{"质检","qcPerson","qcDate"},new String[]{"交付","","deliveryDate"})){Object date=f.get(pair[2]);if(date==null)continue;s.db.update("insert into process_record(robot_sn,type,result,document_no,note,operator_name,actor,business_date,snapshot) values (?,?,?,?,?,?,'IMPORT',?,?::jsonb)",sn,pair[0],pair[0].equals("调试")?f.get("debugResult"):null,pair[0].equals("质检")?f.get("qcReport"):null,"Excel 初始台账导入；仅保留原表已提供事实",Objects.toString(f.get(pair[1]),"原表未填写"),LocalDate.parse(date.toString()),s.encode(s.snapshot(sn)));}
     if(f.get("status")!=null)s.db.update("insert into lifecycle_history(robot_sn,new_status,actor,reason) values (?,?,'IMPORT','Excel 原始状态导入')",sn,f.get("status"));s.audit("robot",sn,"IMPORT","*",null,s.snapshot(sn),"导入 Excel 第 "+item.get("row")+" 行");
   }
   s.audit("import",Long.toString(id),"IMPORT","workbook",null,w.get("report"),"按需求确认映射原始 Excel 三个工作表；空白保留");
 }
}
