"""v2.2 strict regression; only runs via acceptance.py's isolated-port/database guard."""
import concurrent.futures, json, secrets, unittest
from acceptance import Client, client

class V22Acceptance(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.admin=client('ADMIN'); cls.tech=client('TECHNICIAN')
 def create(self):
  return self.admin.call('/robots','POST',{'month':'3312','type':'S','reason':'v2.2 synthetic','fields':{'model':'SYNTHETIC'}})['sn']
 def row(self,sn):return self.admin.call('/robots/'+sn)
 def event(self,sn,kind,values,expect=200,actor=None):
  return (actor or self.admin).call('/robots/'+sn+'/'+kind,'POST',{'revision':self.row(sn)['revision'],'reason':'v2.2 synthetic',**values},expect)
 def test_01_strict_integer_configuration(self):
  original=self.admin.call('/options')['rule']
  for value in ['not-a-number','9999',None,True,1.5,9999.0,-1,0,10000,10**30]:
   with self.subTest(value=value):
    error=self.admin.call('/settings/sn_rule','PUT',{'value':{**original,'maxSequence':value},'reason':'invalid synthetic'},400)
    self.assertIn('maxSequence',error['message']);self.assertEqual(original,self.admin.call('/options')['rule'])
  rule={k:v for k,v in original.items() if k!='maxSequence'}
  self.admin.call('/settings/sn_rule','PUT',{'value':rule,'reason':'missing synthetic'},400)
  self.assertEqual(original,self.admin.call('/options')['rule'])
 def test_02_create_cannot_forge_process(self):
  before=self.admin.call('/sn')
  fields=['qcResult','qcPerson','qcDate','qcReport','debugResult','debugPerson','debugDate','deliveryDate']
  for field in fields:
   self.admin.call('/robots','POST',{'month':'3311','type':'P','reason':'forged synthetic','fields':{field:'通过'}},400)
  self.assertEqual(before,self.admin.call('/sn'))
  sn=self.create();self.assertEqual('在库',self.row(sn)['fields']['status'])
  self.event(sn,'processes',{'type':'交付','date':'2020-02-29'},409)
  self.assertEqual([],self.row(sn)['processes'])
 def test_03_dates_and_process_ownership(self):
  sn=self.create();before=self.row(sn)
  for value in [None,'','2026-02-30','2026-1-01','bad',123]:
   self.event(sn,'processes',{'type':'质检','result':'通过','date':value},400)
   self.assertEqual(before,self.row(sn))
  self.event(sn,'processes',{'type':'质检','result':'通过'},400)
  self.event(sn,'processes',{'type':'质检','result':'通过','date':'2020-02-29','operator':'Synthetic inspector'})
  self.assertEqual('2020-02-29',self.row(sn)['fields']['qcDate'])
  for key in ['qcResult','qcPerson','qcDate','qcReport','debugResult','deliveryDate']:
   self.admin.call('/robots/'+sn,'PATCH',{'revision':self.row(sn)['revision'],'reason':'forged synthetic','fields':{key:'overwrite'}},400)
  self.event(sn,'processes',{'type':'交付','date':'2020-03-01'})
  self.assertEqual('已交付',self.row(sn)['fields']['status'])
 def test_04_concurrent_module_installation(self):
  robots=[self.create(),self.create()];module='SYNTHETIC-CONCURRENT-'+secrets.token_hex(8)
  def install(sn):
   c=client('ADMIN');return c.raw('/api/robots/'+sn+'/modules','POST',json.dumps({'revision':self.row(sn)['revision'],'reason':'Concurrent synthetic','slot':'头部舵机','action':'INSTALL','moduleSn':module,'version':'V22'}).encode(),{'Content-Type':'application/json','X-CSRF-TOKEN':c.csrf})[0]
  with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:codes=list(pool.map(install,robots))
  self.assertEqual([200,409],sorted(codes))
  self.assertEqual(1,sum(len(self.row(sn)['modules']) for sn in robots))
  self.assertEqual(1,sum(len(self.row(sn)['moduleHistory']) for sn in robots))
 def test_05_scoped_technician_queries_exports_aliases(self):
  own,other=self.create(),self.create();name='scoped_'+secrets.token_hex(5);password=secrets.token_urlsafe(20)
  self.admin.call('/users','POST',{'username':name,'role':'TECHNICIAN','scopeSns':[own],'password':password,'reason':'Scoped synthetic'})
  c=Client(name,password);self.assertEqual([own],[r['sn'] for r in c.call('/robots')])
  self.assertEqual([],c.call('/robots?q='+other));self.assertNotIn(other,c.call('/export/robots').decode())
  c.call('/robots/'+other,expect=403);c.call('/export/users',expect=403)
  new=self.admin.call('/sn/generate','POST',{'month':'3312','type':'R','count':1,'reason':'Alias synthetic'})['sns'][0]
  self.event(own,'migrate',{'newSn':new,'kind':'维修替换','evidence':'synthetic only'})
  self.assertEqual([new],[r['sn'] for r in c.call('/robots')]);self.assertEqual(new,c.call('/robots/'+own)['sn'])
  self.assertNotIn(other,json.dumps(c.call('/export/details')))
 def test_06_csrf_and_stale_revision(self):
  sn=self.create();revision=self.row(sn)['revision']
  body={'fields':{'nickname':'one'},'revision':revision,'reason':'Revision synthetic'}
  code,_=self.admin.raw('/api/robots/'+sn,'PATCH',json.dumps(body).encode(),{'Content-Type':'application/json'})
  self.assertEqual(403,code)
  self.admin.call('/robots/'+sn,'PATCH',body)
  self.admin.call('/robots/'+sn,'PATCH',{**body,'fields':{'nickname':'two'}},409)
  self.assertEqual('one',self.row(sn)['fields']['nickname'])
 def test_07_process_column_cannot_make_creation_impossible(self):
  column=next(c for c in self.admin.call('/fields/all') if c['key']=='qcResult')
  self.admin.call('/fields/qcResult','PATCH',{'revision':column['revision'],'required':True,'reason':'Invalid process required'},400)
  after=next(c for c in self.admin.call('/fields/all') if c['key']=='qcResult')
  self.assertEqual(column,after)

if __name__=='__main__':unittest.main(verbosity=2)
