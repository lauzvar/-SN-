"""Run HTTP acceptance against a child JVM and a fresh synthetic database/schema.

Prepare fixtures with synthetic_fixture.py before Maven integration tests. This runner
reuses them and never creates, drops or truncates a database/schema.
"""
import os, re, subprocess, sys, time, urllib.request
from pathlib import Path

def main():
    url=os.environ.get('VALENBOT_DB_URL','')
    if os.environ.get('VALENBOT_TEST_ACK')!='robot_sn_v2_test' or not re.fullmatch(r'jdbc:postgresql://[^/]+/robot_sn_v2_test(?:\?.*)?',url):
        raise SystemExit('Refusing: explicit isolated test database required')
    private=Path(os.environ['VALENBOT_TEST_PRIVATE']).resolve()
    if not (private/'bootstrap.json').is_file():raise SystemExit('Generate synthetic fixtures first')
    project=Path(__file__).resolve().parents[1]
    jar=project/'target/robot-sn-system-2.3.0.jar'
    env={**os.environ,'VALENBOT_PORT':'8083','VALENBOT_PUBLIC_URL':'http://127.0.0.1:8083',
         'VALENBOT_TEST_URL':'http://127.0.0.1:8083','VALENBOT_BOOTSTRAP_FILE':str(private/'bootstrap.json'),
         'PYTHONIOENCODING':'utf-8'}
    java=os.environ.get('VALENBOT_JAVA',str(Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')))
    # Do not accidentally run mutations against somebody else's server.
    import socket
    with socket.socket() as check:
        if check.connect_ex(('127.0.0.1',8083))==0:raise SystemExit('Port 8083 occupied; no existing server stopped')
    with (private/'http-server.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen([java,'-Dfile.encoding=UTF-8','-jar',str(jar)],env=env,stdout=log,stderr=subprocess.STDOUT)
        try:
            for _ in range(60):
                if process.poll() is not None:raise RuntimeError('Test server exited; inspect private http-server.log')
                try:
                    with urllib.request.urlopen(env['VALENBOT_TEST_URL']+'/login',timeout=1) as response:
                        if response.status==200:break
                except Exception:time.sleep(1)
            else:raise RuntimeError('Test server startup timed out')
            for script in ['test_v21.py','test_v22.py','test_v23.py']:
                result=subprocess.run([sys.executable,str(project/'tests'/script)],env=env,capture_output=True,text=True,encoding='utf-8')
                (private/(script+'.log')).write_text(result.stdout+result.stderr,encoding='utf-8')
                print(result.stdout+result.stderr)
                if result.returncode:raise RuntimeError(script+' failed')
        finally:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait()

if __name__=='__main__':main()
