"""Generate private, disposable fixtures; never copy production workbook or credentials to CI."""
import json, secrets, sys
from pathlib import Path

def generate(destination):
    directory = Path(destination)
    directory.mkdir(parents=True, exist_ok=True)
    accounts = [dict(username=name, role=role, password=secrets.token_urlsafe(24), snPermission=role!='USER')
                for name, role in [('admin_trial','ADMIN'),('tech_trial','TECHNICIAN'),('user_trial','USER')]]
    workbook = dict(filename='SYNTHETIC-NOT-BUSINESS.xlsx', sha256='0'*64,
                    report={'mode':'synthetic','note':'自动验收合成数据，不是原始业务台账'},
                    sheets=[dict(name=name,cells=[['SYNTHETIC',name]]) for name in ['机器人信息管理','编码规则','模块清单']],
                    robots=[dict(sn=f'LBR-2609-P-{n:04}',row=n+1,
                                 fields={'model':'SYNTHETIC-ROBOT','nickname':f'Test-{n}'},
                                 modules=[{'slot':'头部舵机','sn':f'SYNTHETIC-HEAD-{n}'}]) for n in range(1,11)])
    for name, data in [('trial-accounts.json',accounts),('bootstrap.json',{'accounts':accounts,'workbook':workbook})]:
        (directory/name).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':
    generate(sys.argv[1])
    print('Synthetic fixture generated (passwords omitted).')
