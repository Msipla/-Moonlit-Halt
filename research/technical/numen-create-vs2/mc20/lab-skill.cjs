const { spawn } = require('node:child_process');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { Rcon } = require('rcon-client');

const java = process.env.LAB_JAVA || '../jdk17/bin/java';
const world = process.env.LAB_WORLD || 'world-create-test';
const mods = process.env.LAB_MODS;
const args = ['-XX:ActiveProcessorCount=4', '-Xms512m', '-Xmx1700m'];
if (mods) args.push('-Dfabric.addMods=' + mods);
args.push('-jar', 'server-launch.jar', 'nogui', '--world', world);
const log = fs.createWriteStream(world + '.log', {flags:'w'});
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const server = spawn(java, args, {stdio:['pipe','pipe','pipe']});
let output = '';
for (const pipe of [server.stdout, server.stderr]) pipe.on('data', d => {output += d.toString(); log.write(d);});
let rcon, owner;
async function poll(fn, timeout=20000) {
  const expires = Date.now() + timeout;
  while (Date.now() < expires) { const v = fn(); if (v) return v; if (server.exitCode !== null) throw new Error('server exited ' + server.exitCode); await delay(100); }
  throw new Error('timed out');
}
async function cmd(s) {
  const v = await rcon.send(s);
  console.log('RCON', s, '=>', v);
  return v;
}
async function drive(s, timeout=35000) {
  const pos = output.length;
  const token = 'receipt_' + (++drive.n);
  server.stdin.write('numen drive LabBot print("' + token + '"); ' + s.replace(/\n/g,' ') + '\n');
  let ret;
  try { ret = await poll(() => output.slice(pos).includes(token) && output.slice(pos).split('\n').find(l => l.includes('LabBot:')),timeout); }
  catch(e) {
    const recent = output.slice(pos);
    console.error('DRIVE MISSING', s, recent.slice(-2500));
    throw e;
  }
  const receipt = output.slice(output.indexOf(ret, pos)).split(/\n\[/)[0].trim();
  console.log('LUA',s,'=>',receipt);
  return receipt;
}
drive.n=0;
const cell=(x,y,z)=>'{x='+x+',y='+y+',z='+z+'}';
const w0=[6,-57,0], w1=[10,-57,2], c0=[8,-57,0], c1=[8,-57,2], gap=[8,-57,1], chest=[8,-59,1];
const rotor = [ ['create:creative_motor[facing=east]',7,-59,3], ['create:mechanical_bearing[facing=east]',8,-59,3], ['minecraft:slime_block',9,-59,3], ['minecraft:oak_planks',9,-58,3] ];
async function main() {
  await poll(()=>output.includes('scheduler heartbeat online'),70000);
  rcon=await Rcon.connect({host:'127.0.0.1',port:25577,password:'isolated-numen-test'});
  owner=spawn(process.execPath,['connect.cjs'],{stdio:'ignore'});
  await poll(()=>output.includes('LabOwner joined the game'),20000);
  await cmd('op LabOwner');
  await cmd('tp LabOwner 8.5 -60 5.5');
  await cmd('execute as LabOwner at @s run numen player summon LabBot');
  await poll(()=>output.includes('LabBot joined the game'),20000);
  await cmd('gamemode creative LabBot');
  await cmd('execute as LabOwner at @s run numen permission mode LabBot bypass');
  await cmd('tp LabBot 8.5 -60 5.5');
  await cmd('tp LabOwner 15.5 -60 7.5');
  await delay(550);
  const result=await drive('local ok=createlab.machine.skill_available(); print("SKILL_AVAILABLE",ok); assert(ok)');
  assert.match(result, /SKILL_AVAILABLE\s+true/);
  console.log('SKILL REGISTRY AND PROMPT INDEX PASSED');

}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{
  if(owner) owner.kill();
  if(rcon) await rcon.end().catch(()=>{});
  if(server.exitCode===null){server.stdin.write('stop\n'); await Promise.race([new Promise(r=>server.once('exit',r)),delay(4500)]);}
  if(server.exitCode===null) server.kill();
  log.end();
});
