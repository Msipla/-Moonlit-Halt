from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import hashlib,json,lzma,subprocess,tarfile,urllib.request,zipfile
R=Path(__file__).resolve().parent
def get(url,path,sha=None):
    if path.exists() and (not sha or hashlib.sha256(path.read_bytes()).hexdigest()==sha):return path
    with urllib.request.urlopen(url,timeout=60) as response,path.open('wb') as f:
        while b:=response.read(1024*1024):f.write(b)
    if sha:assert hashlib.sha256(path.read_bytes()).hexdigest()==sha
    print('Downloaded/verified',path.name,flush=True);return path
def java():
    f=get('https://download.java.net/openjdk/jdk17/ri/openjdk-17+35_linux-x64_bin.tar.gz',R/'jdk.tar.gz','aef49cc7aa606de2044302e757fa94c8e144818e93487081c4fd319ca858134b')
    with tarfile.open(f) as z:z.extractall(R)
    for p in (R/'jdk-17/bin').iterdir():p.chmod(0o755)
    (R/'jdk-17/lib/jspawnhelper').chmod(0o755)
def gradle():
    f=get('https://downloads.gradle.org/distributions/gradle-8.2.1-bin.zip',R/'gradle.zip','03ec176d388f2aa99defcadc3ac6adf8dd2bce5145a129659537c0874dea5ad1')
    with zipfile.ZipFile(f) as z:z.extractall(R)
    (R/'gradle-8.2.1/bin/gradle').chmod(0o755)
def x11():
    packages={};wanted={'xvfb','libxfont2','x11-xkb-utils','libxkbfile1','libxtst6'}
    for dist,sec in [('noble-updates','main'),('noble-updates','universe'),('noble','main'),('noble','universe')]:
        f=get(f'https://archive.ubuntu.com/ubuntu/dists/{dist}/{sec}/binary-amd64/Packages.xz',R/f'{dist}-{sec}.xz')
        for para in lzma.decompress(f.read_bytes()).decode().split('\n\n'):
            fields=dict(l.split(': ',1) for l in para.splitlines() if ': ' in l and not l.startswith(' '))
            name=fields.get('Package')
            if name in wanted and name not in packages:packages[name]=fields
    for name,fields in packages.items():
        f=get('https://archive.ubuntu.com/ubuntu/'+fields['Filename'],R/(name+'.deb'),fields['SHA256'])
        subprocess.run(['dpkg-deb','-x',str(f),str(R/'x11')],check=True)
    (R/'ubuntu-packages.json').write_text(json.dumps({k:{f:v[f] for f in ['Version','Filename','SHA256']} for k,v in packages.items()},indent=2))
def glfw():
    u='https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-glfw/3.3.6/lwjgl-glfw-3.3.6-natives-linux.jar'
    f=get(u,R/'glfw.jar');expected=urllib.request.urlopen(u+'.sha1',timeout=30).read().decode().split()[0]
    assert hashlib.sha1(f.read_bytes()).hexdigest()==expected
    with zipfile.ZipFile(f) as z:
        name=next(n for n in z.namelist() if n.endswith('libglfw.so'))
        (R/'glfw').mkdir(exist_ok=True);(R/'glfw/libglfw.so').write_bytes(z.read(name))
with ThreadPoolExecutor(4) as ex:
    for _ in ex.map(lambda fn:fn(),[java,gradle,x11,glfw]):pass
print('Toolchain ready',flush=True)
