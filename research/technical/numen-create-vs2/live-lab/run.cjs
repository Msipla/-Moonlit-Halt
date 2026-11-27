const {spawn} = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {createRequire} = require('node:module');
const {createHash} = require('node:crypto');
const {snapshot} = require('./world.cjs');

const phase = process.argv[2];
const engineering = phase === 'engineering';
const staticBuilding = engineering && process.env.LAB_STATIC_BUILDING === '1';
const dshCore = engineering && process.env.LAB_ENGINEERING_SMOKE==='dsh-core';
const mobileModel=engineering && process.env.LAB_MOBILE_MODEL==='1';
const dshTrial=mobileModel && ['dsh-land','dsh-sub'].includes(process.env.LAB_MOBILE_DRILL);
const mobileResume=mobileModel && process.env.LAB_MOBILE_RESUME;
const mobile=engineering && (mobileModel || process.env.LAB_ENGINEERING_CABIN==='large');
const engineeringBounds=staticBuilding?[-8,-60,-8,7,-45,7]:mobile||dshCore?[-20,-61,-23,20,-35,21]:[-7,-60,5,8,-49,20];
const constructionSmoke = engineering && process.env.LAB_ENGINEERING_SMOKE==='batching';
const engineeringSmoke = engineering && ['1','line','dough','ship','feed','permissions','planning','overload','routing','logistics','stationary','batching','dsh-core'].includes(process.env.LAB_ENGINEERING_SMOKE);
const engineeringShip = engineering && (mobile || dshCore || process.env.LAB_ENGINEERING_SMOKE==='ship' || ['air','water'].includes(process.env.LAB_ENGINEERING_VESSEL));
assert(!staticBuilding || (!engineeringShip && !engineeringSmoke), 'Static exam needs its own non-ship model run');
const basicCheck = phase === 'basic-check';
const basicWater = phase === 'basic-water';
const basicAxis = phase === 'basic-axis';
const basicAction = basicWater || basicAxis;
const basic = basicCheck || basicAction;
const thinkSmoke = phase === 'think-smoke';
const watermill = phase === 'watermill';
const millMode = process.env.LAB_MILL_AUTONOMOUS;
const autonomousMill = watermill && ['1','side-drive','press'].includes(millMode);
const checkMillIsolation = watermill && millMode === 'check';
const checkMillMechanics = watermill && ['side-check','press-check'].includes(millMode);
const thinking = process.env.LAB_THINKING === '1';
const decisionCheckpoint = process.env.LAB_MILL_DECISION === '1';
const engineeringReceipt = process.env.LAB_ENGINEERING_RECEIPT === '1';
assert(!engineeringReceipt || (watermill && ['side-drive','press'].includes(millMode) && !decisionCheckpoint),
  'LAB_ENGINEERING_RECEIPT requires an isolated engineering model trial');
assert(!decisionCheckpoint || (watermill && millMode === 'side-drive'),
  'LAB_MILL_DECISION requires the side-drive model trial');
assert(basic || watermill || engineering || ['smoke','think-smoke','A','B','C','boat','guard-check'].includes(phase),
  'Choose smoke, think-smoke, watermill, A, B, C, boat, guard-check, basic-check, basic-water or basic-axis');
assert(!thinkSmoke || thinking, 'think-smoke requires LAB_THINKING=1');
const guardCheck = phase === 'guard-check';
const root = path.resolve(__dirname, '..');
const base = path.resolve(process.env.LAB_MC20 || path.join(root, 'mc20'));
const fixtureDir = process.argv[3] || process.env.LAB_KNOWN_FAULT;
const knownFault = fixtureDir && path.resolve(fixtureDir);
assert(!knownFault || ['B','C'].includes(phase), 'Only B and C are used for the separate known-fixture repair comparison');
const boatMode = phase === 'boat' ? process.env.LAB_BOAT_MODE : undefined;
const boatResume = boatMode === 'pro' && process.env.LAB_BOAT_RESUME ? path.resolve(process.env.LAB_BOAT_RESUME) : null;
const millResume = watermill && process.env.LAB_MILL_WORLD ? path.resolve(process.env.LAB_MILL_WORLD) : null;
const focusedBoat = ['focused','pro'].includes(boatMode);
const basicModel = basicWater && process.env.LAB_BASIC_MODEL;
assert(!basicModel || ['deepseek-flash','deepseek-v4-pro'].includes(basicModel), 'Unknown basic-water model');
const model = basicModel || (boatMode === 'pro' || watermill || engineering ? 'deepseek-v4-pro' : 'deepseek-flash');
const repairUnowned = Boolean(knownFault && process.env.LAB_REPAIR_UNOWNED === '1');
const runLabel = knownFault ? (repairUnowned ? 'repair-unowned-' : 'repair-') + phase : boatResume ? 'boat-pro-finish' : focusedBoat ? 'boat-' + boatMode : phase;
const run = path.resolve(process.env.LAB_RUNS || path.join(__dirname, 'runs'), runLabel + '-' + Date.now() + (mobile?'-'+process.pid:''));
const serverPort = Number(process.env.LAB_MC_PORT || 25576);
const rconPort = serverPort + 1;
const relayPort = serverPort + 2;
const {Rcon} = createRequire(path.join(base, 'lab.cjs'))('rcon-client');
const java = process.env.LAB_JAVA || path.join(root, 'jdk17/bin/java');
const probe = process.env.LAB_PROBE || path.join(root,'fabric-probe/probe.jar');
const skillFile = process.env.LAB_SKILL_FILE;
const scopeJar = process.env.LAB_SCOPE_JAR;
const gameBridge=process.env.LAB_GAME_BRIDGE;
assert(!engineering || gameBridge,'Set LAB_GAME_BRIDGE to the compiled game prototype');
const waterAction = watermill || process.env.LAB_BASIC_WATER_ACTION === '1';
assert(!waterAction || basicCheck || basicWater || watermill, 'The water action requires a water laboratory phase');
assert(!skillFile || basicAction || ['B','C'].includes(phase) || (watermill && millMode === 'press'),
  'LAB_SKILL_FILE requires a Skill phase');
