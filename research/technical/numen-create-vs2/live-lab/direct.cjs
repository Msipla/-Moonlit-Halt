const assert = require('node:assert/strict');
const {spawn, spawnSync, execFileSync} = require('node:child_process');
const {randomUUID} = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const {createRequire} = require('node:module');
const {setTimeout: wait} = require('node:timers/promises');

const base = path.resolve(process.env.LAB_MC20 || path.join(__dirname, '../mc20'));
process.env.LAB_MC20 = base;
const {snapshot} = require('./world.cjs');
const {Rcon} = createRequire(path.join(base, 'lab.cjs'))('rcon-client');
const jar = path.join(base, 'mods/create.jar');
const java = process.env.LAB_JAVA || path.resolve(base, '../jdk17/bin/java');
const port = Number(process.env.LAB_MC_PORT || 25586);
assert(Number.isInteger(port) && port >= 1024 && port < 65535);
const ground = -60; // Local height 0 sits immediately above the flat grass.
const name = process.argv[2];
const nativeLine = ['line','stone-line','repair','repair-logistics','ports-check'].includes(name);
const height = nativeLine?11:7;
const origin = name==='ship'?8:0;
const box = [origin-10, ground - 1, origin-10, origin+10, ground + height + 1, origin+10];
const compact = (value) => JSON.stringify(value, (_, v) => typeof v === 'bigint' ? v.toString() : v);
const readJar = (member) => execFileSync('unzip', ['-p', jar, member], {encoding:'utf8'});

const line = require('./line.cjs');
const ship = require('./ship.cjs');
const ponder = nativeLine ? require('./ponder.cjs').sources(jar) : null;
const lineTarget = name==='stone-line' ? line.stone : line.target;
const blocks = [
  'minecraft:air', 'minecraft:stone', 'minecraft:glass', 'minecraft:water',
  'minecraft:hopper', 'minecraft:barrel', 'create:creative_motor', 'create:shaft',
  'create:gearbox', 'create:cogwheel', 'create:large_cogwheel', 'create:encased_fan',
  'create:millstone', 'create:depot', 'create:chute', 'create:smart_chute',
];
const cases = {
  washing: {
    goal:'从空地自主设计和建成 Create 风扇水洗机。把一份小麦粉洗成面团。用设置为 32 RPM 的创造马达供能，验证实际加工，再报告结果。',
    recipe:'splashing/wheat_flour', input:'create:wheat_flour', output:'create:dough', machine:'create:encased_fan',
  },
  milling: {
    goal:'从空地自主设计和建成 Create 磨粉机，用运转的磨石把一份小麦磨成小麦粉。用设置为 32 RPM 的创造马达供能，验证实际加工，再报告结果。',
    recipe:'milling/wheat', input:'minecraft:wheat', output:'create:wheat_flour', machine:'create:millstone',
  },
};
const recipes = Object.values(cases).map(({recipe, input}) => {
  const raw = JSON.parse(readJar('data/create/recipes/'+recipe+'.json'));
  return {type:raw.type, input, results:raw.results};
});

const instructions = [
  'You design a small working machine for the user in a fresh flat Minecraft Create 6.0.8.1 Fabric lab.',
  'Return exactly one JSON object per reply. Actions:',
  '{"action":"build","blocks":[{"at":[x,y,z],"state":"namespace:block[prop=value]","rpm":32}]}',
  '{"action":"test","at":[x,y,z]} drops exactly one approved input at your chosen cell and reads actual input/output.',
  '{"action":"finish","summary":"short report based on measured RPM and actual output"} after a successful test.',
  'Keep JSON actions short and execute one concrete step per reply. Choose your own coordinates, orientations, and connections. '+(['line','stone-line'].includes(name)
    ? 'Build and verify in small batches, starting with the shared power network. Reserve open faces and cells for material flow.'
    : 'Send a compact complete design in one build batch when possible.'),
  'Each cell has exactly one final block state. List each position once. Empty cells are already air; request air only to remove a block you previously placed.',
  'Coordinates are local integers: x,z in [-7,7], y in [0,'+height+']. y=0 is the first air cell above grass. +X east, +Z south.',
  'Allowed blocks: '+blocks.join(', ')+'. State properties use the game\'s names. Only creative_motor accepts an optional signed nonzero rpm, in [-256,256].',
  'A creative motor presents its shaft on the facing side. A fan takes an axial drive from the side opposite facing. Airflow direction is controlled by sign; positive motor setting in a straight connection pushes through a fan facing the same way.',
  'Water in the fan air current switches processing to splashing. Keep the water and output contained. Test with a dropped item in the air current. A millstone takes a shaft along Y (or a side cog mesh); a dropped item can enter its top and the output inventory is read automatically.',
  'A test drops at the center of the chosen cell, 0.16 blocks above its bottom. To feed a container from above, choose the air cell just over it.',
  'Recipe facts extracted from the installed JAR: '+compact(recipes),
  'Read actual receipt values. When a check fails, change the specific faulty state or connection and re-test. For repair, observe before changing anything. Use the measured motor port and downstream connections to trace where rotation stops.',
].join('\n');

