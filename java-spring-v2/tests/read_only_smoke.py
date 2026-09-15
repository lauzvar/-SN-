"""Read-only deployment validation (login and automatic access audit are expected)."""
import http.cookiejar, json, re, sys, urllib.request, urllib.parse
from pathlib import Path

root=Path(sys.argv[1]);base=sys.argv[2].rstrip('/')
accounts=json.loads((root/'trial-accounts.json').read_text(encoding='utf-8-sig'))
source=json.loads((root/'bootstrap.json').read_text(encoding='utf-8-sig'))['workbook']
report=[]
for account in accounts:
    opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    html=opener.open(base+'/login',timeout=20).read().decode()
    csrf=re.search(r'name="_csrf"[^>]*value="([^"]+)"',html).group(1)
    form=urllib.parse.urlencode({'username':account['username'],'password':account['password'],'_csrf':csrf}).encode()
    page=opener.open(base+'/login',form,timeout=20).read().decode()
    assert 'csrf-token' in page,account['username']
    def get(path):return json.loads(opener.open(base+'/api'+path,timeout=20).read())
    assert get('/me')['role']==account['role']
    robots=get('/robots');assert len(robots)==10
    for expected in source['robots']:
        robot=get('/robots/'+expected['sn'])
        for key,value in expected['fields'].items():
            if key in robot['fields']:assert robot['fields'][key]==value,(expected['sn'],key)
        if account['role']=='USER':
            assert 'osVersion' not in robot['fields'] and 'modules' not in robot
        else:assert len(robot['modules'])==len(expected['modules'])
    if account['role']=='ADMIN':assert len(get('/users'))==3
    if account['role']=='USER':
        image=opener.open(base+'/api/robots/LBR-2609-P-0001/label.png',timeout=20).read()
        assert image.startswith(b'\x89PNG');(root/'SN标签示例.png').write_bytes(image)
    report.append({'account':account['username'],'role':account['role'],'robots':10,'readOnlyValidation':'PASS'})
(root/'production-smoke.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