assert(!(guardCheck || basic || watermill) || scopeJar, 'Set LAB_SCOPE_JAR to the compiled test mod');
assert(!staticBuilding || scopeJar, 'The static exam requires the existing construction scope mod for native furniture placement');
assert(!scopeJar || phase === 'C' || guardCheck || basic || watermill || staticBuilding, 'LAB_SCOPE_JAR requires a guarded phase');
const runOnlySkill = path.join('skills','createlab','create_kinetics','SKILL.md');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const box = [-24,-64,-24,24,-35,24];
const construction = [-20,-61,-20,20,-40,20];
const evidence = path.join(run,'model.jsonl');
const events = path.join(run, 'events.jsonl');
const world = path.join(run,'world');
const cmdAs = 'execute as LabOwner at @s run ';
const labPassword = 'isolated-local-rcon';
const stringify = value => JSON.stringify(value, (_, v) => typeof v === 'bigint' ? v.toString() : v);
let rcon, owner, relay, server, output = '';
const ownerReplies = [];

function mark(type, more = {}) {
  const record = stringify({utc:new Date().toISOString(),type,...more});
  fs.appendFileSync(events,record+'\n');
  console.log(phase, record);
}

function prepare() {
  fs.mkdirSync(run, {recursive:true});
  for (const name of ['server-launch.jar','mods','libraries','.fabric','versions']) {
    fs.symlinkSync(path.join(base,name),path.join(run,name));
  }
  fs.copyFileSync(path.join(base,'eula.txt'),path.join(run,'eula.txt'));
  const startingWorld = knownFault || boatResume || millResume || mobileResume;
  if (boatResume) {
    const original = path.join(boatResume,'..','baseline.json');
    if (fs.existsSync(original)) fs.copyFileSync(original,path.join(run,'original-flat.json'));
  }
  if (startingWorld) {
    fs.mkdirSync(world);
    fs.copyFileSync(path.join(startingWorld,'level.dat'),path.join(world,'level.dat'));
    for (const name of ['region','entities','poi']) {
      const source = path.join(startingWorld,name);
      if (fs.existsSync(source)) fs.cpSync(source,path.join(world,name),{recursive:true});
    }
    const placements = path.join(startingWorld,'data','numen_placed.dat');
    if (millResume && fs.existsSync(placements)) {
      fs.mkdirSync(path.join(world,'data'),{recursive:true});
      fs.copyFileSync(placements,path.join(world,'data','numen_placed.dat'));
    }
    if (mobileResume) {
      for (const name of ['data','playerdata']) {
        const source=path.join(startingWorld,name);
        if (fs.existsSync(source)) fs.cpSync(source,path.join(world,name),{recursive:true});
      }
    }
  }
  fs.writeFileSync(path.join(run,'server.properties'), [
    'level-type=minecraft\\:flat','level-seed=192834','generate-structures=false','gamemode=creative',
    'online-mode=false','enforce-secure-profile=false','server-ip=127.0.0.1',
    'server-port='+serverPort,'enable-rcon=true','rcon.port='+rconPort,'rcon.password='+labPassword,
    'spawn-protection=0','view-distance=4','simulation-distance=4','max-tick-time=-1',
    'allow-flight=true','difficulty=peaceful','spawn-animals=false','spawn-npcs=false',
    'broadcast-rcon-to-ops=false','broadcast-console-to-ops=false',
  ].join('\n')+'\n');
  if(process.env.LAB_ENGINEERING_SMOKE==='overload') {
    fs.mkdirSync(path.join(run,'config'),{recursive:true});
    fs.writeFileSync(path.join(run,'config','create-server.toml'),
      '[kinetics.stressValues.v2.capacity]\ncreative_motor = 4.0\n');
  }
  const config = path.join(run,'config/numen/server');
  for (const dir of ['provider/lab','keys','cache']) fs.mkdirSync(path.join(config,dir),{recursive:true});
  fs.writeFileSync(path.join(config,'keys/deepseek.key'),'loopback-only');
  if (repairUnowned || boatResume) {
    fs.writeFileSync(path.join(config,'server.json'),stringify({
      v:1, dailyTokenLimit:0, maxCompanions:0, hardRules:true, unknownAsOther:false,
    }));
  }
  fs.writeFileSync(path.join(config,'provider/lab/deepseek.json'), stringify({
    provider:'deepseek', protocol:'chat', model,
    base_url:'http://127.0.0.1:'+relayPort, reasoning_effort:thinking?'high':'off', ctx:65536,
    ...(process.env.LAB_OUTPUT ? {max_tokens:Number(process.env.LAB_OUTPUT)} : thinking ? {max_tokens:8192} : {}),
  }));
  const cache = path.join(base,'config/numen/server/cache/models-dev.json');
  if (fs.existsSync(cache)) fs.copyFileSync(cache,path.join(config,'cache/models-dev.json'));
  if (mobileModel || staticBuilding) {
    const skill=process.env.LAB_MOBILE_SKILL || path.join(root,'mc20/config/numen/server/skills/createlab/create_kinetics');
    fs.cpSync(skill,path.join(config,'skills/createlab/create_kinetics'),{recursive:true});
  } else if (basicAction || watermill || ['B','C'].includes(phase)) {
    fs.cpSync(path.join(base,'config/numen/server/skills/createlab'),path.join(config,'skills/createlab'),{recursive:true});
  }
  if (autonomousMill || checkMillIsolation || constructionSmoke || staticBuilding || (mobile && process.env.LAB_MOBILE_BATCH!=='off')) {
    // Minecraft offline UUID for the lab's existing owner client.
    const id = createHash('md5').update('OfflinePlayer:LabOwner').digest();
    id[6] = (id[6] & 15) | 48;
    id[8] = (id[8] & 63) | 128;
    const ownerId = id.toString('hex').replace(/^(........)(....)(....)(....)(............)$/,'$1-$2-$3-$4-$5');
    const modules = path.join(run,'config/numen/players',ownerId,'modules/my');
    fs.mkdirSync(modules,{recursive:true});
    fs.copyFileSync(path.join(__dirname,'create-survey.lua'),path.join(modules,'create_survey.lua'));
  }
  fs.writeFileSync(evidence,'');
  if (skillFile) {
    const dest = path.join(config, runOnlySkill);
    fs.copyFileSync(skillFile, dest);
    mark('skill_baseline',{skillSha256:createHash('sha256').update(fs.readFileSync(dest)).digest('hex')});
  }
  console.log('RUN',run);
}