function position(at) {
  assert(Array.isArray(at) && at.length === 3 && at.every(Number.isInteger), 'at must be three integers');
  const [x,y,z] = at;
  assert(Math.abs(x)<=7 && Math.abs(z)<=7 && y>=0 && y<=height, 'cell ['+at.join(',')+'] is outside work box; allowed x,z -7..7, y 0..'+height);
  return [origin+x,ground+y,origin+z];
}

function plan(action) {
  assert(Array.isArray(action.blocks) && action.blocks.length>0 && action.blocks.length<=64, 'batch must have 1..64 cells');
  const seen = new Map();
  return action.blocks.flatMap(op => {
    assert(op && typeof op === 'object', 'each block is an object');
    const at = position(op.at);
    assert(typeof op.state === 'string', 'missing block state');
    const match = /^([a-z_]+:[a-z0-9_]+)(?:\[([a-z_]+=[a-z0-9_]+(?:,[a-z_]+=[a-z0-9_]+)*)\])?$/.exec(op.state);
    assert(match && match[0]===op.state && (name==='ship'?ship.hull:blocks).includes(match[1]), 'invalid or unsupported state at ['+op.at.join(',')+']: '+op.state+'; put filter outside state');
    if (op.rpm!==undefined) {
      assert(match[1]==='create:creative_motor' && Number.isInteger(op.rpm) &&
        op.rpm!==0 && Math.abs(op.rpm)<=256, 'invalid motor setting');
    }
    if (op.filter!==undefined) {
      assert(match[1]==='create:smart_chute' && [lineTarget.intermediate,lineTarget.output,'minecraft:wheat_seeds'].includes(op.filter),
        'unsupported filter for this project');
    }
    if(op.faces!==undefined){
      assert(nativeLine && Array.isArray(op.faces) && op.faces.length>=1 && op.faces.length<=6 &&
        op.faces.every(face=>['down','up','north','south','east','west'].includes(face)), 'invalid shaft face list');
    }
    const props = Object.fromEntries((match[2] || '').split(',').filter(Boolean).map(p=>p.split('=')));
    const key = at.join();
    const previous = seen.get(key);
    if (previous) {
      assert(previous.state===op.state && previous.rpm===op.rpm && previous.filter===op.filter && compact(previous.faces)===compact(op.faces),
        'cell ['+op.at.join(',')+'] has conflicting states '+previous.state+' and '+op.state+
        '; provide one final state for that cell');
      return [];
    }
    const cell = {at, id:match[1], state:op.state, props, rpm:op.rpm,filter:op.filter,faces:op.faces};
    seen.set(key,cell);
    return [cell];
  });
}

function itemCount(state,id) {
  function count(nbt) {
    if (!nbt || typeof nbt !== 'object') return 0;
    if (nbt.id===id && Number.isInteger(nbt.Count)) return nbt.Count;
    return Object.entries(nbt).filter(([k])=>k!=='Filter').map(([,v])=>v).reduce((sum, child)=>sum+count(child),0);
  }
  return count([state.blockEntities,state.items.map(item=>item.item)]);
}

