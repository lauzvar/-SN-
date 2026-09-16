"""Real TLS smoke test, isolated DB only. Supply a test PKCS#12 and CA via environment."""
import hashlib, http.client, http.cookiejar, json, os, re, socket, ssl, subprocess, time, urllib.parse, urllib.request
from pathlib import Path

def main():
    if os.environ.get('VALENBOT_TEST_ACK')!='robot_sn_v2_test' or not re.fullmatch(r'jdbc:postgresql://[^/]+/robot_sn_v2_test(?:\?.*)?',os.environ.get('VALENBOT_DB_URL','')):
        raise SystemExit('Explicit isolated database required')
    project=Path(__file__).resolve().parents[1];private=Path(os.environ['VALENBOT_TEST_PRIVATE'])
    for port in (8444,8084):
        with socket.socket() as s:
            if s.connect_ex(('127.0.0.1',port))==0:raise SystemExit('TLS test port occupied')
    context=ssl.create_default_context(cafile=os.environ['VALENBOT_TEST_CA'])
    env={**os.environ,'VALENBOT_PORT':'8444','VALENBOT_HTTP_PORT':'8084','VALENBOT_PUBLIC_URL':'https://127.0.0.1:8444','SERVER_SSL_ENABLED':'true','VALENBOT_COOKIE_SECURE':'true'}
    java=Path(os.environ['JAVA_HOME'])/'bin/java.exe' if os.name=='nt' else Path(os.environ['JAVA_HOME'])/'bin/java'
    with (private/'tls-server.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen([str(java),'-Dfile.encoding=UTF-8','-jar',str(project/'target/robot-sn-system-2.3.0.jar')],env=env,stdout=log,stderr=subprocess.STDOUT)
        try:
            for _ in range(45):
                if process.poll() is not None:raise RuntimeError('TLS server exited')
                try:
                    with urllib.request.urlopen('https://127.0.0.1:8444/login',context=context,timeout=2) as r:
                        assert r.status==200;break
                except Exception:time.sleep(1)
            else:raise RuntimeError('TLS startup timed out')
            plain=http.client.HTTPConnection('127.0.0.1',8084,timeout=5);plain.request('GET','/r/LBR-2609-P-0001?from=qr',headers={'Host':'untrusted.example'})
            response=plain.getresponse();assert response.status==308;assert response.getheader('Location')=='https://127.0.0.1:8444/r/LBR-2609-P-0001?from=qr';response.read();plain.close()
            plain=http.client.HTTPConnection('127.0.0.1',8084,timeout=5);plain.request('POST','/login',body='username=not-a-user');response=plain.getresponse();assert response.status==400;response.read();plain.close()
            jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPSHandler(context=context),urllib.request.HTTPCookieProcessor(jar))
            page=opener.open('https://127.0.0.1:8444/login').read().decode();token=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page).group(1)
            account=next(a for a in json.loads((private/'trial-accounts.json').read_text(encoding='utf-8-sig')) if a['role']=='ADMIN')
            payload=urllib.parse.urlencode({'username':account['username'],'password':account['password'],'_csrf':token}).encode()
            page=opener.open(urllib.request.Request('https://127.0.0.1:8444/login',data=payload)).read().decode();assert 'csrf-token' in page
            assert len(list(jar))>0 and all(c.secure for c in jar)
            info=json.load(opener.open('https://127.0.0.1:8444/api/robots/LBR-2609-P-0001/label-info'));assert info['url'].startswith('https://127.0.0.1:8444/r/')
            with socket.create_connection(('127.0.0.1',8444),timeout=5) as sock:
                with context.wrap_socket(sock,server_hostname='localhost') as tls:
                    assert tls.version() in ['TLSv1.2','TLSv1.3']
                    certificate=hashlib.sha256(tls.getpeercert(binary_form=True)).hexdigest()
            result={'tlsLogin':True,'certificateHostnameVerified':True,'secureCookies':True,'httpRedirect':True,'httpPostRejected':True,'httpsQr':True,'serverSha256':certificate}
            (private/'tls-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))
        finally:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait()

if __name__=='__main__':main()
