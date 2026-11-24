#!/usr/bin/env python3
"""Real publisher -> SeaweedFS -> ingestion -> catalog -> review -> public download.
Only the identity provider is a local fixture; no production identities or data.
"""
import base64
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
import uuid
import zipfile

ISSUER = 'http://publishing-fixture/issuer'

def b64(value): return base64.urlsafe_b64encode(value).rstrip(b'=').decode()

def der_item(data, offset):
    tag, size = data[offset], data[offset+1]
    offset += 2
    if size & 128:
        count = size & 127
        size = int.from_bytes(data[offset:offset+count], 'big')
        offset += count
    return tag, data[offset:offset+size], offset+size


with tempfile.TemporaryDirectory() as directory:
    key = Path(directory) / 'fixture.pem'
    subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-out',str(key)], check=True, capture_output=True)
    public = subprocess.run(['openssl','rsa','-in',str(key),'-RSAPublicKey_out','-outform','DER'],check=True,capture_output=True).stdout
    _, sequence, _ = der_item(public, 0)
    _, modulus, offset = der_item(sequence, 0)
    _, exponent, _ = der_item(sequence, offset)
    jwks = {'keys':[{'kty':'RSA','kid':'publishing-fixture','alg':'RS256','use':'sig','n':b64(modulus.lstrip(b'\x00')),'e':b64(exponent)}]}
    def token(subject='publisher', operator=False, expired=False, audience=None):
        now = int(time.time())
        claims = {'iss':ISSUER,'aud':audience or ['bookrush-ingestion','bookrush-catalog-admin','account'], 'sub':subject,'iat':now-300 if expired else now,'exp':now-180 if expired else now+1800}
        if operator: claims.update(groups=['platform-operators'], scope='bookrush.catalog.write bookrush.catalog.read bookrush.assets.write')
        data = b64(json.dumps({'alg':'RS256','kid':'publishing-fixture'}).encode())+'.'+b64(json.dumps(claims).encode())
        signature = subprocess.run(['openssl','dgst','-sha256','-sign',str(key)],input=data.encode(),check=True,capture_output=True).stdout
        return data+'.'+b64(signature)

    class Identity(BaseHTTPRequestHandler):
        def log_message(self, *args): pass
        def respond(self, value):
            data = json.dumps(value).encode()
            self.send_response(200); self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
        def do_GET(self): self.respond(jwks)
        def do_POST(self):
            # RestClient may send a chunked form body. Consume it before answering.
            if self.headers.get('Transfer-Encoding') == 'chunked':
                while True:
                    size=int(self.rfile.readline().split(b';')[0],16)
                    if not size:self.rfile.readline();break
                    self.rfile.read(size);self.rfile.read(2)
            else:self.rfile.read(int(self.headers.get('Content-Length','0')))
            self.respond({'access_token':token('ingestion-service',True),'expires_in':1800} if self.path == '/token' else {})
    server = ThreadingHTTPServer(('0.0.0.0',8765), Identity)
    threading.Thread(target=server.serve_forever,daemon=True).start()

    def call(base, path, method='GET', body=None, bearer=None, expected=200, headers=None):
        request_headers = dict(headers or {})
        if bearer: request_headers['Authorization']='Bearer '+bearer
        if isinstance(body,dict): request_headers['Content-Type']='application/json';body=json.dumps(body).encode()
        request=urllib.request.Request(base+path,data=body,headers=request_headers,method=method)
        try:
            with urllib.request.urlopen(request,timeout=30) as response: status,data=response.status,response.read()
        except urllib.error.HTTPError as error: status,data=error.code,error.read()
        assert status == expected, f'{method} {path}: expected {expected}, got {status}: {data[:300]!r}'
        return json.loads(data) if data.startswith((b'{',b'[')) else data

    publisher='http://web'; catalog='http://catalog:8080'
    for base in (catalog,'http://publisher:8099','http://ingestion:8090'):
        for attempt in range(90):
            try:
                if call(base,'/actuator/health').get('status') == 'UP':break
            except Exception:time.sleep(1)
        else:raise AssertionError(f'{base}: service did not become healthy')
    with urllib.request.urlopen('http://web/healthz',timeout=10) as response:
        assert 'https://seaweedfs-s3-bookrush.jteodoro.tec.br' in response.headers.get('Content-Security-Policy',''), 'browser upload is blocked by console CSP'
    owner=token(); other=token('other'); admin=token('editor',True)
    path='/api/v1/publisher/submissions'
    call(publisher,path,expected=401)
    call(publisher,path,bearer=token(expired=True),expected=401)
    call(publisher,path,bearer=token(audience=['wrong']),expected=401)
    call(publisher,'/api/v1/publisher/review',bearer=owner,expected=403)
    call(publisher,path,'OPTIONS',headers={'Origin':'http://publisher.test','Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'authorization,content-type'})
    draft=call(publisher,path,'POST',{'title':'Publisher E2E '+uuid.uuid4().hex},owner); submission=draft['id']; item=path+'/'+submission
    call(publisher,item,bearer=other,expected=404)
    call(publisher,item+'/submit','POST',bearer=owner,expected=409)
    source=io.BytesIO()
    with zipfile.ZipFile(source,'w') as archive:
        archive.writestr('mimetype','application/epub+zip')
        archive.writestr('META-INF/container.xml','<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>')
        archive.writestr('content.opf','<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="book">test</dc:identifier><dc:title>Fixture</dc:title><dc:language>en</dc:language></metadata><manifest><item id="text" href="text.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="text"/></spine></package>')
        archive.writestr('text.xhtml','<html xmlns="http://www.w3.org/1999/xhtml"><body><p>Publisher fixture.</p></body></html>')
    binary=source.getvalue();digest=hashlib.sha256(binary).hexdigest()
    upload=call(publisher,item+'/upload','POST',{'filename':'book.epub','contentType':'application/epub+zip'},owner)
    preflight=urllib.request.Request(upload['url'].split('?')[0],method='OPTIONS',headers={'Origin':'http://publisher.test','Access-Control-Request-Method':'PUT','Access-Control-Request-Headers':'content-type'})
    with urllib.request.urlopen(preflight,timeout=10) as response:
        assert response.headers.get('Access-Control-Allow-Origin')=='http://publisher.test', 'staging CORS is not configured for the publisher'
    call('',upload['url'],'PUT',binary,headers={'Content-Type':'application/epub+zip'})
    call(publisher,item+'/upload/'+upload['uploadId']+'/finalize','POST',{'sha256':digest},owner)
    call(publisher,item+'/rights','POST',{'rightType':'DISTRIBUTION','territory':'GLOBAL','evidenceReference':'E2E explicit author attestation'},other,expected=404)
    call(publisher,item+'/rights','POST',{'rightType':'DISTRIBUTION','territory':'GLOBAL','evidenceReference':'E2E explicit author attestation'},owner)
    assert call(publisher,item+'/submit','POST',bearer=owner)['status']=='PROCESSING'
    assert call(publisher,item+'/submit','POST',bearer=owner)['status']=='PROCESSING'
    book=call(publisher,item,bearer=owner)['catalog_book_id']
    assets=call(catalog,f'/api/admin/books/{book}/assets',bearer=admin)
    assert len([asset for asset in assets if asset['role']=='SOURCE'])==1
    assert not any(asset['role']=='PUBLIC' for asset in assets), 'unreviewed upload was exposed'
    call(publisher,item+'/review','POST',{'status':'APPROVED'},owner,expected=403)
    call(publisher,item+'/review','POST',{'status':'APPROVED'},admin)
    assert call(publisher,item,bearer=owner)['status']=='PUBLISHED'
    assets=call(catalog,f'/api/admin/books/{book}/assets',bearer=admin)
    public_asset=next(asset for asset in assets if asset['role']=='PUBLIC')
    link=call(catalog,f"/api/books/{book}/assets/{public_asset['id']}/download-url")
    downloaded=call('',link['url'])
    assert hashlib.sha256(downloaded).hexdigest()==digest, 'public bytes differ from the submitted book'
    call(publisher,item+'/review','POST',{'status':'APPROVED'},admin)
    call(publisher,item+'/withdraw','POST',bearer=other,expected=404)
    call(publisher,item+'/withdraw','POST',bearer=owner)
    call(catalog,f"/api/books/{book}/assets/{public_asset['id']}/download-url",expected=404)
    # The second supported format must follow the same real path.
    pdf=b'%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n'
    pdf_draft=call(publisher,path,'POST',{'title':'PDF publishing fixture'},owner)
    pdf_item=path+'/'+pdf_draft['id']
    pdf_upload=call(publisher,pdf_item+'/upload','POST',{'filename':'book.pdf','contentType':'application/pdf'},owner)
    call('',pdf_upload['url'],'PUT',pdf,headers={'Content-Type':'application/pdf'})
    call(publisher,pdf_item+'/upload/'+pdf_upload['uploadId']+'/finalize','POST',{'sha256':hashlib.sha256(pdf).hexdigest()},owner)
    call(publisher,pdf_item+'/rights','POST',{'rightType':'DISTRIBUTION','territory':'GLOBAL','evidenceReference':'E2E author attestation'},owner)
    call(publisher,pdf_item+'/submit','POST',bearer=owner)
    call(publisher,pdf_item+'/review','POST',{'status':'APPROVED'},admin)
    pdf_book=call(publisher,pdf_item,bearer=owner)['catalog_book_id']
    pdf_asset=next(asset for asset in call(catalog,f'/api/admin/books/{pdf_book}/assets',bearer=admin) if asset['role']=='PUBLIC')
    pdf_link=call(catalog,f"/api/books/{pdf_book}/assets/{pdf_asset['id']}/download-url")
    assert call('',pdf_link['url'])==pdf
    call(publisher,pdf_item+'/withdraw','POST',bearer=owner)
    print('Publishing E2E passed: real PostgreSQL, SeaweedFS, ingestion, catalog, owned upload, rights, editorial approval, exact EPUB/PDF public downloads and withdrawal; JWT/CORS/ownership checked.')
    server.shutdown()