function read(state) {
  return state.blockEntities.map(e => {
    const at = [e.x,e.y,e.z];
    const block = state.blocks[at.join()];
    const overloaded = e.Network?.Stress > e.Network?.Capacity;
    return {at:[e.x-origin,e.y-ground,e.z-origin],id:block.Name,state:block.Properties, rpm:overloaded ? 0 : e.Speed,
      configuredRpm:e.ScrollValue, source:e.Source && [e.Source.X-origin,e.Source.Y-ground,e.Source.Z-origin],
      network:e.Network?.Id, stress:e.Network?.Stress, capacity:e.Network?.Capacity};
  });
}

// urllib uses the system proxy configuration in the research environment.
function request(messages, remaining) {
  const thinking = process.env.LAB_ENGINEERING_THINKING==='1';
  const input = compact({model:'deepseek-v4-pro',thinking:{type:thinking?'enabled':'disabled'},
    ...(thinking ? {reasoning_effort:'high'} : {temperature:0.2}),response_format:{type:'json_object'},
    max_tokens:thinking?10000:2500,messages});
  const client = [
    'import os, sys, urllib.request, urllib.error',
    'headers = {"Content-Type": "application/json", "Authorization": "Bearer " + os.environ["DEEPSEEK_API_KEY"]}',
    'request = urllib.request.Request("https://api.deepseek.com/chat/completions", sys.stdin.buffer.read(), headers)',
    'try:',
    '    with urllib.request.urlopen(request, timeout=205) as response: sys.stdout.buffer.write(response.read())',
    'except urllib.error.HTTPError as error: sys.exit("DeepSeek HTTP " + str(error.code))',
    'except OSError as error: sys.exit(type(error).__name__)',
  ].join('\n');
  const reply = spawnSync('python3',['-c',client],{
    input,encoding:'utf8',timeout:Math.floor(Math.min(thinking?210000:75000,remaining)),maxBuffer:2*1024*1024,
  });
  if (reply.error) throw reply.error;
  assert(reply.status===0,reply.stderr.trim() || 'DeepSeek request failed');
  return JSON.parse(reply.stdout);
}

