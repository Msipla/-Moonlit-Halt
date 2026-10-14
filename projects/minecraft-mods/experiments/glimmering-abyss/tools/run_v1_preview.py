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
 runtime=ROOT/'.runtime';runtime.mkdir(exist_ok=True);server=runtime/('v1-preview-server-'+str(time.time_ns()));server.mkdir()
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
  log=(out/name).open('w');p=subprocess.Popen(cmd,cwd=server,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True);wait_for(lambda:'Done (' in (out/name).read_text(errors='replace'),300,'Normal overworld startup',p);return p,log
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
 def clear_captures(name):
  target=runtime/'release-client'/name
  if target.exists():shutil.rmtree(target)
 def copy_captures(name,destination):
  target=runtime/'release-client'/name;destination.mkdir(parents=True,exist_ok=True)
  for f in target.iterdir():
   if f.is_file():shutil.copy2(f,destination/f.name)
  if (target/'failure.json').exists():raise AssertionError(json.loads((target/'failure.json').read_text()))
 p,log=start('server-first.log')
 try:
  ready();audit('warmup');time.sleep(10);first=audit('first')
  assert first.get('pass'),first
  assert first['layout']==101 and first['water_source_cells']==25 and first['water_basin_contained']
  assert first['all_template_warm_windows']==184 and first['all_template_warm_windows_native_12']==184
  if not args.mechanics_only:
   clear_captures('abyss-v1-captures')
   with (out/'client.log').open('w') as cl:client=launch(cl,phase=7,extra_vm_args=['-Dabyss.capture.phase7=false','-Dabyss.capture.v1=true'])
   captures=client/'abyss-v1-captures';(out/'screenshots').mkdir(exist_ok=True)
   for f in captures.iterdir():
    if f.is_file():shutil.copy2(f,(out/'screenshots' if f.suffix=='.png' or f.name=='captures.json' else out)/f.name)
   shutil.copy2(client/'launch-provenance.json',out/'client-launch-provenance.json')
   if (captures/'failure.json').exists():raise AssertionError(json.loads((captures/'failure.json').read_text()))
   assert json.loads((captures/'result.json').read_text())['status']=='SPATIAL_PREVIEW_CAPTURED'
   assert all(json.loads((captures/f'dream-{n}.json').read_text())['sleeping_at_capture'] for n in (1,2))
   motion=json.loads((captures/'motion.json').read_text());assert len(motion)>1000
   assert len(json.loads((captures/'walk.json').read_text()))==22
   town_walk=json.loads((captures/'settlement-walk.json').read_text());assert len(town_walk)==28
   assert all(r['health']==20 and not r['flying'] for r in town_walk)
   assert json.loads((captures/'residence-sleep.json').read_text())['real_sleep_observed']
   for label in ('sleep-house-before','sleep-house-after','house-sleep-fixture'):
    shutil.copy2(results/('phase7-'+label+'.json'),out/('phase7-'+label+'.json'))
   house_before=json.loads((out/'phase7-sleep-house-before.json').read_text());house_after=json.loads((out/'phase7-sleep-house-after.json').read_text());house_fixture=json.loads((out/'phase7-house-sleep-fixture.json').read_text())
   assert house_fixture['original_generated_bed'] and house_fixture['day_after']>house_fixture['day_before']
   assert house_after['day']>house_before['day'] and house_after['world_time']-house_before['world_time']>=95
   assert house_after['start_sleep_events']==3 and house_after['stop_sleep_events']==3 and not house_after['sleeping']
   assert house_after['dreams']==2 and house_after['has_echo'] and house_after['game_mode']=='survival'
   final=audit('client');assert final['pass']
  mode='mechanics' if args.mechanics_only else 'transport'
  clear_captures('abyss-v1-'+mode)
  marker=runtime/('release-client/abyss-v1-'+mode+'/restart-ready.json')
  def stop_midride(process):
   deadline=time.time()+300
   while time.time()<deadline and process.poll() is None:
    if marker.exists():command(server,'stop');return
    time.sleep(1)
  monitor=threading.Thread(target=stop_midride,args=(p,),daemon=True);monitor.start()
  with (out/('client-'+mode+'.log')).open('w') as cl:launch(cl,phase=7,extra_vm_args=['-Dabyss.capture.phase7=false','-Dabyss.capture.v1=true','-Dabyss.capture.v1.'+mode+'=true'])
  copy_captures('abyss-v1-'+mode,out/mode)
  assert json.loads((out/mode/'emergency.json').read_text())['pass']
  assert json.loads((out/mode/'restart-ready.json').read_text())['riding_before_stop']
  p.wait(timeout=100);monitor.join(timeout=5)
  sleep_evidence=[]
  for n in (1,2):
   for label in (f'sleep-before-{n}',f'sleep-after-{n}',f'sleep-fixture-{n}'):
    shutil.copy2(results/('phase7-'+label+'.json'),out/('phase7-'+label+'.json'))
   before=json.loads((out/f'phase7-sleep-before-{n}.json').read_text());after=json.loads((out/f'phase7-sleep-after-{n}.json').read_text());fixture=json.loads((out/f'phase7-sleep-fixture-{n}.json').read_text())
   assert fixture['day_after']>fixture['day_before']
   assert after['day']>before['day'] and after['world_time']-before['world_time']>=95
   assert after['start_sleep_events']==n and after['stop_sleep_events']==n and not after['sleeping']
   assert after['dreams']==n and after['dream_day']==after['day'] and after['game_mode']=='survival'
   assert after['has_echo']==(n==2)
   sleep_evidence.append(dict(night=n,day_before=before['day'],night_day=fixture['day_after'],wake_day=after['day'],world_ticks_elapsed=after['world_time']-before['world_time'],dreams=after['dreams'],real_start_events=after['start_sleep_events'],real_stop_events=after['stop_sleep_events']))
  (out/'sleep-verification.json').write_text(json.dumps(sleep_evidence,indent=2)+'\n')
  if args.mechanics_only:assert all(json.loads((out/mode/f'dream-{n}.json').read_text())['sleeping_at_capture'] for n in (1,2))
 finally:stop(p,log)
 p,log=start('server-reload.log')
 try:
  ready();audit('reload-warmup');time.sleep(10);reload=audit('reload');assert reload.get('pass'),reload
  assert first['geometry_sha256']==reload['geometry_sha256'],'Save/restart geometry changed'
  clear_captures('abyss-v1-reconnect')
  with (out/'client-reconnect.log').open('w') as cl:launch(cl,phase=7,extra_vm_args=['-Dabyss.capture.phase7=false','-Dabyss.capture.v1=true','-Dabyss.capture.v1.reconnect=true'])
  copy_captures('abyss-v1-reconnect',out/'reconnect')
  assert json.loads((out/'reconnect/recovery.json').read_text())['pass']
  recovered=request('status','phase7-status.json');assert recovered['unfinished_rides']==0
  (out/'reconnect/site-after-recovery.json').write_text(json.dumps(recovered,indent=2)+'\n')
 finally:stop(p,log)
 if args.mechanics_only:
  summary=dict(status='MECHANICS_TECHNICAL_PASS',release_jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),seed=74102002,real_sleep_events=2,cross_day_verified=True,survival_sleep=True,early_exit=True,midride_stop_and_reconnect=True,previous_spatial_validation='38442034346',aesthetic_accepted=False,android_tested=False)
  (out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(summary,flush=True);return
 properties['level-name']='phase7-second';properties['level-seed']='-92345';config()
 p,log=start('server-second-seed.log')
 try:
  ready();audit('second-warmup');time.sleep(10);second=audit('second');assert second.get('pass'),second
  assert (first['x'],first['z'])!=(second['x'],second['z'])
  clear_captures('abyss-v1-captures')
  with (out/'client-second-seed.log').open('w') as cl:client=launch(cl,phase=7,extra_vm_args=['-Dabyss.capture.phase7=false','-Dabyss.capture.v1=true','-Dabyss.capture.v1.second=true'])
  if (client/'abyss-v1-captures/failure.json').exists():raise AssertionError(json.loads((client/'abyss-v1-captures/failure.json').read_text()))
  assert json.loads((client/'abyss-v1-captures/result.json').read_text())['status']=='SPATIAL_PREVIEW_CAPTURED'
  shutil.copy2(client/'abyss-v1-captures/second-seed-overview.png',out/'screenshots/second-seed-overview.png')
  shutil.copy2(client/'abyss-v1-captures/captures.json',out/'screenshots/second-captures.json')
  shutil.copy2(client/'launch-provenance.json',out/'client-second-seed-provenance.json')
 finally:stop(p,log)
 # Generate authentic worlds with both prototypes and the shipped v1 preview.
 # A real console witness
 # must change the baseline fingerprint before an upgrade can claim preservation.
 for old_version,expected_layout in [('0.7.0',70),('0.7.5',75),('1.0.0-preview.1',100)]:
  (server/'mods'/jar.name).unlink()
  old=ROOT/(f'evidence/v1/runs/38491285454/glimmering-abyss-{old_version}.jar' if expected_layout==100 else f'artifacts/glimmering-abyss-{old_version}.jar');shutil.copy2(old,server/'mods'/old.name)
  properties['level-name']='legacy-'+old_version;properties['level-seed']='74102002';config()
  before_log='server-legacy-'+old_version+'.log';tag='legacy'+str(expected_layout);p,log=start(before_log)
  try:
   legacy=ready();baseline=audit(tag+'-warmup');time.sleep(10)
   witness_x=48 if expected_layout>=100 else 45
   p.stdin.write(f"forceload add {legacy['x']+witness_x} {legacy['z']+8}\n");p.stdin.flush()
   wait_for(lambda:any(k in (out/before_log).read_text() for k in ('Marked chunk','commands.forceload.added')),45,'Native witness chunk loaded',p)
   p.stdin.write(f"setblock {legacy['x']+witness_x} 48 {legacy['z']+8} minecraft:gold_block\n");p.stdin.flush()
   wait_for(lambda:any(k in (out/before_log).read_text() for k in ('Changed the block at','commands.setblock.success')),30,'Native console witness placement',p)
   before=audit(tag+'-before');assert before['pass']
   assert before['geometry_sha256']!=baseline['geometry_sha256'],'Witness was not present in the actual geometry sample'
  finally:stop(p,log)
  (server/'mods'/old.name).unlink();shutil.copy2(jar,server/'mods'/jar.name)
  p,log=start('server-upgraded-'+old_version+'.log')
  try:
   ready();audit(tag+'-upgrade-warmup');time.sleep(10);after=audit(tag+'-after')
   assert after['layout']==expected_layout and after['pass'] and after['player_witness_gold'] and before['geometry_sha256']==after['geometry_sha256']
   (out/('legacy-upgrade-'+old_version+'.json')).write_text(json.dumps(dict(pass_=True,old_jar_sha256=hashlib.sha256(old.read_bytes()).hexdigest(),new_jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),authentic_old_world=True,layout=after['layout'],gold_block_in_sample=True,before_geometry=before['geometry_sha256'],after_geometry=after['geometry_sha256']),indent=2)+'\n')
  finally:stop(p,log)
 summary=dict(status='SETTLEMENT_PREVIEW_TECHNICAL_PASS',minecraft='1.20.1',layout=101,buildings=11,release_jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),seeds=[74102002,-92345],real_client=True,peripheral_walk_legs=22,settlement_walk_legs=28,real_residence_sleep=True,real_server_restart=True,midride_stop_and_reconnect=True,early_exit=True,legacy_layouts_preserved=[70,75,100],aesthetic_acceptance='Spatial direction conditionally approved; east-wall naturalness and this settlement expansion await review of actual screenshots',limitations='Hosted Linux software OpenGL; not Windows, PCL2, target GPU, Sodium/Iris/DH or real phone')
 (out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(summary,flush=True)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--accept-eula',action='store_true');p.add_argument('--skip-build',action='store_true');p.add_argument('--output',required=True);p.add_argument('--mechanics-only',action='store_true');run(p.parse_args())
