"""Normal-overworld release client/server acceptance, two seeds and real stop/restart."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,time,urllib.request,threading
from run_experiment import ROOT,release_jar,wait_for,command
from launch_release_client import launch

def run(args):
 if not args.accept_eula:raise SystemExit('Requires --accept-eula for the isolated Minecraft acceptance server.')
 out=(ROOT/args.output).resolve();out.mkdir(parents=True,exist_ok=True)
 if (out/'provenance.json').exists():raise RuntimeError('Evidence directory already belongs to a run')
 if not args.skip_build:subprocess.run([str(ROOT/'gradlew'),'--no-daemon','build','stageTestMods'],cwd=ROOT,check=True)
 runtime=ROOT/'.runtime';runtime.mkdir(exist_ok=True);server=runtime/('phase7-server-'+str(time.time_ns()));server.mkdir()
 installer=runtime/'fabric-installer-1.0.3.jar';url='https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar'
 with urllib.request.urlopen(url,timeout=90) as r:installer.write_bytes(r.read())
 with urllib.request.urlopen(url+'.sha1',timeout=90) as r:expected=r.read().decode().split()[0]
 assert hashlib.sha1(installer.read_bytes()).hexdigest()==expected
 with (out/'installer.log').open('w') as log:subprocess.run(['java','-jar',str(installer),'server','-mcversion','1.20.1','-loader','0.14.25','-downloadMinecraft','-dir',str(server)],stdout=log,stderr=subprocess.STDOUT,check=True)
 (server/'mods').mkdir();jar=release_jar();shutil.copy2(jar,server/'mods'/jar.name);api=next((ROOT/'build/test-mods').glob('fabric-api-*.jar'));shutil.copy2(api,server/'mods'/api.name);(server/'eula.txt').write_text('eula=true\n')
 properties={'server-ip':'127.0.0.1','server-port':'25585','online-mode':'false','level-name':'phase7-main','level-seed':'74102002','level-type':'minecraft:normal','difficulty':'hard','gamemode':'survival','spawn-protection':'0','view-distance':'16','simulation-distance':'6','max-players':'2','max-tick-time':'180000'}
 def config(): (server/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in properties.items())+'\n')
 config();cmd=['java','-Xms512M','-Xmx3G','-XX:ActiveProcessorCount=4','-Dabyss.testServer=true','-jar','fabric-server-launch.jar','nogui'];results=server/'abyss-results'
 def start(name):
  log=(out/name).open('w');p=subprocess.Popen(cmd,cwd=server,stdout=log,stderr=subprocess.STDOUT);wait_for(lambda:'Done (' in (out/name).read_text(errors='replace'),300,'Normal overworld startup',p);return p,log
 def stop(p,log):
  if p.poll() is None:
   command(server,'stop')
   try:p.wait(timeout=100)
   except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=30)
  log.close()
 def request(command_text,filename,timeout=120):
  f=results/filename
  if f.exists():f.unlink()
  command(server,'abyss phase7 '+command_text);wait_for(f.exists,timeout,command_text,p)
  return json.loads(f.read_text())
 def ready():
  deadline=time.time()+1500
  while time.time()<deadline:
   s=request('status','phase7-status.json');print('WORLDGEN',s,flush=True)
   if s['stage']=='ready':return s
   if s['stage']=='unavailable':raise AssertionError(s)
   time.sleep(12)
  raise TimeoutError('Bounded worldgen did not finish')
 def audit(label):
  r=request('audit '+label,'phase7-'+label+'.json',240);shutil.copy2(results/('phase7-'+label+'.json'),out/('phase7-'+label+'.json'));return r
 p,log=start('server-first.log')
 try:
  ready();audit('warmup');time.sleep(10);first=audit('first')
  assert first.get('pass'),first
  begin=time.time()
  def stop_midride():
   marker=runtime/'release-client/abyss-phase7-captures/restart-ready.json'
   deadline=time.time()+1200
   while time.time()<deadline and p.poll() is None:
    if marker.exists() and marker.stat().st_mtime>=begin:
     command(server,'stop');return
    time.sleep(1)
  monitor=threading.Thread(target=stop_midride,daemon=True);monitor.start()
  with (out/'client.log').open('w') as cl:client=launch(cl,phase=7)
  captures=client/'abyss-phase7-captures';(out/'screenshots').mkdir(exist_ok=True)
  for f in captures.iterdir():
   if f.is_file():shutil.copy2(f,(out/'screenshots' if f.suffix=='.png' or f.name=='captures.json' else out)/f.name)
  shutil.copy2(client/'launch-provenance.json',out/'client-launch-provenance.json')
  assert (captures/'journey.json').stat().st_mtime>=begin and json.loads((captures/'journey.json').read_text())['pass']
  wait_for(lambda:(results/'phase7-intrusion.json').exists(),90,'Intrusion rejection',p)
  for f in results.glob('*.json'):shutil.copy2(f,out/f.name)
  assert json.loads((out/'phase7-intrusion.json').read_text())['removed_after_crossing']
  assert json.loads((out/'phase7-natural.json').read_text())['outside_loaded_monsters']>0
 finally:stop(p,log)
 p,log=start('server-reload.log')
 try:
  ready();audit('reload-warmup');time.sleep(10);reload=audit('reload');assert reload.get('pass'),reload
  assert first['geometry_sha256']==reload['geometry_sha256'],'Save/restart geometry changed'
  with (out/'client-reconnect.log').open('w') as cl:client=launch(cl,phase=7,extra_vm_args=['-Dabyss.capture.phase7.reconnect=true'])
  for f in (client/'abyss-phase7-reconnect').iterdir():
   if f.is_file():shutil.copy2(f,(out/'screenshots' if f.suffix=='.png' else out)/('reconnect-'+f.name if f.name=='captures.json' else f.name))
  assert json.loads((out/'recovery.json').read_text())['pass']
 finally:stop(p,log)
 properties['level-name']='phase7-second';properties['level-seed']='-92345';config()
 p,log=start('server-second-seed.log')
 try:
  ready();audit('second-warmup');time.sleep(10);second=audit('second');assert second.get('pass'),second
  assert (first['x'],first['z'])!=(second['x'],second['z'])
 finally:stop(p,log)
 summary=dict(status='TECHNICAL_PASS',minecraft='1.20.1',release_jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),seeds=[74102002,-92345],real_client=True,real_server_restart=True,natural_outside_monsters=json.loads((out/'phase7-natural.json').read_text())['outside_loaded_monsters'],aesthetic_acceptance='Pending user review of Phase 7 raw frames',limitations='Hosted Linux software OpenGL; not Windows, PCL2, target GPU, Sodium/Iris/DH or real phone')
 (out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(summary,flush=True)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--accept-eula',action='store_true');p.add_argument('--skip-build',action='store_true');p.add_argument('--output',required=True);run(p.parse_args())