async function run(name) {
  const target = nativeLine?lineTarget:name==='ship'?ship.target:cases[name];
  assert(target, 'Choose washing, milling, line, stone-line, repair, repair-logistics, ports-check or ship');
  assert(name==='ports-check' || process.env.DEEPSEEK_API_KEY, 'Set the authorized DeepSeek key in the environment');
  const root = path.resolve(process.env.LAB_RUNS || path.join(__dirname, 'runs'));
  fs.mkdirSync(root,{recursive:true});
  const dir = fs.mkdtempSync(path.join(root,name+'-'));
  const mark = (type,fields={}) => {
    const line = compact({type,utc:new Date().toISOString(),...fields});
    fs.appendFileSync(path.join(dir,'events.jsonl'),line+'\n');
    console.log(line);
  };
  const password = randomUUID();
  const resumeWorld=process.env.LAB_LINE_RESUME;
  assert(!resumeWorld || ['line','stone-line'].includes(name),'resume requires a previously generated line trial');
  if(resumeWorld) {
    for(const f of ['level.dat','region','entities','poi']){
      const from=path.resolve(resumeWorld,f);
      if(fs.existsSync(from)) fs.cpSync(from,path.join(dir,'world',f),{recursive:true});
    }
  }
  for (const f of ['server-launch.jar','libraries','versions','.fabric']) fs.symlinkSync(path.join(base,f),path.join(dir,f));
  fs.mkdirSync(path.join(dir,'mods'));
  for (const f of ['create.jar','fabric-api.jar']) fs.symlinkSync(path.join(base,'mods',f),path.join(dir,'mods',f));
  if(nativeLine){
    fs.symlinkSync(path.join(base,'mods','numen.jar'),path.join(dir,'mods','numen.jar'));
    fs.symlinkSync(path.resolve(process.env.LAB_GAME_BRIDGE || path.join(__dirname,'game-bridge/game-bridge.jar')),
      path.join(dir,'mods','game-bridge.jar'));
  }
  if(target.ship){
    const extra=process.env.LAB_VS_MODS;
    assert(extra,'Set LAB_VS_MODS to the approved VS runtime directory');
    for(const f of ['vs.jar','eureka.jar','kotlin.jar']) fs.symlinkSync(path.resolve(extra,f),path.join(dir,'mods',f));
    fs.symlinkSync(path.resolve(process.env.LAB_SHIP_BRIDGE || path.join(__dirname,'ship-bridge/ship-bridge.jar')),
      path.join(dir,'mods','ship-bridge.jar'));
  }
  fs.copyFileSync(path.join(base,'eula.txt'),path.join(dir,'eula.txt'));
  fs.writeFileSync(path.join(dir,'server.properties'),[
    'level-type=minecraft\\:flat','level-seed=192834','generate-structures=false','gamemode=creative',
    'online-mode=false','server-ip=127.0.0.1','server-port='+port,'enable-rcon=true',
    'rcon.port='+(port+1),'rcon.password='+password,
    'spawn-protection=0','view-distance=4','simulation-distance=4','difficulty=peaceful',
    'spawn-animals=false','spawn-npcs=false','broadcast-rcon-to-ops=false',
  ].join('\n')+'\n');
  const env = {...process.env};
  delete env.DEEPSEEK_API_KEY;
  const server = spawn(java,['-XX:ActiveProcessorCount=4','-Xms512m',target.ship?'-Xmx1550m':'-Xmx1200m',
    ...(target.ship?['-Dengineering.shipLab=true']:[]),...(nativeLine?['-Dengineering.createLab=true']:[]),'-jar','server-launch.jar','nogui'],
    {cwd:dir,env,stdio:['pipe','pipe','pipe']});
  let rcon, observer, joined = false, ready = false;
  const writable = fs.createWriteStream(path.join(dir,'minecraft.log'));
  const closed = new Promise(resolve=>server.once('close',resolve));
  server.once('error', e => mark('server_error',{message:e.code || e.name}));
  for (const stream of [server.stdout,server.stderr]) stream.on('data', data => {
    writable.write(data);
    if (/Done \([\d.]+s\)!/.test(data.toString())) ready = true;
    if (data.toString().includes('LabOwner joined the game')) joined = true;
  });
  const command = (text) => rcon.send(text);
  const save = async (label) => {
    await command('save-all flush');
    const state = snapshot(path.join(dir,'world'),box);
    if (label) fs.writeFileSync(path.join(dir,label+'.json'),compact(state)+'\n');
    return state;
  };

  const probe = async (action) => {
    const result = await command('engineering_probe '+action);
    try {
      return JSON.parse(result);
    } catch {
      throw Error('native engineering probe returned no JSON: '+result.slice(0,200));
    }
  };
  let schema;
  const localPoint = ([x,y,z]) => [x-origin,y-ground,z-origin];
  const nativeRead = async () => (await probe('site')).map(row=>({
    ...row,at:localPoint(row.at),
    ...(row.source ? {source:localPoint(row.source)} : {}),
    ...(row.adjacentLinks ? {adjacentLinks:row.adjacentLinks.map(localPoint)} : {}),
  }));

  async function build(action) {
    const ops = plan(action);
    const oriented = ops.filter(op=>op.faces!==undefined);
    if(oriented.length){
      const results=await probe('orient '+oriented.map(op=>[op.at.join(),op.state,op.faces.join()].join('|')).join(' '));
      for(let i=0;i<oriented.length;i++){
        const op=oriented[i];
        assert(results[i]?.state, 'orientation at ['+localPoint(op.at)+']: '+results[i]?.error);
        op.state=results[i].state;
        op.props=Object.fromEntries((op.state.split('[')[1]?.slice(0,-1) || '').split(',').filter(Boolean).map(p=>p.split('=')));
      }
    }
    if(nativeLine){
      const invalid = await probe('states '+[...new Set(ops.map(op=>op.state))].join(' '));
      assert(!Object.keys(invalid).length,'invalid registered block state: '+compact(invalid));
    }
    const started = performance.now();
    const responses = [];
    for (const op of ops) {
      if(nativeLine && op.id==='minecraft:hopper'){
        const edit=await probe('reorient_hopper '+op.at.join(' ')+' '+op.state);
        assert(!edit.error,edit.error);
        if(edit.existing){
          assert(edit.sameInventory,'hopper state edit replaced its inventory');
          responses.push(edit.changed?'Changed hopper direction; native inventory retained':'Hopper already matches');
          continue;
        }
      }
      const tag = op.rpm!==undefined ? '{ScrollValue:'+op.rpm+'}' :
        (op.filter===undefined ? '' : '{Filter:{id:"'+op.filter+'",Count:1b}}');
      responses.push(await command(`setblock ${op.at.join(' ')} ${op.state}${tag} replace`));
    }
    await wait(500);
    const state = await save();
    const mismatches = ops.flatMap(op=>{
      const actual = state.blocks[op.at.join()] || {Name:'minecraft:air'};
      const ok = actual.Name===op.id && Object.entries(op.props).every(([k,v])=>actual.Properties?.[k]===v);
      const entity = state.blockEntities.find(e=>[e.x,e.y,e.z].join()===op.at.join());
      if (ok && (op.rpm===undefined || entity?.ScrollValue===op.rpm) &&
          (op.filter===undefined || entity?.Filter?.id===op.filter)) return [];
      return [{at:op.at,wanted:op.state,requestedRpm:op.rpm,actual,actualRpm:entity?.ScrollValue,wantedFilter:op.filter,actualFilter:entity?.Filter?.id}];
    });
    const result={writes:ops.length, seconds:(performance.now()-started)/1000,mismatches,responses,kinetics:read(state)};
    if(nativeLine){
      result.nativePower=line.diagnose(await nativeRead(),state,ground,lineTarget);
      result.changed=responses.filter(response=>/Changed/.test(response)).length;
      result.alreadyMatch=ops.length-result.changed-mismatches.length;
    }
    return result;
  }

  async function test(local,label) {
    const [x,y,z] = position(local);
    const before = await save(label+'-before');
    const inputBefore = itemCount(before,target.input);
    const outputBefore = itemCount(before,target.output);
    if (inputBefore) return {passed:false,reason:'previous input remains in the apparatus',inputBefore};
    const cmd = `summon minecraft:item ${x+0.5} ${y+0.16} ${z+0.5} {Item:{id:"${target.input}",Count:1b},PickupDelay:32767s,Tags:["direct_lab_feed"]}`;
    assert.match(await command(cmd),/Summoned/);
    await wait(11000);
    const after = await save(label+'-after');
    const inputAfter = itemCount(after,target.input);
    const outputAfter = itemCount(after,target.output);
    const kinetics = read(after);
    const live = kinetics.some(e=>e.id===target.machine && Math.abs(e.rpm)>0);
    const configured = kinetics.some(e=>e.id==='create:creative_motor' && e.configuredRpm===32 && Math.abs(e.rpm)===32);
    const passed = inputAfter===0 && outputAfter>outputBefore && live && configured;
    if (!passed) await command('kill @e[type=minecraft:item,tag=direct_lab_feed,x=-8,y=-60,z=-8,dx=16,dy=9,dz=16]');
    return {passed,at:local,inputBefore,inputAfter,outputBefore,outputAfter,live,configured,kinetics};
  }

  try {
    const startup = performance.now();
    while (!ready && server.exitCode===null && performance.now()-startup<120000) await wait(200);
    assert(ready, 'Minecraft did not start; see minecraft.log');
    rcon = await Rcon.connect({host:'127.0.0.1',port:port+1,password,timeout:10000});
    await command('gamerule doMobSpawning false');
    if(target.ship && process.env.LAB_SHIP_OBSERVER==='1'){
      // VS can also use an active client watcher to tick the shipyard after assembly.
      observer=spawn(process.execPath,[path.join(base,'connect.cjs'),String(port)],{env,stdio:['ignore','pipe','pipe']});
      for(const stream of [observer.stdout,observer.stderr]){
        stream.on('data',data=>fs.appendFileSync(path.join(dir,'observer.log'),data));
      }
      const expires=performance.now()+20000;
      while(!joined && observer.exitCode===null && performance.now()<expires) await wait(100);
      assert(joined,'observer protocol client did not join');
      assert.match(await command(`tp LabOwner ${origin+0.5} -59 ${origin+9.5}`),/Teleported/);
    }
    await command(`forceload add ${origin-10} ${origin-10} ${origin+10} ${origin+10}`);
    for (const [x,z,X,Z] of [[-8,-8,8,-8],[-8,8,8,8],[-8,-7,-8,7],[8,-7,8,7]]) {
      await command(`fill ${x+origin} ${ground} ${z+origin} ${X+origin} ${ground+height} ${Z+origin} minecraft:barrier`);
    }
    const baseline = await save('initial');
    if(!resumeWorld) assert(Object.values(baseline.blocks).every(b=>b.Name.startsWith('minecraft:')));
    // Invalid coordinates and arbitrary block-entity data never reach a server command.
    assert.throws(()=>plan({blocks:[{at:[8,0,0],state:'minecraft:stone'}]}),/outside/);
    assert.throws(()=>plan({blocks:[{at:[0,0,0],state:'minecraft:barrel{Items:[]}'}]}),/unsupported/);

    if(nativeLine) schema=await probe('schema '+blocks.join(' '));
    if(name==='ports-check'){
      const source=process.env.LAB_FAILED_EVIDENCE || path.join(__dirname,'evidence/local-lab-feed-2026-11-21.json');
      const trace=JSON.parse(fs.readFileSync(source,'utf8')).engineeringNext.line.stagedTrial.loadReadbacks;
      const blocks=trace.map(({at,id,state,configuredRpm})=>({
        at,state:id+(state?'['+Object.entries(state).map(kv=>kv.join('=')).join(',')+']':''),
        ...(configuredRpm!==undefined ? {rpm:configuredRpm} : {}),
      }));
      await assert.rejects(build({blocks:[
        {at:[-7,0,-7],state:'minecraft:glass'},{at:[-6,0,-7],state:'create:shaft[axis=no]'},
      ]}),/invalid registered block state/);
      assert.deepEqual((await save()).blocks,baseline.blocks);
      const options=await probe('orient -7,-60,-7|create:gearbox[axis=y]|down,east -7,-60,-7|create:millstone|up');
      assert.match(options[0].state,/gearbox\[axis=z\]/);
      assert.match(options[1].error,/actual possible shaft faces: \[\[down\]\]/);
      const guide=['fan','millstone','chute','milling/wheat','splashing/wheat_flour'].map(ponder.query);
      assert(guide.slice(0,3).every(x=>x.assets.length===1));
      assert(guide.slice(3).every(x=>x.results?.length));
      const receipt=await build({blocks});
      assert.deepEqual(receipt.mismatches,[]);
      const {loads,cutPairs}=receipt.nativePower;
      assert(loads.some(x=>x.id==='create:millstone' && Math.abs(x.rpm)===64));
      assert(loads.some(x=>x.id==='create:encased_fan' && x.rpm===0));
      assert(cutPairs.some(x=>x.at.join()==='0,4,0' && x.other.join()==='1,4,0'));
      assert(cutPairs.some(x=>x.at.join()==='5,4,0' && x.other.join()==='6,4,0'));
      mark('ports_check',{passed:true,fixture:'line-9oaB2e last persisted model build',schema,options,guide,invalidBatchWorldUnchanged:true,receipt});
      return;
    }

    async function conduct(goal, stage, pending) {
      const start = performance.now();
      const elapsed = () => (performance.now()-start)/1000;
      mark('start',{name,stage,dir,goal,recipe:target.recipe,manualInterventions:0,
        knowledge:recipes,model:'deepseek-v4-pro',thinking:process.env.LAB_ENGINEERING_THINKING==='1',geometry:resumeWorld?'copy of prior model line':'contained fresh flat world',continued:Boolean(resumeWorld)});
      const guidance = target.ship ? ship.instructions :
        target.line ? [instructions,...line.knowledge,
          'Requested recipe facts from this Create JAR: '+compact(target.recipes.map(ponder.recipe)),
          'The two required processing loads are '+target.loads.join(' and ')+'.',
          'Legal properties read from this running game: '+compact(schema),
          'For shaft-driven blocks, use optional faces:[...] on each build cell: write the directions that must connect on that cell. When faces is present, the requested contacts take precedence over axis/facing. The selected legal state is reported. A corner gearbox can accept a Y shaft from below and a horizontal shaft to the side. Face directions are from each block toward its neighbor. A fan shaft face is not its air outlet. You choose the entire layout and required faces; the tool selects a legal block orientation.',
          'Native power shows each physical shaft face, actual RPM and source. adjacentLinks also includes cog connections. On a power cut, fix the first live-to-dead adjacency before changing more distant devices.',
          'Look up a Ponder teaching scene or actual recipe using {"action":"guide","topic":"fan|millstone|chute|milling/wheat|splashing/wheat_flour"}. Verify with world observations.',
        ].join('\n') : instructions;
      const messages = [{role:'system',content:guidance},{role:'user',content:goal}];
      if(resumeWorld && stage!=='repair'){
        const existing=await save();
        const observed=line.diagnose(await nativeRead(),existing,ground,target);
        messages.push({role:'user',content:'Continue construction on the copied partial site from an earlier agent session. '+
          'All coordinates in this observation are local. Preserve any working power unless needed for the material path. '+
          'Build logistics first when power is shared. Current site: '+compact(line.survey(existing,ground))+
          ' Inspection: '+compact(observed)});
      }
      const lab = {save,command,read,position,count:itemCount,ground,target,pending};
      let verifiedAt, reply, motion, powerCheckpoint = Boolean(resumeWorld), stale = 0, previous = '';
      const rejections = new Map();
      const budget = process.env.LAB_ENGINEERING_THINKING==='1' ? 480 : target.line ? 300 : 240;
      for (let turn=1; turn<=(target.line?18:8) && elapsed()<budget; turn++) {
        let output;
        try {
          output=await request(messages,Math.max(1,budget*1000-(performance.now()-start)));
        } catch(error) {
          mark('model_error',{turn,seconds:elapsed(),error:error.code||error.message});
          break;
        }
        const choice = output.choices[0];
        const answer = choice.message.content;
        mark('model',{turn,seconds:elapsed(),finish:choice.finish_reason,usage:output.usage,answer});
        messages.push({role:'assistant',content:answer || '{}'});
        let receipt;
        try {
          assert(choice.finish_reason==='stop', 'incomplete JSON response: '+choice.finish_reason);
          const a = JSON.parse(answer);
          assert(a && typeof a==='object', 'expected one action');
          if (a.action==='build') {
            receipt = await build(a);
            verifiedAt = undefined;
          } else if (target.ship && a.action!=='finish') {
            receipt=await ship.act(a,lab);
            if(a.action==='drive' && receipt.passed) motion=a;
            if(a.action==='stop') verifiedAt=receipt.passed?motion:undefined;
          } else if (a.action==='observe' && target.line) {
            const state=await save();
            receipt={...line.survey(state,ground),nativePower:line.diagnose(await nativeRead(),state,ground,target)};
          } else if (a.action==='guide' && target.line) {
            receipt=ponder.query(a.topic);
          } else if (a.action==='test') {
            receipt = target.line ? await line.assay(a,stage+'-'+turn,lab) : await test(a.at,'model-'+turn);
            verifiedAt = receipt.passed ? a : undefined;
          } else if (a.action==='finish') {
            assert(verifiedAt && typeof a.summary==='string', 'complete a successful real test before reporting');
            reply = a.summary;
            mark('reply',{seconds:elapsed(),text:reply});
            break;
          } else {
            throw new assert.AssertionError({message:'unknown action'});
          }
        } catch (error) {
          if (!(error instanceof assert.AssertionError || error instanceof SyntaxError)) throw error;
          receipt = {error:error.message};
        }
        mark('receipt',{turn,seconds:elapsed(),receipt});
        const signature = compact([answer,receipt.passed,receipt.error]);
        stale = previous===signature ? stale+1 : 0;
        previous = signature;
        const blocked=receipt.error;
        if(blocked){
          rejections.set(blocked,(rejections.get(blocked)||0)+1);
          if(rejections.get(blocked)>=2) break;
        }else rejections.clear();
        if (stale>=2) break;
        let summary=receipt;
        if(target.line && receipt.nativePower){
          const {nativePower,changed,alreadyMatch,mismatches,cells,motorPorts,blockedTransfers}=receipt;
          summary={power:nativePower.status,loads:nativePower.loads,cutPairs:nativePower.cutPairs,
            ...(nativePower.status==='shared' ? {} : {devices:nativePower.devices}),
            changed,alreadyMatch,mismatches,blockedTransfers,cells,motorPorts,
            next:nativePower.status==='shared'
              ? stage==='repair' && blockedTransfers?.length
                ? 'Power is healthy. Here are live blocked transfers with actual neighboring cells. Adjust the one faulty outlet; test through the existing barrels.'
                : 'Power is verified. Keep the working transmission. Complete or repair material flow, then test.'
              : 'Use the first unpowered link directly beside a powered block, or an empty face of the motor toward the load.',
          };
        }
        if(stage!=='repair' && !powerCheckpoint && receipt.nativePower?.status==='shared'){
          powerCheckpoint=true;
          const status=line.survey(await save(),ground);
          mark('power_checkpoint',{seconds:elapsed(),loads:receipt.nativePower.loads});
          messages.splice(2);
          messages.push({role:'user',content:'Milestone reached: all machine loads share verified power. Continue the original task in this existing world. Skip transmission edits unless a later test proves a fault. Install material routing now, then test and finish. Current site: '+compact(status)+' Native power: '+compact(summary.loads)});
        }else{
          messages.push({role:'user',content:'Server receipt: '+compact(summary)});
        }
      }

      // No further model request is made. Refeed the same real apparatus with a fresh unit.
      const independent = verifiedAt ? (target.ship ? await ship.independent(lab,verifiedAt) : target.line ? await line.assay(verifiedAt,stage+'-independent',lab) :
          await test(verifiedAt.at,'independent')) : null;
      const final = await save(target.line||target.ship?stage+'-final':'final');
      const outside = [...new Set([...Object.keys(baseline.blocks),...Object.keys(final.blocks)])].filter(k=>{
        const [x,y,z] = k.split(',').map(Number);
        return (Math.abs(x-origin)>7 || Math.abs(z-origin)>7 || y<ground || y>ground+height) &&
          compact(baseline.blocks[k])!==compact(final.blocks[k]);
      });
      const passed = Boolean(independent?.passed && outside.length===0 && (!target.line && !target.ship || reply));
      mark('result',{stage,passed,seconds:elapsed(),reply:reply || null,modelPassed:Boolean(verifiedAt),
        independent,outside,kinetics:read(final)});
      return {passed,final};
    }

    const repairGoal='这条自动加工产线停产了。观察真实现场，定位故障，进行最小维修并保持正常部分不动。请让它再次处理一批测试原料并按实际结果报告。';
    let first;
    if(name==='repair'||name==='repair-logistics'){
      const fixture=require('./repair-fixture.cjs');
      const receipt=await build({blocks:fixture.cells});
      mark('fixture_build',{autonomous:false,receipt});
      const calibration=await line.assay(fixture.sample,'fixture-assay',
        {save,command,read,position,count:itemCount,ground,target});
      mark('fixture_assay',{autonomous:false,calibration});
      first={passed:calibration.passed,final:await save('fixture-healthy'),sample:fixture.sample};
    }else first=await conduct(target.goal,name);
    if (!first.passed) process.exitCode=1;
    if (first.passed && target.line) {
      const fault=name==='repair-logistics'
        ? await line.injectLogistics(first.sample,first.final,{position,ground,command,save,read,target})
        : await line.inject({before:first.final,ground,command,save,read,target});
      mark('fixture_fault',fault);
      const repair=await conduct(repairGoal,'repair',fault.pending);
      const difference=[...new Set([...Object.keys(first.final.blocks),...Object.keys(repair.final.blocks)])]
        .filter(k=>compact(first.final.blocks[k])!==compact(repair.final.blocks[k]));
      const settings=state=>state.blockEntities.flatMap(e=>e.ScrollValue!==undefined||e.Filter?.id
        ? [[e.x,e.y,e.z,e.ScrollValue,e.Filter?.id]] : []).sort((a,b)=>a.join().localeCompare(b.join()));
      const settingsIntact=compact(settings(first.final))===compact(settings(repair.final));
      mark('repair_integrity',{passed:repair.passed&&difference.length===0&&settingsIntact,
        changedComparedToHealthy:difference,settingsIntact});
      if(!repair.passed||difference.length||!settingsIntact) process.exitCode=1;
    }
  } finally {
    if(observer) observer.kill();
    if (rcon) rcon.end();
    if (server.exitCode===null) {
      server.stdin.end('stop\n');
      const exit = await Promise.race([closed,wait(14000,'timeout',{ref:false})]);
      if (exit==='timeout') server.kill();
    }
    writable.end();
  }
}

run(process.argv[2]).catch(error=>{
  console.error(error.name+': '+error.message);
  process.exitCode=1;
});
