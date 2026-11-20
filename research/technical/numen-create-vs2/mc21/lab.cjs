const { spawn } = require('node:child_process');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { Rcon } = require('node:module').createRequire(require('node:path').resolve('../mc20/lab.cjs'))('rcon-client');

const java = process.env.LAB_JAVA || '../jdk21/bin/java';
const world = process.env.LAB_WORLD || 'world-aero-probe';
const mods = process.env.LAB_MODS;
const args = ['-XX:ActiveProcessorCount=4', '-Xms512m', '-Xmx1700m'];
if (mods) args.push('-Dfabric.addMods=' + mods);
args.push('@libraries/net/neoforged/neoforge/21.1.247/unix_args.txt', 'nogui', '--world', world);
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
const plate=[];
for(let x=7;x<=9;x++) for(let z=1;z<=3;z++) {
  const name=(x===8&&z===2) ? "minecraft:oak_planks" : "aeronautics:levitite";
  plate.push('{name="'+name+'",pos='+cell(x,-58,z)+'}');
}
plate.push('{name="simulated:physics_assembler[face=floor,facing=north]",pos='+cell(8,-57,2)+'}');
async function main() {
  await poll(()=>output.includes('scheduler heartbeat online'),90000);
  rcon=await Rcon.connect({host:'127.0.0.1',port:25587,password:'isolated-numen-test'});
  await cmd('labspawn');
  await poll(()=>output.includes('LabBot joined the game'),15000);
  await cmd('tp LabBot 8.5 -60 5.5');
  const p=cell(8,-57,2);
  assert.match(await drive('return numen.build.place({'+plate.join(',')+'})',40000), /built 10\/10/);
  assert.match(await drive('local x=aerolab.craft.motion(); print("SUBLEVELS",#x); assert(#x==0)'), /ok .*1 call/);
  await drive('return numen.use.block('+p+', {hold=2})');
  assert.match(await drive('local x=aerolab.craft.motion(); print("SUBLEVELS_AFTER_CLICK",#x); assert(#x==0)'), /ok .*1 call/);
  const begin=await drive('local a=aerolab.craft.assemble('+p+'); print("ASSEMBLY",a.accepted,a.problem); assert(a.accepted); return aerolab.craft.motion()');
  assert.match(begin, /ASSEMBLY\s+true/);
  await delay(1550);
  const after=await drive('return aerolab.craft.motion()');
  const read=r=>({id:r.match(/id = "([^"]+)"/)?.[1], y:Number(r.match(/, y = (-?[0-9.]+)/)?.[1]), vy:Number(r.match(/vy = (-?[0-9.]+)/)?.[1])});
  const start=read(begin),end=read(after);
  console.log('SABLE MOTION',start,end,'deltaY',end.y-start.y);
  assert.ok(start.id && start.id===end.id && Math.abs(end.y-start.y)>0.35);
  console.log('AERONAUTICS STATIC BUILD, STOCK CLICK CONTROL, ASSEMBLY AND REAL MOTION PASSED');
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{
  if(owner) owner.kill();
  if(rcon) await rcon.end().catch(()=>{});
  if(server.exitCode===null){server.stdin.write('stop\n'); await Promise.race([new Promise(r=>server.once('exit',r)),delay(4500)]);}
  if(server.exitCode===null) server.kill();
  log.end();
});
