"""Synthetic Excel-import regression. Uses only acceptance.py's guarded test instance."""
import concurrent.futures, io, json, secrets, unittest, zipfile
import xml.etree.ElementTree as ET
from acceptance import Client, client

NS='http://schemas.openxmlformats.org/spreadsheetml/2006/main'
def workbook(template,rows,formula=False,header=None):
    src=zipfile.ZipFile(io.BytesIO(template)); out=io.BytesIO()
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as dest:
        for name in src.namelist():
            data=src.read(name)
            if name=='xl/worksheets/sheet1.xml':
                root=ET.fromstring(data); sd=root.find('{'+NS+'}sheetData')
                for row in list(sd):
                    if int(row.attrib['r'])>=4:sd.remove(row)
                for n,values in enumerate(rows,4):
                    row=ET.SubElement(sd,'{'+NS+'}row',{'r':str(n)})
                    for col,value in values.items():
                        c=ET.SubElement(row,'{'+NS+'}c',{'r':col+str(n),'t':'inlineStr'})
                        if formula and col=='B':
                            c.attrib.pop('t');ET.SubElement(c,'{'+NS+'}f').text='1+1'
                        else:ET.SubElement(ET.SubElement(c,'{'+NS+'}is'),'{'+NS+'}t').text=value
                if header:
                    c=root.find('.//{'+NS+'}c[@r="A3"]');c.clear();c.set('r','A3');c.set('t','inlineStr')
                    ET.SubElement(ET.SubElement(c,'{'+NS+'}is'),'{'+NS+'}t').text=header
                data=ET.tostring(root,encoding='utf-8',xml_declaration=True)
            dest.writestr(name,data)
    return out.getvalue()

def upload(c,data,expect=200,csrf=True):
    boundary='ValenbotTest'+secrets.token_hex(12)
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="month"\r\n\r\n3802\r\n--{boundary}\r\nContent-Disposition: form-data; name="type"\r\n\r\nP\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="synthetic.xlsx"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()+data+f'\r\n--{boundary}--\r\n'.encode()
    headers={'Content-Type':'multipart/form-data; boundary='+boundary}
    if csrf:headers['X-CSRF-TOKEN']=c.csrf
    code,raw=c.raw('/api/import/robots/preview','POST',body,headers)
    assert code==expect,(code,raw[:500])
    return json.loads(raw) if raw else None