async function until(fn, ms) {
  const end = performance.now()+ms;
  while (performance.now()<end) {
    const value = fn();
    if (value) return value;
    if (server && server.exitCode !== null) throw new Error('Minecraft process exited with code '+server.exitCode);
    if (relay && relay.exitCode !== null) throw new Error('Relay process exited with code '+relay.exitCode);
    await delay(Math.min(125, Math.max(0, end-performance.now())));
  }
  throw new Error('No matching event within '+ms+' ms');
}

async function cmd(command) {
  const response = await rcon.send(command);
  if (!command.startsWith('save-all')) mark('rcon',{command,response});
  return response;
}

const rows = () => fs.readFileSync(evidence,'utf8').trim().split('\n').filter(Boolean).map(JSON.parse);
async function isolateMillAgent() {
  const started = performance.now();
  const kicked = await cmd('kick LabBot Autonomous trial ended');
  const online = await cmd('list');
  assert.match(kicked,/\bKicked LabBot\b/i,'The game must acknowledge removal of LabBot');
  assert.match(online,/\bLabOwner\b/,'The game must return the player list');
  assert.doesNotMatch(online,/\bLabBot\b/,'LabBot is still in the assay world');
  mark('agent_isolated',{durationMs:Math.round(performance.now()-started),online});
}

async function chat(text, timeout = 340000) {
  const baseCount = rows().length;
  mark('chat',{text,baseCount});
  const command=cmdAs+'numen chat LabBot '+text;
  const started = performance.now();
  const deadline = started+timeout;
  if (watermill || engineering && Buffer.byteLength(command)>1400) {
    // A recovered plan can exceed the RCON packet limit.
    assert(!/[\r\n]/.test(command),'The lab chat must fit one console line');
    server.stdin.write(command+'\n');
  } else {
    await cmd(command);
  }
  try {
    return await until(() => {
      const recent = rows().slice(baseCount);
      // Basic cases may have a pending summon or fixture-completion event.
      if ((basicAction || thinkSmoke || watermill || engineering) && !recent.some(row=>row.context.some(item=>item.role==='user'&&item.content?.includes(text)))) {
        return null;
      }
      const last = recent.at(-1);
      // The agent can retry a transport failure inside the original experiment window.
      if (watermill && last?.error && (!last.status || last.status >= 500 || [408,409,429].includes(last.status))) {
        return null;
      }
      if ((autonomousMill || engineering) && last?.status === 200) {
        if (engineering && last.finish==='length') {
          mark('agent_truncated',{calls:recent.length,finish:last.finish,seconds:(performance.now()-started)/1000});
          return {...last,truncated:true};
        }
        if (last.error || last.finish !== 'stop' || !last.content?.trim()) return null;
        const reply = ownerReplies.find(r=>r.at>=started && r.at<=deadline
          && r.text.endsWith('<LabBot> '+last.content.trim()));
        if (!reply) return null;
        mark('agent_return',{calls:recent.length,finish:last.finish,status:last.status,content:last.content,
          deliveredSeconds:(reply.at-started)/1000});
        return last;
      }
      if (last && (!last.finish || last.finish !== 'tool_calls')) {
        return mark('agent_return',{calls:recent.length, finish:last.finish, status:last.status, content:last.content}) || last;
      }
      return null;
    }, Math.max(0,deadline-performance.now()));
  } catch (error) {
    mark('agent_timeout',{calls:rows().length-baseCount,reason:error.message});
    return null;
  }
}

async function save(label) {
  await cmd('save-all flush');
  const captured = snapshot(world,box);
  fs.writeFileSync(path.join(run,label+'.json'),stringify(captured)+'\n');
  const counts = {};
  for (const state of Object.values(captured.blocks)) counts[state.Name] = (counts[state.Name] || 0)+1;
  const create = captured.blockEntities.filter(e => e.id.startsWith('create:')).map(e => {
    const {id,x,y,z,Speed,Network,Source} = e;
    const speed = id === 'create:crushing_wheel_controller'
      ? {processingSpeed: Speed}
      : {savedRpm: Speed, effectiveRpm: effectiveRpm(e)};
    return {id,x,y,z,...speed,network:Network,source:Source};
  });
  const control = Object.entries(captured.blocks).filter(([,b])=>b.Name==='create:crushing_wheel_controller');
  mark('snapshot',{label,counts,create,control,sand:sand(captured)});
  return captured;
}

