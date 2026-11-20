const {spawn} = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {createRequire} = require('node:module');
const {snapshot} = require('./world.cjs');

const phase = process.argv[2];
assert(['smoke','A','B','C','boat'].includes(phase), 'Choose smoke, A, B, C or boat');
const root = path.resolve(__dirname, '..');
const base = path.resolve(process.env.LAB_MC20 || path.join(root, 'mc20'));
const fixtureDir = process.argv[3] || process.env.LAB_KNOWN_FAULT;
const knownFault = fixtureDir && path.resolve(fixtureDir);
assert(!knownFault || ['B','C'].includes(phase), 'Only B and C are used for the separate known-fixture repair comparison');
const boatMode = phase === 'boat' ? process.env.LAB_BOAT_MODE : undefined;
const boatResume = boatMode === 'pro' && process.env.LAB_BOAT_RESUME ? path.resolve(process.env.LAB_BOAT_RESUME) : null;
const focusedBoat = ['focused','pro'].includes(boatMode);
const model = boatMode === 'pro' ? 'deepseek-v4-pro' : 'deepseek-flash';
const repairUnowned = Boolean(knownFault && process.env.LAB_REPAIR_UNOWNED === '1');
const runLabel = knownFault ? (repairUnowned ? 'repair-unowned-' : 'repair-') + phase : boatResume ? 'boat-pro-finish' : focusedBoat ? 'boat-' + boatMode : phase;
const run = path.resolve(process.env.LAB_RUNS || path.join(__dirname, 'runs'), runLabel + '-' + Date.now());
const serverPort = Number(process.env.LAB_MC_PORT || 25576);
const rconPort = serverPort + 1;
const relayPort = serverPort + 2;
const {Rcon} = createRequire(path.join(base, 'lab.cjs'))('rcon-client');
const java = process.env.LAB_JAVA || path.join(root, 'jdk17/bin/java');
const probe = process.env.LAB_PROBE || path.join(root,'fabric-probe/probe.jar');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const box = [-24,-64,-24,24,-35,24];
const evidence = path.join(run,'model.jsonl');
const events = path.join(run, 'events.jsonl');
const world = path.join(run,'world');
const cmdAs = 'execute as LabOwner at @s run ';
const labPassword = 'isolated-local-rcon';
const stringify = value => JSON.stringify(value, (_, v) => typeof v === 'bigint' ? v.toString() : v);
let rcon, owner, relay, server, output = '';

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
  const startingWorld = knownFault || boatResume;
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
  }
  fs.writeFileSync(path.join(run,'server.properties'), [
    'level-type=minecraft\\:flat','level-seed=192834','generate-structures=false','gamemode=creative',
    'online-mode=false','enforce-secure-profile=false','server-ip=127.0.0.1',
    'server-port='+serverPort,'enable-rcon=true','rcon.port='+rconPort,'rcon.password='+labPassword,
    'spawn-protection=0','view-distance=4','simulation-distance=4','max-tick-time=-1',
    'allow-flight=true','difficulty=peaceful','spawn-animals=false','spawn-npcs=false',
    'broadcast-rcon-to-ops=false','broadcast-console-to-ops=false',
  ].join('\n')+'\n');
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
    base_url:'http://127.0.0.1:'+relayPort, reasoning_effort:'off', ctx:65536,
  }));
  const cache = path.join(base,'config/numen/server/cache/models-dev.json');
  if (fs.existsSync(cache)) fs.copyFileSync(cache,path.join(config,'cache/models-dev.json'));
  if (['B','C'].includes(phase)) {
    fs.cpSync(path.join(base,'config/numen/server/skills/createlab'),path.join(config,'skills/createlab'),{recursive:true});
  }
  fs.writeFileSync(evidence,'');
  console.log('RUN',run);
}

