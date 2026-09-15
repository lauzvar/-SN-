"""2.1 isolated HTTP acceptance. Reuses HTTP client, not v2.0 permission expectations."""
import csv,io,json,time,unittest,concurrent.futures
from pathlib import Path
from acceptance import Client,client,PRIVATE,BOOT
PASSWORD='Only-LocalV21-Check!'
class UpgradeAcceptance(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.a,cls.t=client('ADMIN'),client('TECHNICIAN');cls.prefix='v21_'+str(int(time.time()))
  cls.sn1='LBR-2609-P-0001';cls.sn2='LBR-2609-P-0002'
  cls.id1=cls.a.call('/robots/'+cls.sn1)['robotId'];cls.id2=cls.a.call('/robots/'+cls.sn2)['robotId']
 def account(self,suffix,role='USER',robot=None,name='同名客户'):
  username=self.prefix+'_'+suffix;b={'username':username,'role':role,'password':PASSWORD,'reason':'独立测试库 2.1 验收'}
  if robot:b.update(boundRobotId=robot,customerName=name)
  self.a.call('/users','POST',b);return username,Client(username,PASSWORD)
 def row(self,sn):return self.a.call('/robots/'+sn)
 def edit(self,c,sn,fields,expect=200):return c.call('/robots/'+sn,'PATCH',{'revision':self.row(sn)['revision'],'fields':fields,'reason':'2.1 验收'},expect)
 def event(self,sn,kind,b,expect=200):return self.t.call('/robots/'+sn+'/'+kind,'POST',{'revision':self.row(sn)['revision'],'reason':'2.1 验收',**b},expect)
 def newRobot(self):return self.t.call('/robots','POST',{'month':'3201','type':'S','fields':{'model':'Independent Test'},'reason':'技术建档验收'})['sn']
 def column(self,key):return next(d for d in self.a.call('/fields/all') if d['key']==key)
 def test_01_upgrade_preserves_workbook(self):
  for source in BOOT['workbook']['robots']:
   r=self.row(source['sn']);self.assertTrue(r['robotId'])
   for k,v in source['fields'].items():self.assertEqual(r['fields'][k],v)
   self.assertEqual(len(r['modules']),len(source['modules']))
  self.assertEqual(len(self.a.call('/sources')[0]['sheets']),3)
  self.assertEqual(sum(d['storage_kind']=='MODULE' for d in self.a.call('/fields/all')),13)
  self.assertEqual(client('USER').call('/robots'),[])
 def test_02_same_name_binding_isolation(self):
  ua,a=self.account('alice',robot=self.id1);ub,b=self.account('bob',robot=self.id2);un,n=self.account('none')
  self.__class__.ua,self.__class__.alice=ua,a;self.__class__.ub,self.__class__.bob=ub,b
  for c,own,other in [(a,self.sn1,self.sn2),(b,self.sn2,self.sn1)]:
   self.assertEqual([r['sn'] for r in c.call('/robots')],[own]);self.assertEqual(c.call('/robots?q='+other),[])
   for tail in ['', '/label.png','/qr.png','/label-info']:c.call('/robots/'+other+tail,expect=403)
   self.assertEqual(c.call('/robots/'+own)['fields']['customer'],'同名客户')
   self.assertNotIn(other,c.call('/export/robots').decode())
   c.call('/export/details',expect=403);c.call('/sources',expect=403)
  self.assertEqual(n.call('/robots'),[]);n.call('/robots/'+self.sn1,expect=403)
  self.assertEqual(len(list(csv.reader(io.StringIO(n.call('/export/robots').decode('utf-8-sig'))))),1)
  self.a.call('/users/'+ua,'PATCH',{'boundRobotId':self.id1,'customerName':'改名后的客户','reason':'验证改名不影响绑定'})
  self.assertEqual(a.call('/robots')[0]['sn'],self.sn1);self.assertEqual(b.call('/robots')[0]['sn'],self.sn2)
  self.a.call('/users/'+ua,'PATCH',{'customerName':'仅修改名称','reason':'局部名称保存'})
  self.assertEqual(next(x for x in client('ADMIN').call('/users') if x['username']==ua)['customer_name'],'仅修改名称')
  self.assertEqual(a.call('/robots')[0]['robotId'],self.id1)
  self.a.call('/users/'+un,'PATCH',{'customerName':'不能仅凭名称绑定','reason':'局部保存'},400)
  self.a.call('/users/'+ua,'PATCH',{'boundRobotId':self.id2,'customerName':'冲突客户','reason':'不可抢占'},409)
  (PRIVATE/'upgrade-2.1.0/browser-accounts.json').write_text(json.dumps({'alice':ua,'bob':ub,'unbound':un,'password':PASSWORD}),encoding='utf-8')
 def test_03_nickname_and_rebind_immediate(self):
  self.edit(self.alice,self.sn1,{'nickname':'仅昵称可改'})
  for field in ['customer','hardwareVersion','boundRobotId','sn','user_id']:self.edit(self.alice,self.sn1,{field:'unauthorized'},403)
  self.alice.call('/users/'+self.ua,'PATCH',{'role':'ADMIN','reason':'越权'},403)
  self.a.call('/users/'+self.ua,'PATCH',{'boundRobotId':'','reason':'解除绑定'})
  self.assertEqual(self.alice.call('/robots'),[]);self.alice.call('/robots/'+self.sn1,expect=403)
  self.a.call('/users/'+self.ub,'PATCH',{'boundRobotId':'','reason':'解除第二台'})
  self.a.call('/users/'+self.ua,'PATCH',{'boundRobotId':self.id2,'customerName':'同名客户','reason':'重新绑定'})
  self.assertEqual(self.alice.call('/robots')[0]['sn'],self.sn2);self.alice.call('/robots/'+self.sn1+'/qr.png',expect=403)
  self.a.call('/users/'+self.ua,'PATCH',{'boundRobotId':'','reason':'完成隔离验证'})
  self.a.call('/users/'+self.ua,'PATCH',{'boundRobotId':self.id1,'customerName':'同名客户','reason':'还原测试用户关系'})
  self.a.call('/users/'+self.ub,'PATCH',{'boundRobotId':self.id2,'customerName':'同名客户','reason':'还原第二用户关系'})
  history=self.a.call('/users/binding-history');self.assertTrue(any(x['username']==self.ua and x['old_robot_id']==self.id1 and x['new_robot_id'] is None for x in history))
 def test_04_main_columns_visibility_required_archive(self):
  key='custom_'+self.prefix
  self.a.call('/fields','POST',{'key':key,'label':'内部检验温度','dataType':'number','ordinal':2,'required':True,'reason':'新增主档列'})
  d=self.column(key);self.assertFalse(d['customer_visible']);self.assertTrue(d['required'])
  self.assertNotIn(key,self.alice.call('/robots/'+self.sn1)['fields'])
  self.edit(self.t,self.sn1,{key:0});self.assertEqual(self.row(self.sn1)['fields'][key],0)
  self.edit(self.t,self.sn1,{key:''},400)
  self.t.call('/robots','POST',{'month':'3201','type':'S','fields':{},'reason':'必填检验'},400)
  self.edit(self.bob,self.sn2,{'nickname':'历史缺失不强制补造'})
  self.a.call('/fields/'+key,'PATCH',{'revision':d['revision'],'customerVisible':True,'label':'客户可见温度','reason':'明确公开字段'})
  self.assertEqual(self.alice.call('/robots/'+self.sn1)['fields'][key],0)
  self.edit(self.alice,self.sn1,{key:99},403)
  self.assertIn('客户可见温度',self.alice.call('/export/robots').decode())
  d=self.column(key);self.a.call('/fields/'+key,'DELETE',{'revision':d['revision'],'reason':'归档显示列'})
  self.assertNotIn(key,self.row(self.sn1)['fields']);self.assertNotIn(key,[x['key'] for x in self.a.call('/fields')]);self.assertNotIn('客户可见温度',self.a.call('/export/robots').decode())
  self.edit(self.t,self.sn1,{key:10},400)
  d=self.column(key);self.a.call('/fields/'+key+'/restore','POST',{'revision':d['revision'],'reason':'恢复验证原值'})
  self.assertEqual(self.row(self.sn1)['fields'][key],0)
  d=self.column(key);self.a.call('/fields/'+key,'PATCH',{'revision':d['revision'],'required':False,'reason':'后续验收允许空值'})
  for core in ['sn','customer','nickname']:
   d=self.column(core);self.a.call('/fields/'+core,'DELETE',{'revision':d['revision'],'reason':'验证展示和底层分离'})
   self.assertEqual(self.alice.call('/robots')[0]['robotId'],self.id1)
   self.assertNotIn(core,self.alice.call('/robots/'+self.sn1)['fields'])
   d=self.column(core);self.a.call('/fields/'+core+'/restore','POST',{'revision':d['revision'],'reason':'恢复展示'})
  self.edit(self.alice,self.sn1,{'nickname':'恢复后仍仅昵称可改'})
  model=self.column('model');self.a.call('/fields/model','PATCH',{'revision':model['revision'],'customerVisible':False,'reason':'验证 QR 标签字段过滤'})
  self.assertNotIn('model',self.alice.call('/robots/'+self.sn1)['fields'])
  model=self.column('model');self.a.call('/fields/model','PATCH',{'revision':model['revision'],'customerVisible':True,'reason':'恢复客户可见'})
  for c in [self.t,self.alice]:
   c.call('/fields','POST',{'key':'custom_attack','label':'越权','reason':'越权'},403)
   c.call('/fields/model','PATCH',{'revision':0,'label':'越权','reason':'越权'},403)
   c.call('/fields/model','DELETE',{'revision':0,'reason':'越权'},403)
   c.call('/fields/all',expect=403)
 def test_05_admin_user_management_and_sessions(self):
  name,c=self.account('management')
  self.a.call('/users/'+name,'PATCH',{'active':False,'reason':'局部停用修复'})
  c.call('/me',expect=401)
  self.a.call('/users/'+name,'PATCH',{'active':True,'reason':'启用'})
  c=Client(name,PASSWORD);self.assertEqual(c.call('/me')['role'],'USER')
  self.a.call('/users/'+name,'PATCH',{'role':'TECHNICIAN','reason':'角色修改'})
  c.call('/me',expect=401);c=Client(name,PASSWORD);self.assertEqual(c.call('/me')['role'],'TECHNICIAN')
  new='Reset-Only-V21-2026!';self.a.call('/users/'+name,'PATCH',{'password':new,'reason':'单项重置密码'})
  c.call('/me',expect=401)
  with self.assertRaises(AssertionError):Client(name,PASSWORD)
  c=Client(name,new);self.assertEqual(c.call('/me')['role'],'TECHNICIAN')
  users=self.a.call('/users');saved=next(x for x in users if x['username']==name);self.assertTrue(saved['active']);self.assertEqual(saved['role'],'TECHNICIAN')
  audit=json.dumps(self.a.call('/audit'));self.assertNotIn(PASSWORD,audit);self.assertNotIn(new,audit)
  self.assertNotIn('password_hash',json.dumps(users))
  for actor in [self.t,self.alice]:
   actor.call('/users',expect=403);actor.call('/users/binding-history',expect=403)
   actor.call('/users','POST',{'username':'attack','role':'ADMIN','password':PASSWORD,'reason':'攻击'},403)
   actor.call('/users/'+actor.call('/me')['username'],'PATCH',{'role':'ADMIN','reason':'自我提权'},403)
  self.a.call('/users/admin_trial','PATCH',{'active':False,'reason':'保护当前管理员'},409)
  self.a.call('/users/admin_trial','PATCH',{'role':'USER','reason':'保护管理入口'},409)
 def test_06_technician_creates_business_records_no_deletes(self):
  tid=self.a.call('/tables','POST',{'name':self.prefix,'reason':'业务表验收'})['id']
  self.a.call(f'/tables/{tid}/fields','POST',{'key':'note','label':'服务备注','reason':'字段'})
  rid=self.t.call(f'/tables/{tid}/records','POST',{'data':{'note':'技术人员新增'},'reason':'新增'})['id']
  self.t.call(f'/tables/{tid}/records/{rid}','PATCH',{'revision':0,'data':{'note':'技术人员修改'},'reason':'修改'})
  self.assertEqual(self.t.call(f'/tables/{tid}')['records'][0]['data']['note'],'技术人员修改')
  self.t.call(f'/tables/{tid}/records/{rid}','DELETE',{'revision':1,'reason':'越权删除'},403)
  self.t.call(f'/tables/{tid}/fields','POST',{'key':'attack','label':'攻击','reason':'越权'},403)
  self.t.call(f'/tables/{tid}','DELETE',{'reason':'越权'},403)
  sn=self.newRobot();self.edit(self.t,sn,{'nickname':'技术建档'})
  self.t.call('/robots/'+sn,'DELETE',{'revision':self.row(sn)['revision'],'reason':'越权删除'},403)
 def test_07_regression_migration_qr_modules_repair(self):
  sn=self.newRobot();identity=self.row(sn)['robotId'];u,c=self.account('migration',robot=identity)
  self.event(sn,'modules',{'action':'INSTALL','slot':'头部舵机','moduleSn':self.prefix+'-H1','version':'H1'})
  rid=self.event(sn,'repairs',{'reason':'测试故障'})['id']
  installed=self.row(sn)['modules'][0]
  self.event(sn,'modules',{'action':'REPLACE','slot':'头部舵机','installationId':installed['id'],'moduleSn':self.prefix+'-H2','version':'H2'})
  self.assertEqual(self.row(sn)['repairs'][0]['snapshot']['modules'][0]['module_sn'],self.prefix+'-H1')
  self.t.call('/repairs/'+str(rid)+'/handle','POST',{'status':'已完成','result':'更换并复测','reason':'验收完成'})
  new=self.t.call('/sn/generate','POST',{'month':'3201','type':'R','count':1,'reason':'替换编号'})['sns'][0]
  self.event(sn,'migrate',{'newSn':new,'kind':'维修替换','evidence':'审批凭证'})
  self.assertEqual(c.call('/robots/'+sn)['sn'],new);self.assertEqual(c.call('/robots')[0]['robotId'],identity)
  self.assertEqual(len(self.row(new)['moduleHistory']),2);self.assertEqual(len(self.row(new)['repairs']),1)
  self.assertTrue(c.call('/robots/'+sn+'/label.png').startswith(b'\x89PNG'))
  self.a.call('/robots','POST',{'sn':sn,'fields':{},'reason':'旧号不可复用'},409)
  def allocate(_):return client('ADMIN').call('/sn/generate','POST',{'month':'3202','type':'S','count':3,'reason':'并发验收'})['sns']
  with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:numbers=[n for group in pool.map(allocate,range(6)) for n in group]
  self.assertEqual(len(numbers),len(set(numbers)))
  self.a.call('/sn/'+numbers[0]+'/void','POST',{'reason':'作废验收'})
  self.a.call('/robots','POST',{'sn':numbers[0],'fields':{},'reason':'作废号重用'},409)
if __name__=='__main__':unittest.main(verbosity=2)