function sand(s) {
  const stored = s.blockEntities.flatMap(e=>(e.Items||[]).filter(i=>i.id==='minecraft:sand').map(i=>({at:[e.x,e.y,e.z],count:i.Count,id:e.id})));
  const loose = s.items.filter(e=>e.item.id==='minecraft:sand').map(e=>({at:e.pos,count:e.item.Count,id:'minecraft:item'}));
  return {total:[...stored,...loose].reduce((n,i)=>n+i.count,0),stored,loose};
}

function effectiveRpm(entity) {
  const {Speed,Network} = entity;
  if (typeof Speed !== 'number') return null;
  if (Network && Network.Stress > Network.Capacity) return 0;
  return Speed;
}

function controllers(s) {
  return Object.entries(s.blocks).filter(([,b])=>b.Name==='create:crushing_wheel_controller').map(([k,b])=>({
    pos:k.split(',').map(Number),valid:b.Properties?.valid==='true',facing:b.Properties?.facing,
  }));
}

function fixture(s) {
  const water = new Set(s.blockEntities
    .filter(e=>['create:water_wheel','create:large_water_wheel'].includes(e.id)&&effectiveRpm(e)&&e.Network)
    .map(e=>String(e.Network.Id)));
  const wheels=s.blockEntities.filter(e=>e.id==='create:crushing_wheel'&&effectiveRpm(e)&&water.has(String(e.Network?.Id)));
  const control=controllers(s).find(c=>c.valid);
  if (!control) return null;
  const adjacent=wheels.filter(e=>control.pos.reduce((n,p,i)=>n+Math.abs(p-[e.x,e.y,e.z][i]),0)===1);
  return adjacent.length === 2 && Math.sign(adjacent[0].Speed)!==Math.sign(adjacent[1].Speed) ? {control,wheels:adjacent} : null;
}

async function feed(label, start) {
  const working=fixture(start);
  if (!working) { mark('feed_skipped',{label,reason:'No valid driven controller'}); return null; }
  const [x,y,z]=working.control.pos;
  const hopper=start.blockEntities.find(e=>e.x===x&&e.y===y+1&&e.z===z&&e.id==='minecraft:hopper'&&!(e.Items||[]).length);
  if (!hopper && start.blocks[[x,y+1,z]]) {
    mark('feed_skipped',{label,reason:'Intake obstructed',state:start.blocks[[x,y+1,z]]});
    return null;
  }
  if (hopper) await cmd(`item replace block ${x} ${y+1} ${z} container.0 with minecraft:gravel 1`);
  else await cmd(`summon minecraft:item ${x+.5} ${y+1.15} ${z+.5} {Item:{id:"minecraft:gravel",Count:1b},PickupDelay:32767s}`);
  await delay(7000);
  const after=await save(label);
  const output=sand(after).total-sand(start).total;
  mark('feed_result',{label,output,controller:working.control.pos,input:hopper?'top hopper':'loose item'});
  return {after,output};
}

async function createTest() {
  const limits='施工区 x=-20 到 20、z=-20 到 20、地面 y=-61，地上可用到 y=-40。你在创造模式，材料可用。我来验收和投料，不会帮施工。';
  await chat('用水车建一台 Create 粉碎机，用碎石试车；机器有故障时检查并修复，最后说明故障和产物。'+limits);
  let state=await save('after-build');
  if (!fixture(state)) {
    await chat('请继续在原地完成前面的任务，确认机器确实能运行；准备好投料时告诉我。',270000);
    state=await save('after-build-retry');
  }
  const first=await feed('first-feed',state);
  if (!first || first.output<1) return mark('create_result',{initial:false,faultInjected:false,repaired:false});
  await faultAndRepair(first.after,'机器停了');
}

async function faultAndRepair(state, notice) {
  const f=fixture(state);
  const wheel=f.wheels.find(e=>e.Source && state.blocks[[e.Source.X,e.Source.Y,e.Source.Z]]?.Name==='create:shaft');
  const {x,y,z}=f.wheels[0];
  const coords=wheel ? [wheel.Source.X,wheel.Source.Y,wheel.Source.Z] : [x,y,z];
  const {Name,Properties}=state.blocks[coords];
  const axis=Properties.axis;
  await cmd('setblock '+coords.join(' ')+' minecraft:air');
  await delay(1000);
  const newAxis=axis==='y'?'x':'y';
  const changed=Object.entries({...Properties,axis:newAxis}).map(([k,v])=>k+'='+v).join(',');
  await cmd('setblock '+coords.join(' ')+' '+Name+'['+changed+']');
  await delay(1000);
  const broken=await save('fault-injected');
  if (fixture(broken)) return mark('create_result',{initial:true,faultInjected:false,repaired:false,reason:'Independent check did not confirm a stall'});
  await chat(notice);
  const recovery=await save('after-agent-repair');
  const restored = recovery.blocks[coords];
  const axisRestored = restored?.Name === Name && restored?.Properties?.axis === axis;
  const second=await feed('second-feed',recovery);
  mark('create_result',{initial:true,faultInjected:true,axisRestored,
    repaired:Boolean(axisRestored&&second&&second.output>0),axisFault:{pos:coords,block:Name,originalAxis:axis,newAxis}});
}

