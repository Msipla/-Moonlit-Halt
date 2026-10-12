"""Launch the actual remapped mod JAR in an official Minecraft + Fabric client.
No development mapping/classes, launcher accounts or external credentials are used.
Local offline identity connects only to the isolated localhost test server.
"""
from pathlib import Path
import concurrent.futures,hashlib,json,os,subprocess,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
RUNTIME=ROOT/'.runtime/release-client'
def get(url):
 with urllib.request.urlopen(url,timeout=90) as r:return r.read()
def download(url,target,sha1=None):
 target=Path(target)
 if target.exists() and (sha1 is None or hashlib.sha1(target.read_bytes()).hexdigest()==sha1):return target
 raw=get(url)
 if sha1 and hashlib.sha1(raw).hexdigest()!=sha1:raise RuntimeError('SHA1 mismatch: '+url)
 target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw);return target

def launch(log):
 RUNTIME.mkdir(parents=True,exist_ok=True)
 versions=json.loads(get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
 v=next(x for x in versions['versions'] if x['id']=='1.20.1')
 vanilla=json.loads(download(v['url'],RUNTIME/'version.json',v['sha1']).read_text())
 fabric=json.loads(get('https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.14.25/profile/json'))
 (RUNTIME/'fabric-profile.json').write_text(json.dumps(fabric,indent=2)+'\n')
 natives=RUNTIME/'natives';natives.mkdir(exist_ok=True);cp=[];jobs=[]
 def allowed(lib):
  if 'rules' not in lib:return True
  value=False
  for rule in lib['rules']:
   if rule.get('os',{}).get('name','linux')=='linux' and not rule.get('os',{}).get('arch','').startswith('x86'):
    value=rule['action']=='allow'
  return value
 for lib in vanilla['libraries']:
  if not allowed(lib):continue
  a=lib.get('downloads',{}).get('artifact')
  if a:jobs.append((a['url'],RUNTIME/'libraries'/a['path'],a['sha1']));cp.append(str(RUNTIME/'libraries'/a['path']))
  classifier=lib.get('natives',{}).get('linux')
  if classifier:
   a=lib['downloads']['classifiers'][classifier.replace('${arch}','64')];jobs.append((a['url'],RUNTIME/'libraries'/a['path'],a['sha1']))
 for lib in fabric['libraries']:
  group,name,version=lib['name'].split(':');rel='/'.join([group.replace('.','/'),name,version,name+'-'+version+'.jar']);url=lib.get('url','https://maven.fabricmc.net/')+rel
  sha=get(url+'.sha1').decode().split()[0];jobs.append((url,RUNTIME/'libraries'/rel,sha));cp.append(str(RUNTIME/'libraries'/rel))
 a=vanilla['downloads']['client'];jobs.append((a['url'],RUNTIME/'client.jar',a['sha1']));cp.append(str(RUNTIME/'client.jar'))
 with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
  list(pool.map(lambda x:download(*x),jobs))
 for path in (RUNTIME/'libraries').rglob('*natives-linux*.jar'):
  with zipfile.ZipFile(path) as z:
   for item in z.infolist():
    if item.filename.endswith('.so'):
     (natives/Path(item.filename).name).write_bytes(z.read(item))
 assets=RUNTIME/'assets';idx=vanilla['assetIndex'];index=download(idx['url'],assets/'indexes'/f"{idx['id']}.json",idx['sha1']);objects=json.loads(index.read_text())['objects']
 # Full official assets, not substitute fonts/graphics. Deduplicate hashes.
 hashes={x['hash'] for x in objects.values()}
 with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
  list(pool.map(lambda h:download('https://resources.download.minecraft.net/'+h[:2]+'/'+h,assets/'objects'/h[:2]/h,h),hashes))
 mods=RUNTIME/'mods';mods.mkdir(exist_ok=True)
 import shutil
 version=next(x.split('=',1)[1] for x in (ROOT/'gradle.properties').read_text().splitlines() if x.startswith('mod_version='))
 jar=ROOT/f'build/libs/glimmering-abyss-{version}.jar'
 for old in mods.glob('glimmering-abyss-*.jar'):old.unlink()
 shutil.copy2(jar,mods/jar.name);api=next((ROOT/'build/test-mods').glob('fabric-api-*.jar'));shutil.copy2(api,mods/api.name)
 cmd=['java','-Xms512M','-Xmx2G','-XX:ActiveProcessorCount=4','-Djava.library.path='+str(natives),'-Dorg.lwjgl.librarypath='+str(natives),'-Dabyss.capture.phase4=true','-Dabyss.port=25585','-cp',os.pathsep.join(cp),fabric['mainClass'],'--username','Phase4Tester','--version','1.20.1','--gameDir',str(RUNTIME),'--assetsDir',str(assets),'--assetIndex',idx['id'],'--uuid','00000000000000000000000000000004','--accessToken','0','--userType','legacy','--versionType','release','--width','1280','--height','720']
 (RUNTIME/'launch-provenance.json').write_text(json.dumps({'minecraft':'1.20.1','fabric':'0.14.25','release_jar':jar.name,'release_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'development_classes':False,'assets':len(hashes),'official_manifest_url':v['url']},indent=2)+'\n')
 subprocess.run(cmd,cwd=RUNTIME,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=900)
 return RUNTIME
