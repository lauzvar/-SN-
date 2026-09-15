"""End-to-end checks. Mutates ONLY an explicitly acknowledged isolated test server."""
import concurrent.futures, http.cookiejar, json, os, re, sys, unittest
import urllib.request, urllib.error, urllib.parse
from pathlib import Path

BASE=os.environ.get('VALENBOT_TEST_URL','http://127.0.0.1:8083')
if os.environ.get('VALENBOT_TEST_ACK')!='robot_sn_v2_test' or urllib.parse.urlparse(BASE).port!=8083:
    raise SystemExit('Refusing mutations: set VALENBOT_TEST_ACK=robot_sn_v2_test and use isolated port 8083.')
PRIVATE=Path(os.environ['VALENBOT_TEST_PRIVATE'])
ACCOUNTS=json.loads((PRIVATE/'trial-accounts.json').read_text(encoding='utf-8-sig'))
BOOT=json.loads((PRIVATE/'bootstrap.json').read_text(encoding='utf-8-sig'))

class Client:
    def __init__(self,username=None,password=None):
        self.open=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=None
        if username:
            page=self.raw('/login')[1].decode()
            token=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page).group(1)
            payload=urllib.parse.urlencode({'username':username,'password':password,'_csrf':token}).encode()
            status,body=self.raw('/login','POST',payload,{'Content-Type':'application/x-www-form-urlencoded'})
            match=re.search(r'name="csrf-token" content="([^"]+)"',body.decode())
            if not match: raise AssertionError('Login failed: '+username)
            self.csrf=match.group(1)
    def raw(self,path,method='GET',body=None,headers=None):
        req=urllib.request.Request(BASE+path,data=body,method=method,headers=headers or {})
        try:
            with self.open.open(req,timeout=30) as res:return res.status,res.read()
        except urllib.error.HTTPError as e:return e.code,e.read()
    def call(self,path,method='GET',body=None,expect=200):
        headers={'Content-Type':'application/json'}
        if self.csrf:headers['X-CSRF-TOKEN']=self.csrf
        code,raw=self.raw('/api'+path,method,json.dumps(body).encode() if body is not None else None,headers)
        assert code==expect,(method,path,code,raw[:600])
        try:return json.loads(raw)
        except:return raw

def client(role):
    u=next(x for x in ACCOUNTS if x['role']==role)
    return Client(u['username'],u['password'])