async function verifyScope() {
  await cmd('deop LabOwner');
  await cmd('gamerule commandModificationBlockLimit 64');
  await cmd('setblock 0 -62 1 minecraft:diamond_block');
  await cmd('setblock 21 -60 2 minecraft:diamond_block');
  await cmd('fill 0 -59 0 19 -59 4 minecraft:diamond_block');
  await cmd('fill 1 -60 5 2 -60 5 minecraft:stone');

  const native = [
    'local fill=pcall(numen.mc.run,"fill 1 -60 8 2 -60 8 minecraft:diamond_block")',
    'local execute=pcall(numen.mc.run,"execute run setblock 2 -60 9 minecraft:diamond_block")',
    'print("scope-command-denied", tostring(fill), tostring(execute))',
    'local state=numen.build.place({{name="minecraft:oak_planks",pos={x=0,y=-60,z=8}}})',
    'print("scope-native-finished",state.placed)',
  ].join(';');
  mark('scope_drive',{program:native});
  server.stdin.write('numen drive LabBot '+native+'\n');
  await until(()=>output.includes('scope-native-finished'),25000);

  // Put the body next to an accessible target just above the permitted height.
  await cmd('fill -1 -41 7 1 -41 9 minecraft:stone');
  await cmd('setblock 0 -40 8 minecraft:stone');
  await cmd('tp LabBot 0.5 -40 9.5');
  const outside='local r=numen.build.place({{name="minecraft:diamond_block",pos={x=0,y=-39,z=8}}});print("scope-outside-receipt",r.placed)';
  mark('scope_drive',{program:outside});
  server.stdin.write('numen drive LabBot '+outside+'\n');
  await until(()=>output.includes('scope-outside-receipt'),30000);
  await cmd('setblock 20 -60 -10 minecraft:water');
  await delay(6500);
  const s = await save('scope-verified');
  const id = (...p)=>s.blocks[p]?.Name;
  assert.match(output,/scope-command-denied\s+false\s+false/);
  assert.equal(id(0,-60,8),'minecraft:oak_planks');
  assert.equal(id(0,-39,8),undefined);
  assert.match(output,/scope-outside-receipt\s+0/);
  assert.equal(id(0,-62,1),'minecraft:dirt');
  assert.equal(id(21,-60,2),undefined);
  assert.equal(id(1,-60,5),'minecraft:stone');
  assert.equal(id(2,-60,5),'minecraft:stone');
  assert(!Object.values(s.blocks).some(b=>b.Name==='minecraft:diamond_block'));
  assert.equal(id(20,-60,-10),'minecraft:water');
  const flow = Object.entries(s.blocks).filter(([,block])=>block.Name==='minecraft:water');
  assert(flow.length>1,'Check flowing water as well as the source');
  assert(flow.every(([p])=>p.split(',').map(Number).every((n,i)=>n>=construction[i]&&n<=construction[i+3])));
  mark('scope_result',{passed:true,maximumCommandVolume:64,waterCellsInScope:flow.length});
}

async function drive(program, marker) {
  const offset = output.length;
  mark('basic_drive',{program});
  server.stdin.write('numen drive LabBot '+program+'\n');
  await until(()=>output.slice(offset).includes(marker),30000);
}

const axisTarget = [0,-60,9];
const luaPos = ([x,y,z]) => `{x=${x},y=${y},z=${z}}`;
const inScope = ([x,y,z]) => [x,y,z].every((n,i)=>n>=construction[i]&&n<=construction[i+3]);

async function startWrongAxis() {
  const pos = luaPos(axisTarget);
  await drive(`local r=numen.build.place({{name="create:shaft",pos=${pos},properties={axis="x"}}});print("basic-wrong-axis",r.placed,numen.scan.block(${pos}).properties.axis)`,
    'basic-wrong-axis');
  assert.match(output,/basic-wrong-axis\s+1\s+y/);
}

async function waterSample(label, baseline) {
  const time = await cmd('time query gametime');
  const tick = Number(time.match(/\d+$/)?.[0]);
  assert(Number.isInteger(tick), 'Game time not found: '+time);
  const state = await save(label);
  const water = Object.entries(state.blocks)
    .filter(([,b])=>b.Name==='minecraft:water')
    .map(([cell,b])=>({pos:cell.split(',').map(Number),level:Number(b.Properties.level)}));
  const wheels = state.blockEntities
    .filter(e=>e.id==='create:water_wheel')
    .map(e=>({pos:[e.x,e.y,e.z],rpm:effectiveRpm(e),flowScore:e.FlowScore}));
  const outside = [...new Set([...Object.keys(baseline.blocks),...Object.keys(state.blocks)])]
    .filter(cell=>!inScope(cell.split(',').map(Number)) && stringify(baseline.blocks[cell])!==stringify(state.blocks[cell]));
  const sample = {tick,wheels,water,outside};
  mark('basic_water_sample',{label,...sample});
  return sample;
}

async function waitForGameTicks(start, ticks) {
  const deadline = Date.now()+20000;
  do {
    await delay(1000);
    const time = await rcon.send('time query gametime');
    const tick = Number(time.match(/\d+$/)?.[0]);
    if (tick>=start+ticks) return;
  } while (Date.now()<deadline);
  throw new Error('Game ticks did not advance');
}

