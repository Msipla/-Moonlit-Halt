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
 runtime=ROOT/'.runtime';runtime.mkdir(exist_ok=True);server=runtime/('phase75-server-'+str(time.time_ns()));server.mkdir()
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
  assert first['layout']==75 and first['water_source_cells']==25 and first['water_basin_contained']
  begin=time.time()
  def stop_midride():
   marker=runtime/'release-client/abyss-phase7-captures/restart-ready.json'
   deadline=time.time()+1200
   while time.time()<deadline and p.poll() is None:
    if marker.exists() and marker.stat().st_mtime>=begin:
     command(server,'stop');return
    time.sleep(1)
  monitor=threading.Thread(target=stop_midride,daemon=True);monitor.start()
  with (out/'client.log').open('w') as cl:client=launch(cl,phase=7,extra_vm_args=['-Dabyss.capture.phase75=true'])
  captures=client/'abyss-phase7-captures';(out/'screenshots').mkdir(exist_ok=True)
  for f in captures.iterdir():
   if f.is_file():shutil.copy2(f,(out/'screenshots' if f.suffix=='.png' or f.name=='captures.json' else out)/f.name)
  shutil.copy2(client/'launch-provenance.json',out/'client-launch-provenance.json')
  assert (captures/'dream-1.png').exists() and (captures/'dream-2.png').exists()
  assert (captures/'journey.json').stat().st_mtime>=begin and json.loads((captures/'journey.json').read_text())['pass']
  assert (captures/'restart-ready.json').stat().st_mtime>=begin and json.loads((captures/'restart-ready.json').read_text())['riding_before_stop'],'No actual mid-ride server-stop evidence'
  assert json.loads((results/'phase7-client.json').read_text())['pass'],'Client-era safety and scene audit failed'
  wait_for(lambda:(results/'phase7-intrusion.json').exists(),90,'Intrusion rejection',p)
  for f in results.glob('*.json'):shutil.copy2(f,out/f.name)
  assert json.loads((out/'phase7-intrusion.json').read_text())['removed_after_crossing']
  assert json.loads((out/'phase7-debug-permissions.json').read_text())['pass']
  assert json.loads((out/'refuge-sleep.json').read_text())['actual_refuge_bed_sleep']
  motion=json.loads((out/'cabin-motion.json').read_text())
  assert len(motion)>500 and max(r['car_x'] for r in motion)-min(r['car_x'] for r in motion)>27
  assert json.loads((out/'phase7-natural.json').read_text())['outside_loaded_monsters']>0
  access=json.loads((out/'surface-access.json').read_text());assert len(access)==3 and all(v['pass'] for v in access)
  p.wait(timeout=100)  # The monitor already requested stop; do not leave a duplicate stop command for the next process.
 finally:stop(p,log)
 p,log=start('server-reload.log')
 try:
  ready();audit('reload-warmup');time.sleep(10);reload=audit('reload');assert reload.get('pass'),reload
  assert first['geometry_sha256']==reload['geometry_sha256'],'Save/restart geometry changed'
  with (out/'client-reconnect.log').open('w') as cl:client=launch(cl,phase=7,extra_vm_args=['-Dabyss.capture.phase7.reconnect=true','-Dabyss.capture.phase75=true'])
  for f in (client/'abyss-phase7-reconnect').iterdir():
   if f.is_file():shutil.copy2(f,(out/'screenshots' if f.suffix=='.png' else out)/('reconnect-'+f.name if f.name=='captures.json' else f.name))
  assert json.loads((out/'recovery.json').read_text())['pass']
 finally:stop(p,log)
 properties['level-name']='phase7-second';properties['level-seed']='-92345';config()
 p,log=start('server-second-seed.log')
 try:
  ready();audit('second-warmup');time.sleep(10);second=audit('second');assert second.get('pass'),second
  assert (first['x'],first['z'])!=(second['x'],second['z'])
  with (out/'client-second-seed.log').open('w') as cl:client=launch(cl,phase=7,extra_vm_args=['-Dabyss.capture.phase75=true','-Dabyss.capture.phase75.second=true'])
  shutil.copy2(client/'abyss-phase7-captures/second-seed-overview.png',out/'screenshots/second-seed-overview.png')
 finally:stop(p,log)
 # Generate an authentic Phase 7 save with its shipped JAR, add a player-built
 # witness, then upgrade that exact save. Never fake the layout tag on a new cave.
 (server/'mods'/jar.name).unlink()
 old=ROOT/'artifacts/glimmering-abyss-0.7.0.jar';shutil.copy2(old,server/'mods'/old.name)
 properties['level-name']='phase7-legacy';properties['level-seed']='74102002';config()
 p,log=start('server-legacy-phase7.log')
 try:
  legacy=ready();audit('legacy-warmup');time.sleep(10)
  command(server,f"setblock {legacy['x']+45} 48 {legacy['z']+8} minecraft:gold_block")
  before=audit('legacy-before');assert before['pass']
 finally:stop(p,log)
 (server/'mods'/old.name).unlink();shutil.copy2(jar,server/'mods'/jar.name)
 p,log=start('server-legacy-upgraded.log')
 try:
  legacy=ready();audit('legacy-upgrade-warmup');time.sleep(10);after=audit('legacy-after')
  assert after['layout']==70 and after['pass'] and after['player_witness_gold'] and before['geometry_sha256']==after['geometry_sha256']
  (out/'legacy-upgrade.json').write_text(json.dumps(dict(pass_=True,old_jar_sha256=hashlib.sha256(old.read_bytes()).hexdigest(),new_jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),authentic_old_world=True,layout=after['layout'],gold_block_in_sample=True,before_geometry=before['geometry_sha256'],after_geometry=after['geometry_sha256']),indent=2)+'\n')
 finally:stop(p,log)
 summary=dict(status='TECHNICAL_PASS',minecraft='1.20.1',release_jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),seeds=[74102002,-92345],real_client=True,real_server_restart=True,natural_outside_monsters=json.loads((out/'phase7-natural.json').read_text())['outside_loaded_monsters'],aesthetic_acceptance='Pending user review of Phase 7.5 natural cave and Android riding comfort',limitations='Hosted Linux software OpenGL; not Windows, PCL2, target GPU, Sodium/Iris/DH or real phone')
 (out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(summary,flush=True)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--accept-eula',action='store_true');p.add_argument('--skip-build',action='store_true');p.add_argument('--output',required=True);run(p.parse_args())
