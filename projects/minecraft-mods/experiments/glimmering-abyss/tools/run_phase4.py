"""Verify Phase 4 with actual release JAR on both official Fabric client and server."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,time,urllib.request
from run_experiment import ROOT,release_jar,wait_for,command
from launch_release_client import launch

def run(args):
 if not args.accept_eula:raise SystemExit('Requires --accept-eula for this isolated Minecraft test server.')
 output=(ROOT/args.output).resolve()
 if output.exists() and (output/'provenance.json').exists():raise RuntimeError('Choose a new evidence directory; history cannot be overwritten')
 output.mkdir(parents=True,exist_ok=True)
 if not args.skip_build:subprocess.run([str(ROOT/'gradlew'),'--no-daemon','build','stageTestMods'],cwd=ROOT,check=True)
 runtime=ROOT/'.runtime';runtime.mkdir(exist_ok=True)
 server=runtime/('phase4-server-'+str(time.time_ns()));server.mkdir()
 installer=runtime/'fabric-installer-1.0.3.jar';url='https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar'
 with urllib.request.urlopen(url,timeout=90) as r:installer.write_bytes(r.read())
 with urllib.request.urlopen(url+'.sha1',timeout=90) as r:expected=r.read().decode().split()[0]
 if hashlib.sha1(installer.read_bytes()).hexdigest()!=expected:raise RuntimeError('Installer hash mismatch')
 with (output/'installer.log').open('w') as log:subprocess.run(['java','-jar',str(installer),'server','-mcversion','1.20.1','-loader','0.14.25','-downloadMinecraft','-dir',str(server)],stdout=log,stderr=subprocess.STDOUT,check=True)
 (server/'mods').mkdir();jar=release_jar();shutil.copy2(jar,server/'mods'/jar.name);api=next((ROOT/'build/test-mods').glob('fabric-api-*.jar'));shutil.copy2(api,server/'mods'/api.name)
 (server/'eula.txt').write_text('eula=true\n')
 properties={'server-ip':'127.0.0.1','server-port':'25585','online-mode':'false','level-name':'phase4-study','level-seed':'74102002','level-type':'minecraft:flat','generator-settings':json.dumps({'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'structures':{}}),'difficulty':'peaceful','gamemode':'creative','spawn-protection':'0','view-distance':'16','simulation-distance':'6','max-players':'2','max-tick-time':'120000'}
 (server/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in properties.items())+'\n')
 cmd=['java','-Xms512M','-Xmx1500M','-XX:ActiveProcessorCount=4','-Dabyss.testServer=true','-jar','fabric-server-launch.jar','nogui']
 def start(file):
  log=(output/file).open('w');p=subprocess.Popen(cmd,cwd=server,stdout=log,stderr=subprocess.STDOUT)
  wait_for(lambda:'Done (' in (output/file).read_text(errors='replace'),240,'Server startup',p);return p,log
 def stop(p,log):
  if p.poll() is None:
   command(server,'stop')
   try:p.wait(timeout=90)
   except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=30)
  log.close()
 p,log=start('server-first.log');results=server/'abyss-results'
 try:
  # Confirm legacy baseline in this new world before intentionally opening a bridge gate.
  for mode in ['baseline','reference','glimmer']:
   command(server,'abyss light '+mode);wait_for(lambda:not (server/'abyss-command.txt').exists(),60,'Light update',p);time.sleep(5);command(server,'abyss audit')
   path=results/f'audit-{mode}.json';wait_for(path.exists,180,'Legacy native audit',p);shutil.copy2(path,output/path.name)
  capture_start=time.time()
  with (output/'client.log').open('w') as client_log:client=launch(client_log)
  captures=client/'abyss-phase4-captures';rows=json.loads((captures/'captures.json').read_text());walk=json.loads((captures/'walk-collision.json').read_text())
  required=[captures/'captures.json',captures/'walk-collision.json',captures/'frame-intervals.json',captures/'blue-walk-interior.png']+[captures/r['file'] for r in rows]
  assert len(rows)==11 and walk['walk_and_collision_pass']
  assert all(f.exists() and f.stat().st_mtime>=capture_start for f in required),'Stale current-launch capture'
  screenshot_dir=output/'screenshots';screenshot_dir.mkdir(exist_ok=True)
  for f in required:shutil.copy2(f,screenshot_dir/f.name if f.suffix=='.png' or f.name=='captures.json' else output/f.name)
  shutil.copy2(client/'launch-provenance.json',output/'client-launch-provenance.json')
  for label in ['first','repeat']:
   f=results/f'architecture-{label}.json';wait_for(f.exists,60,'Architecture audit',p);shutil.copy2(f,output/f.name)
  shutil.copy2(results/'audit-glimmer-before-building.json',output/'audit-glimmer-before-building.json')
 finally:stop(p,log)
 # An actual server restart loads the existing save without placing the structure again.
 p,log=start('server-reload.log')
 try:
  # Keep structure chunks loaded long enough for native light to be available without a player.
  # audit first loads chunks, then audit once lighting has settled.
  command(server,'abyss building audit reload-warmup');wait_for(lambda:(results/'architecture-reload-warmup.json').exists(),90,'Reload warmup',p);time.sleep(8)
  command(server,'abyss building audit reload');wait_for(lambda:(results/'architecture-reload.json').exists(),90,'Reload audit',p);shutil.copy2(results/'architecture-reload.json',output/'architecture-reload.json')
 finally:stop(p,log)
 from phase4_validate import validate
 summary=validate(output);summary.update(minecraft='1.20.1',fabric_loader='0.14.25',fabric_api='0.85.0+1.20.1',release_jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),real_client_screenshots=12,client='Official Minecraft release client + Fabric, packaged remapped JAR; no development classes',aesthetic_acceptance='Pending user review of original Phase 4 screenshots',performance_limit='Hosted Linux software OpenGL; not target Windows/GPU/phone')
 (output/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n');print(json.dumps(summary,ensure_ascii=False,indent=2),flush=True)
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--accept-eula',action='store_true');parser.add_argument('--skip-build',action='store_true');parser.add_argument('--output',required=True);run(parser.parse_args())