class Acceptance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.a,cls.t,cls.u=client('ADMIN'),client('TECHNICIAN'),client('USER')
    def get(self,sn):return self.a.call('/robots/'+sn)
    def patch(self,c,sn,fields,expect=200,**kw):return c.call('/robots/'+sn,'PATCH',{'revision':self.get(sn)['revision'],'fields':fields,'reason':'独立数据库验收',**kw},expect)
    def new(self,month='3101',type='S'):
        return self.a.call('/robots','POST',{'month':month,'type':type,'fields':{'model':'Acceptance Robot'},'reason':'独立测试主档'})['sn']
    def mutate(self,sn,route,data,client=None,expect=200):
        return (client or self.t).call('/robots/'+sn+'/'+route,'POST',{'revision':self.get(sn)['revision'],'reason':'独立验收',**data},expect)
    def test_01_import_fidelity(self):
        rows=self.a.call('/robots');self.assertEqual(len(rows),10)
        for source in BOOT['workbook']['robots']:
            robot=self.get(source['sn'])
            for key,value in source['fields'].items():self.assertEqual(robot['fields'][key],value,(source['sn'],key))
            self.assertEqual(sorted((m['slot'],m['module_sn']) for m in robot['modules']),sorted((m['slot'],m['sn']) for m in source['modules']))
        sources=self.a.call('/sources');self.assertEqual(len(sources),1)
        self.assertEqual(sources[0]['sha256'],BOOT['workbook']['sha256'])
        for orig,stored in zip(BOOT['workbook']['sheets'],sources[0]['sheets']):self.assertEqual(orig,stored)
        self.assertEqual(self.get('LBR-2609-P-0001')['fields']['deliveryDate'],'2026-09-15')
        self.assertIsNone(self.get('LBR-2609-P-0001')['fields']['activationDate'])
        self.assertIsNone(self.get('LBR-2609-P-0001')['fields']['qcResult'])
        sn=self.a.call('/sn/generate','POST',{'month':'2609','type':'P','count':1,'reason':'校验导入续号'})['sns'][0]
        self.assertEqual(sn,'LBR-2609-P-0011')
    def test_02_permissions(self):
        sn='LBR-2609-P-0001';r=self.u.call('/robots/'+sn)
        self.assertNotIn('modules',r);self.assertNotIn('processes',r);self.assertNotIn('source',r)
        self.assertNotIn('osVersion',r['fields']);self.assertNotIn('qcPerson',r['fields'])
        csv=self.u.call('/export/robots').decode('utf-8-sig');self.assertNotIn('OS1.2.0',csv);self.assertNotIn('主控系统版本',csv);self.assertIn('产品型号',csv)
        for route in ['/export/details','/export/sources','/export/audit','/users','/sources','/audit','/tables','/sn','/logins']:self.u.call(route,expect=403)
        self.patch(self.u,sn,{'nickname':'普通用户试改'})
        for key in ['model','osVersion','status','sn','qcResult']:self.patch(self.u,sn,{key:'invalid'},expect=403)
        self.u.call('/sn/generate','POST',{'month':'2609','type':'P','count':1,'reason':'越权'},403)
        self.patch(self.t,sn,{'firmwareVersion':'FW-test'})
        self.t.call('/robots','POST',{'month':'2609','type':'P','reason':'越权'},403)
        for route in ['/tables','/fields','/users','/sn/ranges']:self.t.call(route,'POST',{'reason':'越权'},403)
        self.t.call('/settings/sn_rule','PUT',{},403)
        self.t.call('/fields/custom_forbidden','DELETE',{'reason':'越权'},403)
        self.t.call('/robots/'+sn,'DELETE',{'revision':self.get(sn)['revision'],'reason':'越权'},403)
        self.patch(self.t,sn,{'sn':'LBR-2609-P-9999'},expect=400)
        self.patch(self.t,sn,{'wifiMac':'bad'},expect=400)
        self.patch(self.t,sn,{'qcResult':'通过'},expect=400)
        self.patch(self.t,sn,{'debugResult':'通过'},expect=400)
        raw=self.u.raw('/api/robots/'+sn,'PATCH',json.dumps({'fields':{'nickname':'CSRF'}}).encode(),{'Content-Type':'application/json'})
        self.assertEqual(raw[0],403)
        self.assertEqual(Client().raw('/api/robots')[0],401)
        audit=self.a.call('/audit');self.assertTrue(any(x['actor']=='user_trial' and x['field_name']=='nickname' for x in audit))
        self.patch(self.a,sn,{'nickname':'=1+1'});self.assertIn("'=1+1",self.u.call('/export/robots').decode())
    def test_03_numbers_concurrency_and_limits(self):
        self.a.call('/sn/generate','POST',{'month':'2613','type':'S','count':1,'reason':'非法月份'},400)
        self.a.call('/sn/generate','POST',{'month':'3102','type':'X','count':1,'reason':'非法类型'},400)
        def allocate(_):return client('ADMIN').call('/sn/generate','POST',{'month':'3102','type':'S','count':5,'reason':'并发验收'})['sns']
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:nums=[x for group in pool.map(allocate,range(8)) for x in group]
        self.assertEqual(len(set(nums)),40);self.assertEqual(sorted(int(n[-4:]) for n in nums),list(range(1,41)))
        self.a.call('/sn/'+nums[0]+'/void','POST',{'reason':'验收作废'})
        self.a.call('/robots','POST',{'sn':nums[0],'fields':{},'reason':'重用作废编号'},409)
        counter=self.a.call('/sn/generate','POST',{'month':'3103','type':'S','count':1,'reason':'换月'})['sns'][0];self.assertTrue(counter.endswith('0001'))
        self.a.call('/settings/sn_rule','PUT',{'value':{'prefix':'LBR','types':['S','P','M','R'],'maxSequence':40},'reason':'验收上限'})
        self.a.call('/sn/generate','POST',{'month':'3102','type':'S','count':1,'reason':'超限'},409)
        self.a.call('/settings/sn_rule','PUT',{'value':{'prefix':'LBR','types':['S','P','M','R'],'maxSequence':9999},'reason':'恢复上限'})
        made=self.a.call('/sn/ranges','POST',{'month':'3104','type':'P','count':2,'assignee':'tech_trial','reason':'分配试验'})['sns'];self.assertEqual(len(made),2)
    def test_04_lifecycle_repair_module_migration(self):
        sn=self.new();self.__class__.workflow_sn=sn
        self.mutate(sn,'processes',{'type':'出库'},expect=409)
        self.mutate(sn,'processes',{'type':'质检','result':'不通过','documentNo':'FAIL-001'})
        self.mutate(sn,'processes',{'type':'交付'},expect=409)
        self.mutate(sn,'processes',{'type':'质检','result':'通过','documentNo':'PASS-001'})
        self.mutate(sn,'processes',{'type':'交付','date':'2031-01-02'})
        self.assertEqual(self.get(sn)['fields']['status'],'已交付')
        self.mutate(sn,'modules',{'slot':'头部舵机','action':'INSTALL','moduleSn':'TEST-HEAD-A','version':'H1'})
        self.mutate(sn,'modules',{'slot':'头部舵机','action':'INSTALL','moduleSn':'TEST-HEAD-B'},expect=409)
        other=self.new()
        self.mutate(other,'modules',{'slot':'头部舵机','action':'INSTALL','moduleSn':'TEST-HEAD-A'},expect=409)
        rid=self.mutate(sn,'repairs',{'reason':'电机维修'})['id']
        installed=next(x for x in self.get(sn)['modules'] if not x['removed_at'])
        self.mutate(sn,'modules',{'slot':'头部舵机','action':'REPLACE','installationId':installed['id'],'moduleSn':'TEST-HEAD-B','version':'H2'})
        self.assertEqual(self.get(sn)['repairs'][0]['snapshot']['modules'][0]['module_sn'],'TEST-HEAD-A')
        self.t.call('/repairs/'+str(rid)+'/handle','POST',{'status':'已完成','reason':'完成','result':'更换头部后复测通过'})
        self.assertEqual(self.get(sn)['fields']['status'],'在库')
        self.t.call('/repairs/'+str(rid)+'/handle','POST',{'status':'处理中','reason':'覆盖'},409)
        flash=self.mutate(sn,'flash',{'deviceId':'fixture-001'})['id']
        out=self.t.call('/flash/'+str(flash)+'/readback','POST',{'actualSn':'WRONG','evidence':'工装日志 A'});self.assertFalse(out['matched'])
        out=self.t.call('/flash/'+str(flash)+'/readback','POST',{'actualSn':sn,'evidence':'工装日志 B'});self.assertTrue(out['matched'])
        new=self.t.call('/sn/generate','POST',{'month':'3101','type':'R','count':1,'reason':'维修替换'})['sns'][0]
        self.mutate(sn,'migrate',{'newSn':new,'kind':'维修替换','evidence':'批准单 REPAIR-1'})
        after=self.get(sn);self.assertEqual(after['sn'],new);self.assertEqual(len(after['moduleHistory']),2);self.assertEqual(len(after['repairs']),1);self.assertEqual(len(after['snHistory']),1)
        self.t.call('/flash/'+str(flash)+'/readback','POST',{'actualSn':sn,'evidence':'旧值'},409)
        self.a.call('/robots','POST',{'sn':sn,'fields':{},'reason':'复用旧码'},409)
        self.assertEqual(self.u.call('/robots/'+sn)['sn'],new)
        before=self.get(new);self.patch(self.t,new,{'nickname':'revision1'})
        self.t.call('/robots/'+new,'PATCH',{'revision':before['revision'],'fields':{'nickname':'stale'},'reason':'并发更新'},409)
        self.mutate(new,'status',{'status':'已报废'})
        self.mutate(new,'status',{'status':'在库'},expect=409)
    def test_05_users_scopes_immediate_revocation(self):
        sn='LBR-2609-P-0001';pwd='TestOnly-UiProbe-2026!'
        self.a.call('/users','POST',{'username':'scope_probe','password':pwd,'role':'USER','scopeSns':[sn],'reason':'范围验收'})
        c=Client('scope_probe',pwd);self.assertEqual(len(c.call('/robots')),1)
        c.call('/robots/LBR-2609-P-0002',expect=403);c.call('/robots/LBR-2609-P-0002/label.png',expect=403)
        self.assertNotIn('LBR-2609-P-0002',c.call('/export/robots').decode())
        self.a.call('/users/scope_probe','PATCH',{'role':'TECHNICIAN','active':True,'scopeSns':[sn],'snPermission':False,'reason':'授权变更'})
        self.assertEqual(c.call('/me')['role'],'TECHNICIAN');c.call('/sn/generate','POST',{'month':'3105','type':'S','count':1,'reason':'无 SN 权限'},403)
        c.call('/sources',expect=403);c.call('/export/sources',expect=403)
        self.a.call('/users/scope_probe','PATCH',{'role':'TECHNICIAN','active':False,'reason':'禁用'})
        c.call('/me',expect=401)
        self.a.call('/users/admin_trial','PATCH',{'role':'USER','active':True,'reason':'降级自己'},409)
        self.a.call('/users','POST',{'username':'ui_probe','password':pwd,'role':'ADMIN','reason':'独立数据库浏览器验收'})
    def test_06_dynamic_tables_fields(self):
        tid=self.a.call('/tables','POST',{'name':'独立验收表','reason':'结构测试'})['id']
        fid=self.a.call(f'/tables/{tid}/fields','POST',{'key':'report','label':'报告编号','reason':'添加字段'})['id']
        self.t.call(f'/tables/{tid}/fields','POST',{'key':'unauthorized','label':'越权','reason':'测试'},403)
        rid=self.a.call(f'/tables/{tid}/records','POST',{'data':{'report':'R-001'},'reason':'新增记录'})['id']
        self.t.call(f'/tables/{tid}/records/{rid}','PATCH',{'revision':0,'data':{'report':'R-002'},'reason':'技术编辑'})
        self.assertEqual(self.t.call(f'/tables/{tid}')['records'][0]['data']['report'],'R-002')
        self.t.call(f'/tables/{tid}/fields/{fid}','DELETE',{'reason':'越权删除'},403)
        self.t.call(f'/tables/{tid}','DELETE',{'reason':'越权删除'},403)
        self.a.call(f'/tables/{tid}/fields/{fid}','DELETE',{'reason':'归档字段'})
        self.assertEqual(self.a.call(f'/tables/{tid}')['records'][0]['data'],{})
        self.a.call(f'/tables/{tid}','DELETE',{'reason':'归档表'})
        self.a.call(f'/tables/{tid}',expect=404)
        self.a.call('/fields','POST',{'key':'custom_secret','label':'内部事项','group':'BLUE','dataType':'text','reason':'测试自定义权限'})
        self.patch(self.t,'LBR-2609-P-0001',{'custom_secret':'INTERNAL-ONLY'})
        self.assertNotIn('custom_secret',self.u.call('/robots/LBR-2609-P-0001')['fields'])
        self.assertNotIn('INTERNAL-ONLY',self.u.call('/export/robots').decode())
        self.a.call('/fields/custom_secret','DELETE',{'reason':'删除测试字段'})
        self.assertNotIn('custom_secret',self.a.call('/robots/LBR-2609-P-0001')['fields'])
    def test_07_label(self):
        sn='LBR-2609-P-0001';image=self.u.call('/robots/'+sn+'/label.png')
        self.assertTrue(image.startswith(b'\x89PNG'));(PRIVATE/'label-acceptance.png').write_bytes(image)
        info=self.u.call('/robots/'+sn+'/label-info');self.assertIn('/r/'+sn,info['url'])
        self.assertIn('Valenbot',info['title'])

if __name__=='__main__':unittest.main(verbosity=2)