async function verifyBasicNative() {
  await startWrongAxis();
  const wrong = await save('axis-wrong-format');
  assert.equal(wrong.blocks[axisTarget]?.Properties?.axis,'y');
  const pos = luaPos(axisTarget);
  await drive(`local r=numen.build.place({{name="create:shaft[axis=x]",pos=${pos}}});print("basic-correct-axis",r.placed,numen.scan.block(${pos}).properties.axis)`,
    'basic-correct-axis');
  const correct = await save('axis-correct-format');
  assert.equal(correct.blocks[axisTarget]?.Properties?.axis,'x');

  // A dry, isolated vertical rim from the archived working water-wheel layout.
  // These administrator blocks belong only to the no-model calibration.
  await cmd('setblock 6 -57 0 create:water_wheel[facing=east]');
  await cmd('fill 5 -60 1 5 -56 1 minecraft:stone');
  await cmd('fill 7 -60 1 7 -56 1 minecraft:stone');
  await cmd('fill 6 -60 2 6 -56 2 minecraft:stone');
  await cmd('fill 6 -60 0 6 -58 0 minecraft:stone');
  await cmd('setblock 6 -58 -2 minecraft:stone');
  await cmd('tp LabBot 6.5 -57 -1.5');
  const dry = await save('calibration-dry');
  assert.equal(effectiveRpm(dry.blockEntities.find(e=>e.id==='create:water_wheel')),0);
  await drive('local r=numen.build.place({{name="minecraft:water",pos={x=6,y=-56,z=1}}});print("basic-liquid-design",r.placed)',
    'basic-liquid-design');
  assert.match(output,/basic-liquid-design\s+0/);
  await drive('numen.creative.give("minecraft:water_bucket",{count=1});numen.use.block({x=6,y=-56,z=2},{item="minecraft:water_bucket"});local b=numen.scan.block({x=6,y=-56,z=1});print("basic-native-rim",b.name,b.properties and b.properties.level)',
    'basic-native-rim');
  await delay(4000);
  const rim = await waterSample('calibration-rim',dry);
  mark('basic_native_result',{
    wrongAxis:'y',correctAxis:'x',designPlaced:0,
    sourceAtIntendedCell:rim.water.some(w=>String(w.pos)==='6,-56,1'&&w.level===0),
    wheel:rim.wheels[0],
  });
}

async function verifyWaterAction() {
  await cmd('setblock 20 -57 -10 create:water_wheel[facing=east]');
  await cmd('setblock 19 -57 -9 minecraft:stone');
  await cmd('setblock 20 -56 -9 minecraft:stone');
  const baseline = await save('action-before');
  const operation = 'createlab_water.source.place';
  const wheel = '{x=20,y=-57,z=-10}';
  const target = '{x=20,y=-56,z=-9}';
  await drive(`local o=${operation}({x=21,y=-56,z=-9},${wheel});local s=${operation}(${target},${wheel});local a=${operation}({x=19,y=-56,z=-10},${wheel});print("basic-action-rejections",o.reason,s.reason,a.reason)`,
    'basic-action-rejections');
  assert.match(output,/basic-action-rejections\s+outside_construction_scope\s+target_not_air\s+source_must_be_above_rim/);
  const rejected = await save('action-rejected');
  assert.deepEqual(rejected.blocks,baseline.blocks);
  await cmd('setblock 20 -56 -9 minecraft:air');
  await drive(`local r=${operation}(${target},${wheel});local b=numen.scan.block(${target});print("basic-action-placed",r.placed,r.reason,r.landed_at.x,r.landed_at.y,r.landed_at.z,r.rpm_now,b.name,b.properties.level)`,
    'basic-action-placed');
  assert.match(output,/basic-action-placed\s+true\s+placed\s+20\s+-56\s+-9\s+0\s+minecraft:water\s+0/);
  await delay(5000);
  const after = await waterSample('action-edge',baseline);
  const sources = after.water.filter(w=>w.level===0);
  assert.deepEqual(sources,[{pos:[20,-56,-9],level:0}]);
  assert(after.water.some(w=>w.level>0));
  assert.equal(after.outside.length,0);
  mark('basic_action_result',{passed:true,rejectedWritesUnchanged:true,sources,totalWater:after.water.length,
    wheel:after.wheels[0],outside:after.outside});
}

async function testBasic() {
  if (basicAxis) await startWrongAxis();
  const baseline = await save('baseline');
  if (basicAxis) {
    assert.equal(baseline.blocks[axisTarget]?.Properties?.axis,'y');
    await chat('把坐标 (0,-60,9) 的 Create 传动轴改成 X 轴（东西向）。请参考 create_kinetics，实际操作，再用扫描核对真实轴向并报告。只改该坐标。',150000);
    const after = await save('axis');
    const state = after.blocks[axisTarget];
    const changed = [...new Set([...Object.keys(baseline.blocks),...Object.keys(after.blocks)])]
      .filter(cell=>stringify(baseline.blocks[cell])!==stringify(after.blocks[cell]));
    mark('basic_axis_result',{pos:axisTarget,before:baseline.blocks[axisTarget],after:state,changed,
      passed:state?.Name==='create:shaft'&&state.Properties?.axis==='x'&&changed.length===1});
    return;
  }
  const pour = waterAction ? '干态 RPM 验证后，使用启用的 createlab_water.source.place 只放一个水源，水源和轮心坐标由你填写。'
    : '读到干态 RPM 后，用水桶对水道旁的墙面只放一个水源。';
  await chat('在身边的空平地让一台小型机械动力水车稳定起转。参考 create_kinetics，自行选坐标和朝向。先造干的水轮和单格落水通道，围好下方接水槽，保持接近水道的路线干燥。'+pour+'再读回实际水源、经过轮缘的流动和 RPM。无需整地或扫描整片区域，不接轴或负载。施工边界 x/z 各 -20 至 20，y=-61 至 -40。',waterAction ? 270000 : 210000);
  const first = await waterSample('water-1',baseline);
  await waitForGameTicks(first.tick,80);
  const second = await waterSample('water-2',baseline);
  await waitForGameTicks(second.tick,80);
  const last = await waterSample('water-3',baseline);
  const passed = first.wheels.length===1 && Boolean(first.wheels[0].rpm) &&
    [first,second,last].every(s=>s.outside.length===0&&s.wheels.length===1&&
      stringify(s.wheels[0])===stringify(first.wheels[0])&&
      s.water.filter(w=>w.level===0).length===1&&s.water.some(w=>w.level>0)&&
      stringify(s.water)===stringify(first.water));
  mark('basic_water_result',{passed,ticks:[first.tick,second.tick,last.tick],wheel:last.wheels[0],
    sources:last.water.filter(w=>w.level===0),flowing:last.water.filter(w=>w.level>0).length,outside:last.outside});
}