class ExcelImportAcceptance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.a,cls.t,cls.u=client('ADMIN'),client('TECHNICIAN'),client('USER')
        cls.template=cls.a.call('/import/robots/template')
    def confirm(self,p,c=None,expect=200):
        return (c or self.a).call('/import/robots/confirm','POST',{'token':p['token'],'reason':'Synthetic Excel acceptance'},expect)
    def test_01_template_and_role_boundaries(self):
        self.assertEqual(self.template,self.t.call('/import/robots/template'))
        for path in ['/sources','/audit','/export/sources','/export/audit']:
            self.a.call(path)
            self.t.call(path,expect=403);self.u.call(path,expect=403)
        self.u.call('/import/robots/template',expect=403)
        upload(self.u,workbook(self.template,[{'B':'synthetic'}]),403)
        self.u.call('/import/robots/confirm','POST',{'token':'none','reason':'x'},403)
        upload(self.t,workbook(self.template,[{'B':'synthetic'}]),403,csrf=False)
        self.assertNotIn('source',self.t.call('/robots/LBR-2609-P-0001'))
    def test_02_full_preview_import_source_and_one_use(self):
        rows=[{'A':'LBR-3801-P-0010','B':'Excel synthetic','C':'2026-09-16','D':'V3','F':'新机','L':'合成客户','Y':'SYNTHETIC-MODULE-IMPORT'}, {'B':'Auto synthetic','N':'Linux synthetic'}]
        before=len(self.a.call('/robots'));p=upload(self.t,workbook(self.template,rows));self.assertTrue(p['valid'],p)
        self.assertEqual(before,len(self.a.call('/robots')))
        self.confirm(p,self.a,409) # preview is bound to the submitting account
        result=self.confirm(p,self.t);self.assertEqual(result['count'],2);self.assertEqual(before+2,len(self.a.call('/robots')))
        self.confirm(p,self.t,409)
        robot=self.a.call('/robots/'+result['robots'][0]['sn']);self.assertEqual('在库',robot['fields']['status'])
        self.assertEqual('V3',robot['fields']['hardwareVersion']);self.assertEqual('合成客户',robot['fields']['customer'])
        self.assertEqual('2026-09-16',robot['fields']['productionDate']);self.assertIsNone(robot['fields']['qcResult'])
        self.assertEqual(1,len(robot['modules']));self.assertEqual('SYNTHETIC-MODULE-IMPORT',robot['modules'][0]['module_sn'])
        source=next(x for x in self.a.call('/sources') if x['id']==result['importId'])
        self.assertEqual(source['sha256'],p['sha256']);self.assertEqual(46,len(source['report']['mapping']))
        self.assertTrue(any(c['cell']=='D4' and c['value']=='V3' for c in source['sheets'][0]['cells']))
    def test_03_bad_files_and_rows_never_write(self):
        before=len(self.a.call('/robots'))
        upload(self.a,self.template,400);upload(self.a,b'not Excel',400)
        upload(self.a,workbook(self.template,[{'B':'x'}],formula=True),400)
        upload(self.a,workbook(self.template,[{'B':'x'}],header='Wrong'),400)
        for rows in [[{'A':'wrong','B':'x'}],[{'C':'2026-02-30','B':'x'}],[{'H':'not-mac','B':'x'}],[{'AP':'通过','B':'x'}],[{'M':'2026-01-01','B':'x'}],[{'J':'已交付','B':'x'}],[{'A':'LBR-2609-P-0001'}],[{'A':'LBR-3803-P-0001'},{'A':'LBR-3803-P-0001'}],[{'Y':'SYNTHETIC-MODULE-IMPORT'}]]:
            p=upload(self.a,workbook(self.template,rows));self.assertFalse(p['valid'],rows);self.assertFalse(p['token'])
        self.assertEqual(before,len(self.a.call('/robots')))
    def test_04_commit_rollback_and_revalidation(self):
        before=len(self.a.call('/robots'));sources=len(self.a.call('/sources'))
        p=upload(self.a,workbook(self.template,[{'A':'LBR-3802-P-9999','B':'rolled back'},{'B':'exceeds sequence'}]));self.assertTrue(p['valid'])
        self.confirm(p,expect=409)
        self.assertEqual(before,len(self.a.call('/robots')));self.assertEqual(sources,len(self.a.call('/sources')))
        self.assertFalse(any(x['sn']=='LBR-3802-P-9999' for x in self.a.call('/sn')['numbers']))
        p=upload(self.t,workbook(self.template,[{'A':'LBR-3804-P-0001','B':'changed scope'}]));self.assertTrue(p['valid'])
        self.a.call('/sn/generate','POST',{'month':'3804','type':'P','count':1,'reason':'competing reserve'})
        self.a.call('/robots','POST',{'sn':'LBR-3804-P-0001','reason':'competing create','fields':{}})
        self.confirm(p,self.t,409)
    def test_05_no_sn_permission_and_scope(self):
        name='import_tech_'+secrets.token_hex(5);password=secrets.token_urlsafe(24)
        reserved=self.a.call('/sn/generate','POST',{'month':'3805','type':'P','count':1,'reason':'assign test'})['sns'][0]
        self.a.call('/users','POST',{'username':name,'password':password,'role':'TECHNICIAN','snPermission':False,'scopeSns':[],'reason':'synthetic technician without SN permission'})
        restricted=Client(name,password)
        self.assertFalse(upload(restricted,workbook(self.template,[{'B':'no sn'}]))['valid'])
        self.assertFalse(upload(restricted,workbook(self.template,[{'A':'LBR-3805-P-0002'}]))['valid'])
        p=upload(restricted,workbook(self.template,[{'A':reserved,'B':'within scope'}]));self.assertTrue(p['valid'],p)
        self.confirm(p,restricted);self.assertEqual('within scope',restricted.call('/robots/'+reserved)['fields']['model'])
        for path in ['/audit','/sources']:restricted.call(path,expect=403)
        self.a.call('/users/'+name,'PATCH',{'scopeSns':[reserved],'snPermission':True,'reason':'restrict to existing robot'})
        scoped=Client(name,password)
        self.assertFalse(upload(scoped,workbook(self.template,[{'A':'LBR-3805-P-0003','B':'out of scope'}]))['valid'])
    def test_06_concurrent_exact_sn(self):
        raw=workbook(self.template,[{'A':'LBR-3806-P-0001','B':'concurrent'}])
        contexts=[client('ADMIN'),client('ADMIN')];previews=[upload(c,raw) for c in contexts]
        def submit(pair):
            c,p=pair;return c.raw('/api/import/robots/confirm','POST',json.dumps({'token':p['token'],'reason':'concurrent import'}).encode(),{'Content-Type':'application/json','X-CSRF-TOKEN':c.csrf})[0]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:codes=list(pool.map(submit,zip(contexts,previews)))
        self.assertEqual([200,409],sorted(codes))
    def test_07_limits(self):
        upload(self.a,b'x'*(2*1024*1024+1),413)
        upload(self.a,workbook(self.template,[{'B':'too many'} for _ in range(101)]),400)

if __name__=='__main__':unittest.main(verbosity=2)
