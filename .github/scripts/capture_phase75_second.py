"""Supplement initial second-seed rendering with the exact already-tested JAR."""
from pathlib import Path
import sys,os,json,hashlib,shutil,subprocess,time,urllib.request,re
from PIL import Image
PROJECT=Path(__file__).resolve().parents[2]/'projects/minecraft-mods/experiments/glimmering-abyss'
sys.path.insert(0,str(PROJECT/'tools'))
from run_experiment import wait_for,command
from launch_release_client import launch
run=os.environ['GITHUB_RUN_ID'];out=PROJECT/'evidence/phase75/runs'/run;out.mkdir(parents=True)
release=PROJECT/'evidence/phase75/runs/38401293992/glimmering-abyss-0.7.5.jar'
expected=json.loads((release.parent/'checksums.json').read_text())['artifacts'][release.name]['sha256']
assert hashlib.sha256(release.read_bytes()).hexdigest()==expected
(PROJECT/'build/libs').mkdir(parents=True,exist_ok=True);shutil.copy2(release,PROJECT/'build/libs'/release.name)
runtime=PROJECT/'.runtime';server=runtime/('phase75-second-'+run);server.mkdir(parents=True)
installer=runtime/'fabric-installer-1.0.3.jar';url='https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar'
with urllib.request.urlopen(url,timeout=90) as r:installer.write_bytes(r.read())
with urllib.request.urlopen(url+'.sha1',timeout=90) as r:assert hashlib.sha1(installer.read_bytes()).hexdigest()==r.read().decode().split()[0]
with (out/'installer.log').open('w') as log:subprocess.run(['java','-jar',str(installer),'server','-mcversion','1.20.1','-loader','0.14.25','-downloadMinecraft','-dir',str(server)],stdout=log,stderr=subprocess.STDOUT,check=True)
(server/'mods').mkdir();shutil.copy2(release,server/'mods'/release.name);api=next((PROJECT/'build/test-mods').glob('fabric-api-*.jar'));shutil.copy2(api,server/'mods'/api.name)
(server/'eula.txt').write_text('eula=true\n');props={'server-ip':'127.0.0.1','server-port':25585,'online-mode':'false','level-name':'second-capture','level-seed':-92345,'level-type':'minecraft:normal','difficulty':'hard','gamemode':'creative','spawn-protection':0,'view-distance':16,'simulation-distance':6,'max-players':2,'max-tick-time':180000}
(server/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in props.items())+'\n');results=server/'abyss-results'
log=(out/'server.log').open('w');p=subprocess.Popen(['java','-Xms512M','-Xmx3G','-XX:ActiveProcessorCount=4','-Dabyss.testServer=true','-jar','fabric-server-launch.jar','nogui'],cwd=server,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True)
def request(text,name,timeout=240):
 f=results/('phase7-'+name+'.json')
 if f.exists():f.unlink()
 command(server,'abyss phase7 '+text);wait_for(f.exists,timeout,text,p);return json.loads(f.read_text())
try:
 wait_for(lambda:'Done (' in (out/'server.log').read_text(),300,'Server startup',p)
 deadline=time.time()+1500
 while time.time()<deadline:
  state=request('status','status');print(state,flush=True)
  if state['stage']=='ready':break
  assert state['stage']!='unavailable';time.sleep(12)
 else:raise TimeoutError('World generation')
 request('audit warmup','warmup');time.sleep(10);audit=request('audit second','second');assert audit['pass'] and audit['layout']==75 and audit['architectural_missing_or_wrong_blocks']==0
 shutil.copy2(results/'phase7-second.json',out/'phase7-second.json')
 # Same final viewpoint; initial login starts nearby, avoiding a several-thousand-block teleport before the first frame.
 p.stdin.write(f"gamerule spawnRadius 0\nsetworldspawn {state['x']-31} {state['entry_y']+15} {state['z']+60}\nforceload add {state['x']-48} {state['z']+48} {state['x']-16} {state['z']+80}\n");p.stdin.flush()
 wait_for(lambda:'Marked' in (out/'server.log').read_text(),45,'Camera neighborhood loaded',p)
 (out/'screenshots').mkdir();attempts=[]
 for attempt in range(1,4):
  with (out/f'client-{attempt}.log').open('w') as cl:client=launch(cl,phase=7,extra_vm_args=['-Dabyss.capture.phase75.second=true'])
  cap=client/'abyss-phase7-captures';rows=json.loads((cap/'captures.json').read_text());row=next(r for r in rows if r['file']=='second-seed-overview.png');raw=cap/'second-seed-overview.png'
  shutil.copy2(raw,out/'screenshots'/f'attempt-{attempt}.png');(out/'screenshots'/f'attempt-{attempt}.json').write_text(json.dumps(rows,indent=2)+'\n')
  provenance=json.loads((client/'launch-provenance.json').read_text());assert provenance['release_sha256']==expected and not provenance['development_classes']
  im=Image.open(raw).convert('RGB');assert im.size==(1280,720)
  sky=sum(b>180 and r>90 and g>120 for r,g,b in im.getdata())/(1280*720)
  valid=row['health']==20 and row['gl_error']==0 and re.search(r'pC: 0+, pU: 0+',row['chunk_debug']) and sky<.03
  attempts.append(dict(attempt=attempt,bright_sky_fraction=sky,health=row['health'],gl_error=row['gl_error'],chunk_debug=row['chunk_debug'],preliminary_capture_pass=bool(valid)));print(attempts[-1],flush=True)
  if valid:
   shutil.copy2(raw,out/'screenshots/second-seed-overview.png');shutil.copy2(cap/'captures.json',out/'screenshots/captures.json');shutil.copy2(client/'launch-provenance.json',out/'client-launch-provenance.json');break
 else:raise AssertionError('Second scene still incomplete; retain attempts for visual inspection')
 (out/'summary.json').write_text(json.dumps(dict(status='CAPTURE_PASS',scope='Second-seed framebuffer supplement; does not replace full acceptance run 38401293992',seed=-92345,release_run='38401293992',release_jar_sha256=expected,test_fixture_source=os.environ['GITHUB_SHA'],attempts=attempts,manual_visual_review='Required before selection in public gallery',image_sha256=hashlib.sha256(raw.read_bytes()).hexdigest()),indent=2)+'\n')
finally:
 if p.poll() is None:
  command(server,'stop')
  try:p.wait(timeout=100)
  except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=30)
 log.close()