async function until(fn, ms) {
  const end = Date.now()+ms;
  while (Date.now()<end) {
    const value = fn();
    if (value) return value;
    if (server && server.exitCode !== null) throw new Error('Minecraft process exited with code '+server.exitCode);
    if (relay && relay.exitCode !== null) throw new Error('Relay process exited with code '+relay.exitCode);
    await delay(125);
  }
  throw new Error('No matching event within '+ms+' ms');
}

async function cmd(command) {
  const response = await rcon.send(command);
  if (!command.startsWith('save-all')) mark('rcon',{command,response});
  return response;
}

const rows = () => fs.readFileSync(evidence,'utf8').trim().split('\n').filter(Boolean).map(JSON.parse);
async function chat(text, timeout = 340000) {
  const baseCount = rows().length;
  mark('chat',{text,baseCount});
  await cmd(cmdAs+'numen chat LabBot '+text);
  try {
    return await until(() => {
      const recent = rows().slice(baseCount);
      const last = recent.at(-1);
      if (last && (!last.finish || last.finish !== 'tool_calls')) {
        return mark('agent_return',{calls:recent.length, finish:last.finish, status:last.status, content:last.content}) || last;
      }
      return null;
    }, timeout);
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
  const second=await feed('second-feed',recovery);
  mark('create_result',{initial:true,faultInjected:true,repaired:Boolean(second&&second.output>0),axisFault:{pos:coords,block:Name,originalAxis:axis,newAxis}});
}

async function main() {
  prepare();
  const relayEnv={...process.env};
  assert(relayEnv.DEEPSEEK_API_KEY,'Set DEEPSEEK_API_KEY in the runtime');
  relay=spawn('python3',[path.join(__dirname,'relay.py'),String(relayPort),evidence],{env:relayEnv,stdio:['ignore','pipe','pipe']});
  delete relayEnv.DEEPSEEK_API_KEY;
  const relayLog=fs.createWriteStream(path.join(run,'relay.log'));
  let ready=false;
  relay.stdout.on('data',data=>{relayLog.write(data);if(data.toString().includes('relay ready')) ready=true;});
  relay.stderr.pipe(relayLog,{end:false});
  await until(()=>ready,5000);
  const args=['-XX:ActiveProcessorCount=4','-Xms512m','-Xmx'+(process.env.LAB_JAVA_HEAP || '1700m')];
  if(phase==='C') args.push('-Dfabric.addMods='+probe);
  args.push('-jar','server-launch.jar','nogui');
  server=spawn(java,args,{cwd:run,env:relayEnv,stdio:['pipe','pipe','pipe']});
  const log=fs.createWriteStream(path.join(run,'minecraft.log'));
  for (const stream of [server.stdout,server.stderr]) stream.on('data',data=>{log.write(data);output+=data.toString();});
  await until(()=>output.includes('scheduler heartbeat online'),120000);
  rcon=await Rcon.connect({host:'127.0.0.1',port:rconPort,password:labPassword,timeout:12000});
  owner=spawn(process.execPath,[path.join(base,'connect.cjs'),String(serverPort)],{env:relayEnv,stdio:'ignore'});
  await until(()=>output.includes('LabOwner joined the game'),22000);
  await cmd('op LabOwner');
  await cmd('gamerule doMobSpawning false');
  await cmd('gamerule doWeatherCycle false');
  await cmd('gamerule doDaylightCycle false');
  await cmd('setworldspawn 0 -60 0');
  await cmd('worldborder center 0 0');
  await cmd('worldborder set 42');
  await cmd('tp LabOwner 0.5 -59 15.5');
  await cmd(cmdAs+'numen player summon LabBot');
  await until(()=>output.includes('LabBot joined the game'),20000);
  await cmd('gamemode creative LabBot');
  await cmd(cmdAs+'numen permission mode LabBot bypass');
  await cmd('tp LabBot 0.5 -59 11.5');
  await cmd(cmdAs+'numen companion LabBot profile lab:deepseek');
  if (phase==='smoke') {
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