async function main() {
  if (staticBuilding) assert(process.env.DEEPSEEK_API_KEY, 'Set DEEPSEEK_API_KEY in the runtime');
  prepare();
  const relayEnv={...process.env};
  if (!(guardCheck || basicCheck || checkMillIsolation || checkMillMechanics || engineeringSmoke)) {
    assert(relayEnv.DEEPSEEK_API_KEY,'Set DEEPSEEK_API_KEY in the runtime');
    relay=spawn('python3',[path.join(__dirname,'relay.py'),String(relayPort),evidence],{env:relayEnv,stdio:['ignore','pipe','pipe']});
    const relayLog=fs.createWriteStream(path.join(run,'relay.log'));
    let ready=false;
    relay.stdout.on('data',data=>{relayLog.write(data);if(data.toString().includes('relay ready')) ready=true;});
    relay.stderr.pipe(relayLog,{end:false});
    await until(()=>ready,5000);
  }
  delete relayEnv.DEEPSEEK_API_KEY;
  const args=['-XX:ActiveProcessorCount=4','-Xms512m','-Xmx'+(process.env.LAB_JAVA_HEAP || '1700m')];
  const extraMods = phase === 'C' || basic || watermill ? [probe] : [];
  if (scopeJar) {
    extraMods.push(path.resolve(scopeJar));
    args.push('-Dcreatelab.scope='+(staticBuilding ? engineeringBounds : construction).join(','));
  }
  if (waterAction) args.push('-Dcreatelab.water=true');
  if (engineering) {
    extraMods.push(path.resolve(gameBridge));
    args.push('-Dengineering.site='+engineeringBounds.join(','));
    if (constructionSmoke && process.env.LAB_LIFECYCLE_PROBE) {
      extraMods.push(path.resolve(process.env.LAB_LIFECYCLE_PROBE));
    }
    if (engineeringShip) {
      const vs = process.env.LAB_VS_MODS;
      assert(vs,'Set LAB_VS_MODS for the engineering ship smoke test');
      extraMods.push(...['vs.jar','eureka.jar'].map(name=>path.resolve(vs,name)));
      if(dshCore||dshTrial){
        assert(process.env.LAB_DSH_MOBILE_BASE && process.env.LAB_MOBILE_POSE_PROBE,
          'The DSH core test requires the optional Mobile Base JAR and the isolated pose recorder');
        extraMods.push(path.resolve(process.env.LAB_DSH_MOBILE_BASE),path.resolve(vs,'trackwork.jar'));
      }
      args.push('-Dengineering.ships=true');
      if (process.env.LAB_MOBILE_POSE_PROBE) extraMods.push(path.resolve(process.env.LAB_MOBILE_POSE_PROBE));
    }
  }
  if (extraMods.length) args.push('-Dfabric.addMods='+extraMods.join(path.delimiter));
  args.push('-jar','server-launch.jar','nogui');
  server=spawn(java,args,{cwd:run,env:relayEnv,stdio:['pipe','pipe','pipe']});
  const log=fs.createWriteStream(path.join(run,'minecraft.log'));
  for (const stream of [server.stdout,server.stderr]) stream.on('data',data=>{log.write(data);output+=data.toString();});
  await until(()=>output.includes('scheduler heartbeat online'),120000);
  rcon=await Rcon.connect({host:'127.0.0.1',port:rconPort,password:labPassword,timeout:12000});
  const captureOwner = autonomousMill || checkMillIsolation || engineering;
  if (!engineeringShip || dshCore || dshTrial || process.env.LAB_SHIP_PROTOCOL_OWNER==='1') owner=spawn(process.execPath,[path.join(base,'connect.cjs'),String(serverPort)],{
    env:relayEnv,stdio:captureOwner ? ['ignore','ignore','ignore','ipc'] : 'ignore',
  });
  if (captureOwner && owner) owner.on('message',text=>{
    if (typeof text==='string' && text.includes('<LabBot> ')) ownerReplies.push({text,at:performance.now()});
  });
  await until(()=>output.includes('LabOwner joined the game'),engineeringShip ? 110000 : 22000);
  await cmd('op LabOwner');
  await cmd('gamerule doMobSpawning false');
  await cmd('gamerule doWeatherCycle false');
  await cmd('gamerule doDaylightCycle false');
  await cmd('setworldspawn 0 -60 0');
  await cmd('worldborder center 0 0');
  await cmd('worldborder set '+(mobile?80:42));
  await cmd('tp LabOwner 0.5 -59 15.5');
  await cmd(cmdAs+'numen player summon LabBot');
  await until(()=>output.includes('LabBot joined the game'),20000);
  await cmd('gamemode creative LabBot');
  const permissionMode=engineering ? (process.env.LAB_ENGINEERING_PERMISSION || 'bypass') : 'bypass';
  assert(['ask','bypass','observe'].includes(permissionMode),'invalid fixture permission mode');
  await cmd(cmdAs+'numen permission mode LabBot '+permissionMode);
  await cmd('tp LabBot 0.5 -59 11.5');
  if (guardCheck) {
    await save('baseline');
    await verifyScope();
    return;
  }
  if (!(basicCheck || checkMillIsolation || checkMillMechanics || engineeringSmoke)) await cmd(cmdAs+'numen companion LabBot profile lab:deepseek');
  if (scopeJar) {
    await cmd('deop LabOwner');
    await cmd('gamerule commandModificationBlockLimit 64');
  }
  if (basicCheck) return waterAction ? verifyWaterAction() : verifyBasicNative();
  if (basicAction) return testBasic();
  if (staticBuilding) return require('./game-bridge/static-building.cjs')({save,mark,chat,engineeringBounds,isolate:isolateMillAgent});
  if (engineering) return require(mobile&&!dshCore?'./game-bridge/mobile-cabin.cjs':'./game-bridge/check.cjs')({run,base,save,mark,drive,chat,cmd,engineeringBounds,
    isolate:isolateMillAgent,output:()=>output,until});
  if (watermill) {
    const mill = require('./watermill.cjs');
    const lab = {chat,cmd,save,mark,run,inScope,isolateMillAgent,
      waitForLog:(marker,ms)=>until(()=>output.includes(marker),ms)};
    if (checkMillIsolation) {
      const testReply = '<LabBot> local packet check';
      await cmd('tellraw LabOwner '+JSON.stringify({text:testReply}));
      await until(()=>ownerReplies.some(r=>r.text===testReply),3000);
      mark('owner_reply_check',{passed:true});
      return mill.checkIsolation(lab);
    }
    if (millMode==='side-check') return mill.checkSideDrive(lab);
    if (['press','press-check'].includes(millMode)) return mill.testPress(lab,checkMillMechanics);
    return autonomousMill ? mill.testAutonomous(lab,millMode==='side-drive') : mill.test(lab);
  }
  if (thinkSmoke) {
    await cmd('setblock 3 -59 3 minecraft:emerald_block');
    await cmd('setblock 4 -59 3 minecraft:gold_block');
    await chat('请在第一轮 Lua 仅读坐标 (3,-59,3) 的方块并等待回执。如果回执是绿宝石块，请在第二轮单独调用 Lua 读取 (4,-59,3)；否则读取 (5,-59,3)。向我报告两个实际上读到的方块 ID。每轮只读一个格，不施工。',90000);
    await save('thinking-smoke');
  } else if (phase==='smoke') {
    await cmd('setblock 3 -59 3 minecraft:emerald_block');
    await chat('请先实际读取坐标 (3,-59,3) 的方块，然后报告观测到的精确 ID 和坐标。不要放置或破坏任何方块。',120000);
    await save('smoke');
  } else {
    if (knownFault) {
      await cmd('tp LabOwner 14.5 -60 5.5');
      await cmd('tp LabBot 8.5 -60 5.5');
      const ready = await save('baseline');
      const first = await feed('first-feed',ready);
      if (!first || first.output<1) throw new Error('The known starting fixture did not process the control sample');
      const issue = '眼前这台用水车带动的 Create 粉碎机停了。请检查原因并修复，保持现有结构，修好后告诉我故障和修复位置，我来投碎石复验。';
      const clearance = '这是专用试验场，我已授权你修理这台机器的故障方块。';
      await faultAndRepair(first.after,issue+(repairUnowned ? clearance : ''));
    } else if (phase==='boat') {
      const baseline = await save('baseline');
      const requirement = '请在创造模式造一条静态木船壳，供我手动装配移动基地。船体外宽 7 格、纵长 13 格，船首朝北，甲板封顶。内部从南侧入口到船首要有连续、净高至少 2 格的通道，两舷有窗，舱内放一个储物桶。在舱尾留两个可从通道到达的机械安装空位，各用一个颜色不同的羊毛标记邻近地板。造完告诉我船首、入口和两个留空位坐标。施工区 x=-20 到 20、z=-20 到 20、地面 y=-61，地上可用到 y=-40。请自行设计布局。';
      const focused = '这里已经是平地。直接在地表选一块小区域造正式的船，先在一段 Lua 里自行生成设计，再按层施工。省去地形勘察、整地、试验墙和外围脚手架。北是负 Z，尺寸指船体的 X、Z 外包盒。完工后只需检查入口、几处通道和标记位置。';
      const anchor = '以现场原点附近为船体中心，脚下地表就是施工面。只生成与船有关的格，所有设计坐标必须落在提供的 X/Z 边界内。';
      const continuation = '这是上一轮同伴建了一部分的施工现场。船体主体约在 x=-3..3、z=-6..6 一带。请直接在已有结构上补齐船舱、舷窗、储物和安装位，清掉船体外的临时垫块。优先用工具直接施工，动手前省略长篇设计说明。';
      const prompt = requirement+(boatResume ? continuation : (focusedBoat ? focused : '')+(boatMode==='pro' ? anchor : ''));
      await chat(prompt,boatResume ? 480000 : focusedBoat ? 340000 : 480000);
      const state=await save('boat');
      const changed = Object.entries(state.blocks).filter(([cell,block])=>stringify(baseline.blocks[cell])!==stringify(block));
      const positions = changed.map(([cell])=>cell.split(',').map(Number));
      const span = axis => Math.max(...positions.map(p=>p[axis]))-Math.min(...positions.map(p=>p[axis]))+1;
      const names = changed.map(([,block])=>block.Name);
      const markers = new Set(names.filter(name=>name.endsWith('_wool')));
      if (!boatResume && (span(0)!==7 || span(2)!==13 || !names.includes('minecraft:barrel') ||
          markers.size<2 || names.filter(name=>name.includes('glass')).length<2)) {
        await chat('请检查并补齐前面要求的船壳、内部通道、两侧窗、储物桶和机械安装空位。完成后给出坐标。',focusedBoat ? 170000 : 300000);
        await save('boat-retry');
      }
    } else {
      await save('baseline');
      await createTest();
    }
  }
}

main().catch(error=>{console.error(error.message);if(fs.existsSync(run))mark('error',{message:error.message});process.exitCode=1;}).finally(async()=>{
  if(owner) owner.kill();
  if(rcon) await rcon.end().catch(()=>{});
  if(server && server.exitCode===null) {
    server.stdin.write('stop\n');
    await Promise.race([new Promise(resolve=>server.once('exit',resolve)),delay(8000)]);
    if(server.exitCode===null) server.kill();
  }
  if(relay) relay.kill();
});
