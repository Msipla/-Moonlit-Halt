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
const layout=[];
const put = (p, type) => layout.push('{name='+JSON.stringify(type)+',pos='+cell(...p)+'}');
put(chest,'minecraft:barrel[facing=up]');
put([8,-58,1],'minecraft:hopper[facing=down]');
put(w0,'create:water_wheel[facing=east]');
put([7,-57,0],'create:shaft[axis=x]');
put(c0,'create:crushing_wheel[axis=x]');
put(w1,'create:water_wheel[facing=west]');
put([9,-57,2],'create:shaft[axis=x]');
put(c1,'create:crushing_wheel[axis=x]');
for (const x of [6,10]) {
  for (const y of [-60,-59,-58,-57,-56]) {
    put([x-1,y,1],'minecraft:stone');
    put([x+1,y,1],'minecraft:stone');
    if(y !== -57) { put([x,y,0],'minecraft:stone'); put([x,y,2],'minecraft:stone'); }
    else put([x,y,x === 6 ? 2 : 0],'minecraft:stone');
  }
}
async function state(label) {
  console.log('\nSTATE',label);
  for(const p of [w0,w1,c0,c1,gap,chest]) await cmd('data get block '+p.join(' '));
}
async function main() {
  await poll(()=>output.includes('scheduler heartbeat online'),50000);
  rcon=await Rcon.connect({host:'127.0.0.1',port:25577,password:'isolated-numen-test'});
  if(process.env.LAB_COMPAT_ONLY) {
    await cmd('list');
    console.log('BOOT OK');
    return;
  }
  owner=spawn(process.execPath,['connect.cjs'],{stdio:'ignore'});
  await poll(()=>output.includes('LabOwner joined the game'),20000);
  await cmd('op LabOwner');
  await cmd('tp LabOwner 8.5 -60 5.5');
  await cmd('execute as LabOwner at @s run numen player summon LabBot');
  await poll(()=>output.includes('LabBot joined the game'),20000);
  await cmd('gamemode creative LabBot');
  await cmd('execute as LabOwner at @s run numen permission mode LabBot bypass');
  await cmd('tp LabBot 8.5 -60 5.5');
  await drive('return numen.status.self()');
  const built = await drive('return numen.build.place({'+layout.join(',')+'})',95000);
  assert.match(built, /built 46\/46/);
  await drive('for _, p in ipairs({'+[w0,w1,c0,c1,gap].map(p=>cell(...p)).join(',')+'}) do local b=numen.scan.block(p); print(b.name,b.properties); end; return numen.scan.container('+cell(...gap)+')');
  await state('dry');
  // A source at the top of each channel is the controlled test input.
  await cmd('setblock 6 -56 1 minecraft:water');
  await cmd('setblock 10 -56 1 minecraft:water');
  await delay(4500);
  const sample = 'local i=createlab.machine.inspect; local w=i('+cell(...w0)+').kinetics; local a=i('+cell(...c0)+').kinetics; local b=i('+cell(...c1)+').kinetics; local c=i('+cell(...gap)+').crusher; print("RPM",w.rpm,a.rpm,b.rpm,"SU",a.stress,a.capacity,"VALID",c.valid,c.facing,c.processing_speed); ';
  assert.match(await drive(sample+'assert(w.rpm==8 and a.rpm==8 and b.rpm==-8 and a.stress==64 and a.capacity==256 and not a.overstressed and c.valid and c.facing=="down")'), /ok .*4 calls/);
  async function payload(expected) {
    await cmd('summon minecraft:item 8.5 -56.9 1.5 {Item:{id:"minecraft:gravel",Count:1b},PickupDelay:80s}');
    await delay(1400);
    await cmd('data get block 8 -57 1');
    const re = new RegExp('id: "minecraft:sand"[^}]*Count: '+expected+'b|Count: '+expected+'b[^}]*id: "minecraft:sand"');
    let chestContents = '';
    for (let secs=0; secs<22 && !re.test(chestContents); secs++) {
      await delay(1000);
      chestContents=await rcon.send('data get block 8 -59 1');
    }
    console.log('BARREL',chestContents);
    assert.match(chestContents,re);
  }
  await payload(1);
  await cmd('setblock 9 -57 2 create:shaft[axis=y]');
  await delay(1000);
  assert.match(await drive(sample+'local s=numen.scan.block({x=9,y=-57,z=2}); print("SHAFT_AXIS",s.properties.axis); assert(w.rpm==8 and a.rpm==8 and b.rpm==0 and not c.valid and s.properties.axis=="y")'), /ok .*5 calls/);
  const repair = 'local i=createlab.machine.inspect; local a=i({x=10,y=-57,z=2}).kinetics; local b=i({x=8,y=-57,z=2}).kinetics; local s=numen.scan.block({x=9,y=-57,z=2}); if a.rpm~=0 and b.rpm==0 and s.name=="create:shaft" and s.properties.axis~="x" then return numen.build.place({{name="create:shaft[axis=x]",pos=s.pos}}) end; error("unexpected diagnosis")';
  assert.match(await drive(repair), /built 1\/1/);
  await delay(1000);
  assert.match(await drive(sample+'assert(a.rpm==8 and b.rpm==-8 and c.valid and c.facing=="down")'), /ok .*4 calls/);
  await payload(2);
  const result=await drive('return numen.scan.container('+cell(...chest)+')');
  assert.match(result, /sand/);
  console.log('CREATE BUILD, DIAGNOSIS, REPAIR AND TWO RECIPE OUTPUTS PASSED');
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{
  if(owner) owner.kill();
  if(rcon) await rcon.end().catch(()=>{});
  if(server.exitCode===null){server.stdin.write('stop\n'); await Promise.race([new Promise(r=>server.once('exit',r)),delay(4500)]);}
  if(server.exitCode===null) server.kill();
  log.end();
});
