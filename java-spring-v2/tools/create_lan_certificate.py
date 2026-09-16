"""Create a private LAN CA and server PKCS#12 without printing secrets.

Requires cryptography. Run in a PRIVATE directory outside source/distribution.
Read the one-time password file into the runtime user's encrypted credential store,
then remove that plaintext file. Distribute only root-ca.cer, never private keys.
"""
import argparse, datetime, ipaddress, json, secrets, socket
from pathlib import Path
from cryptography import x509
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12

def create(directory, address):
    directory.mkdir(parents=True,exist_ok=True)
    if any(directory.iterdir()):raise SystemExit('Refusing to overwrite existing certificate directory')
    now=datetime.datetime.now(datetime.timezone.utc)
    password=secrets.token_urlsafe(40).encode()
    root_key=rsa.generate_private_key(public_exponent=65537,key_size=4096)
    root_name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Valenbot LAN Root CA '+now.strftime('%Y%m%d'))])
    root=(x509.CertificateBuilder().subject_name(root_name).issuer_name(root_name).public_key(root_key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(minutes=5)).not_valid_after(now+datetime.timedelta(days=1825)).add_extension(x509.BasicConstraints(ca=True,path_length=0),critical=True).add_extension(x509.KeyUsage(digital_signature=True,content_commitment=False,key_encipherment=False,data_encipherment=False,key_agreement=False,key_cert_sign=True,crl_sign=True,encipher_only=None,decipher_only=None),critical=True).add_extension(x509.SubjectKeyIdentifier.from_public_key(root_key.public_key()),critical=False).sign(root_key,hashes.SHA256()))
    server_key=rsa.generate_private_key(public_exponent=65537,key_size=3072)
    names=[x509.DNSName('localhost'),x509.DNSName(socket.gethostname()),x509.IPAddress(ipaddress.ip_address('127.0.0.1')),x509.IPAddress(ipaddress.ip_address(address))]
    server=(x509.CertificateBuilder().subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Valenbot LAN Server')])).issuer_name(root.subject).public_key(server_key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(minutes=5)).not_valid_after(now+datetime.timedelta(days=365)).add_extension(x509.BasicConstraints(ca=False,path_length=None),critical=True).add_extension(x509.SubjectAlternativeName(names),critical=False).add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]),critical=False).add_extension(x509.KeyUsage(digital_signature=True,content_commitment=False,key_encipherment=True,data_encipherment=False,key_agreement=False,key_cert_sign=False,crl_sign=False,encipher_only=None,decipher_only=None),critical=True).add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(root_key.public_key()),critical=False).sign(root_key,hashes.SHA256()))
    (directory/'root-ca.cer').write_bytes(root.public_bytes(serialization.Encoding.DER))
    (directory/'root-ca.pem').write_bytes(root.public_bytes(serialization.Encoding.PEM))
    (directory/'root-ca-key.encrypted.pem').write_bytes(root_key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.BestAvailableEncryption(password)))
    (directory/'valenbot.p12').write_bytes(pkcs12.serialize_key_and_certificates(b'valenbot',server_key,server,[root],serialization.BestAvailableEncryption(password)))
    (directory/'one-time-password.txt').write_bytes(password)
    report={'address':address,'expires':server.not_valid_after_utc.isoformat(),'rootSha256':root.fingerprint(hashes.SHA256()).hex(),'serverSha256':server.fingerprint(hashes.SHA256()).hex()}
    (directory/'certificate-info.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('private_directory',type=Path);p.add_argument('lan_ip');args=p.parse_args();create(args.private_directory,args.lan_ip)
