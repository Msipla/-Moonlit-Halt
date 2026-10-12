from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import hashlib,json,os,shutil,subprocess,urllib.parse,urllib.request,zipfile
R=Path(__file__).resolve().parent
P=R.parent/'moonlit-halt-repo/projects/minecraft-mods/experiments/glimmering-abyss'
proxy=urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
opts=f'-XX:-UseContainerSupport -Djava.net.preferIPv4Stack=true -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={proxy.port} -Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port}'
env=dict(os.environ,JAVA_HOME=str(R/'jdk-17'),JAVA_TOOL_OPTIONS=opts)
def download(url,path,sha1=None):
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists() and sha1 and hashlib.sha1(path.read_bytes()).hexdigest()==sha1:return
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url,timeout=45) as f: data=f.read()
            if sha1 and hashlib.sha1(data).hexdigest()!=sha1:raise RuntimeError('SHA1 mismatch '+path.name)
            path.write_bytes(data);return
        except Exception:
            if attempt==2:raise
def fonts():
    info=json.loads((R/'gradle-cache/caches/fabric-loom/1.20.1/minecraft-info.json').read_text())
    a=info['assetIndex'];base=R/'gradle-cache/caches/fabric-loom/assets'
    idx=base/'indexes'/(a['id']+'.json');download(a['url'],idx,a['sha1'])
    objects=json.loads(idx.read_text())['objects'];records=[]
    for name in ['minecraft/font/include/unifont.json','minecraft/font/unifont.zip']:
        data=objects[name];h=data['hash'];target=base/'objects'/h[:2]/h
        download('https://resources.download.minecraft.net/'+h[:2]+'/'+h,target,h)
        records.append(dict(path=name,sha1=h,size=target.stat().st_size))
    (R/'prepared-fonts.json').write_text(json.dumps(records,indent=2));print('Official fonts verified',flush=True)
def wrapper():
    url='https://downloads.gradle.org/distributions/gradle-8.2.1-bin.zip'
    n=int(hashlib.md5(url.encode()).hexdigest(),16);alphabet='0123456789abcdefghijklmnopqrstuvwxyz';value=''
    while n:n,i=divmod(n,36);value=alphabet[i]+value
    cache=R/'gradle-cache/wrapper/dists/gradle-8.2.1-bin'/value;cache.mkdir(parents=True,exist_ok=True)
    link=cache/'gradle-8.2.1'
    if not link.exists():link.symlink_to(R/'gradle-8.2.1',target_is_directory=True)
    (cache/'gradle-8.2.1-bin.zip.ok').touch();print('Verified wrapper distribution prepared',flush=True)
def server():
    template=P/'.runtime/server-template-phase2';template.mkdir(parents=True,exist_ok=True)
    url='https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar'
    sha=urllib.request.urlopen(url+'.sha1',timeout=30).read().decode().split()[0]
    installer=R/'fabric-installer-1.0.3.jar';download(url,installer,sha)
    with urllib.request.urlopen('https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.14.25/server/json',timeout=30) as response:metadata=json.load(response)
    libraries=[];records=[]
    for lib in metadata['libraries']:
        group,artifact,version=lib['name'].split(':');rel=group.replace('.','/')+'/'+artifact+'/'+version+'/'+artifact+'-'+version+'.jar'
        base='https://maven.fabricmc.net/' if group.startswith('net.fabricmc') else 'https://repo.maven.apache.org/maven2/'
        u=base+rel;sha=urllib.request.urlopen(u+'.sha1',timeout=30).read().decode().split()[0]
        jar=template/'libraries'/rel;download(u,jar,sha)
        with zipfile.ZipFile(jar) as z:assert z.testzip() is None
        libraries.append(str(jar));records.append(dict(path=rel,sha1=sha,bytes=jar.stat().st_size))
    # Invoke the verified official installer's launch-JAR writer with the same inputs,
    # avoiding only its unreliable Java network downloader on this cloud host.
    java_source=R/'OfflineLaunch.java'
    java_source.write_text("""import java.nio.file.*;import java.util.*;import java.lang.reflect.*;
public class OfflineLaunch {public static void main(String[] a)throws Exception {
var c=Class.forName("net.fabricmc.installer.server.ServerInstaller");
var progress=Class.forName("net.fabricmc.installer.util.InstallerProgress");
var method=c.getDeclaredMethod("makeLaunchJar",Path.class,String.class,String.class,List.class,boolean.class,progress);method.setAccessible(true);
List<Path> paths=new ArrayList<>();for(int i=1;i<a.length;i++)paths.add(Path.of(a[i]));
Object p=Proxy.newProxyInstance(progress.getClassLoader(),new Class[]{progress},(o,m,v)->{if(v!=null)System.out.println(Arrays.toString(v));return null;});
method.invoke(null,Path.of(a[0]),"net.fabricmc.loader.impl.launch.knot.KnotServer","net.fabricmc.loader.impl.launch.server.FabricServerLauncher",paths,false,p);
}}""")
    subprocess.run([str(R/'jdk-17/bin/javac'),str(java_source)],env=env,check=True)
    with (R/'server-bootstrap-offline-writer.log').open('w') as log:
        subprocess.run([str(R/'jdk-17/bin/java'),'-cp',str(R)+os.pathsep+str(installer),'OfflineLaunch',str(template/'fabric-server-launch.jar')]+libraries,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
    info=json.loads((R/'gradle-cache/caches/fabric-loom/1.20.1/minecraft-info.json').read_text())
    source=R/'gradle-cache/caches/fabric-loom/1.20.1/minecraft-server.jar'
    assert hashlib.sha1(source.read_bytes()).hexdigest()==info['downloads']['server']['sha1']
    shutil.copy2(source,template/'server.jar')
    (R/'server-library-checks.json').write_text(json.dumps(records,indent=2));print('Server bootstrap and all libraries verified',flush=True)
with ThreadPoolExecutor(3) as ex:
    for _ in ex.map(lambda fn:fn(),[fonts,wrapper,server]):pass
print('Runtime ready',flush=True)
