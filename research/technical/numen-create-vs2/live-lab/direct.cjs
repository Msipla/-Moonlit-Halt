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
const box = [-10, ground - 1, -10, 10, ground + 8, 10];
const compact = (value) => JSON.stringify(value, (_, v) => typeof v === 'bigint' ? v.toString() : v);
const readJar = (member) => execFileSync('unzip', ['-p', jar, member], {encoding:'utf8'});

const blocks = [
  'minecraft:air', 'minecraft:stone', 'minecraft:glass', 'minecraft:water',
  'minecraft:hopper', 'minecraft:barrel', 'create:creative_motor', 'create:shaft',
  'create:gearbox', 'create:cogwheel', 'create:large_cogwheel', 'create:encased_fan',
  'create:millstone', 'create:depot', 'create:chute',
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
  'Choose your own coordinates, orientations, and connections. Send a compact complete design in one build batch when possible.',
  'Each cell has exactly one final block state. List each position once. Empty cells are already air; request air only to remove a block you previously placed.',
  'Coordinates are local integers: x,z in [-7,7], y in [0,7]. y=0 is the first air cell above grass. +X east, +Z south.',
  'Allowed blocks: '+blocks.join(', ')+'. State properties use the game\'s names. Only creative_motor accepts an optional signed nonzero rpm, in [-256,256].',
  'A creative motor presents its shaft on the facing side. A fan takes an axial drive from the side opposite facing. Airflow direction is controlled by sign; positive motor setting in a straight connection pushes through a fan facing the same way.',
  'Water in the fan air current switches processing to splashing. Keep the water and output contained. Test with a dropped item in the air current. A millstone takes a shaft along Y (or a side cog mesh); a dropped item can enter its top and the output inventory is read automatically.',
  'A test drops at the center of the chosen cell, 0.16 blocks above its bottom. To feed a container from above, choose the air cell just over it.',
  'Recipe facts extracted from the installed JAR: '+compact(recipes),
  'Read actual receipt values. When a check fails, change the specific faulty state or connection and re-test.',
].join('\n');

function position(at) {
  assert(Array.isArray(at) && at.length === 3 && at.every(Number.isInteger), 'at must be three integers');
  const [x,y,z] = at;
  assert(Math.abs(x)<=7 && Math.abs(z)<=7 && y>=0 && y<=7, 'cell ['+at.join(',')+'] is outside work box; allowed x,z -7..7, y 0..7');
  return [x,ground+y,z];
}

function plan(action) {
  assert(Array.isArray(action.blocks) && action.blocks.length>0 && action.blocks.length<=64, 'batch must have 1..64 cells');
  const seen = new Map();
  return action.blocks.flatMap(op => {
    assert(op && typeof op === 'object', 'each block is an object');
    const at = position(op.at);
    assert(typeof op.state === 'string', 'missing block state');
    const match = /^([a-z_]+:[a-z0-9_]+)(?:\[([a-z_]+=[a-z0-9_]+(?:,[a-z_]+=[a-z0-9_]+)*)\])?$/.exec(op.state);
    assert(match && match[0]===op.state && blocks.includes(match[1]), 'invalid or unsupported block state');
    if (op.rpm!==undefined) {
      assert(match[1]==='create:creative_motor' && Number.isInteger(op.rpm) &&
        op.rpm!==0 && Math.abs(op.rpm)<=256, 'invalid motor setting');
    }
    const props = Object.fromEntries((match[2] || '').split(',').filter(Boolean).map(p=>p.split('=')));
    const key = at.join();
    const previous = seen.get(key);
    if (previous) {
      assert(previous.state===op.state && previous.rpm===op.rpm,
        'cell ['+op.at.join(',')+'] has conflicting states '+previous.state+' and '+op.state+
        '; provide one final state for that cell');
      return [];
    }
    const cell = {at, id:match[1], state:op.state, props, rpm:op.rpm};
    seen.set(key,cell);
    return [cell];
  });
}

function itemCount(state,id) {
  function count(nbt) {
    if (!nbt || typeof nbt !== 'object') return 0;
    if (nbt.id===id && Number.isInteger(nbt.Count)) return nbt.Count;
    return Object.values(nbt).reduce((sum, child)=>sum+count(child),0);
  }
  return count([state.blockEntities,state.items.map(item=>item.item)]);
}

function read(state) {
  return state.blockEntities.map(e => {
    const at = [e.x,e.y,e.z];
    const block = state.blocks[at.join()];
    const overloaded = e.Network?.Stress > e.Network?.Capacity;
    return {at:[e.x,e.y-ground,e.z],id:block.Name,state:block.Properties, rpm:overloaded ? 0 : e.Speed,
      configuredRpm:e.ScrollValue, source:e.Source && [e.Source.X,e.Source.Y-ground,e.Source.Z],
      network:e.Network?.Id, stress:e.Network?.Stress, capacity:e.Network?.Capacity};
  });
}

// urllib uses the system proxy configuration in the research environment.
function request(messages, remaining) {
  const input = compact({model:'deepseek-v4-pro',thinking:{type:'disabled'},response_format:{type:'json_object'},
    max_tokens:2500,temperature:0.2,messages});
  const client = [
    'import os, sys, urllib.request, urllib.error',
    'headers = {"Content-Type": "application/json", "Authorization": "Bearer " + os.environ["DEEPSEEK_API_KEY"]}',
    'request = urllib.request.Request("https://api.deepseek.com/chat/completions", sys.stdin.buffer.read(), headers)',
    'try:',
    '    with urllib.request.urlopen(request, timeout=72) as response: sys.stdout.buffer.write(response.read())',
    'except urllib.error.HTTPError as error: sys.exit("DeepSeek HTTP " + str(error.code))',
    'except OSError as error: sys.exit(type(error).__name__)',
  ].join('\n');
  const reply = spawnSync('python3',['-c',client],{
    input,encoding:'utf8',timeout:Math.floor(Math.min(75000,remaining)),maxBuffer:2*1024*1024,
  });
  if (reply.error) throw reply.error;
  assert(reply.status===0,reply.stderr.trim() || 'DeepSeek request failed');
  return JSON.parse(reply.stdout);
}

async function run(name) {
  const target = cases[name];
  assert(target, 'Choose washing or milling');
  assert(process.env.DEEPSEEK_API_KEY, 'Set the authorized DeepSeek key in the environment');
  const root = path.resolve(process.env.LAB_RUNS || path.join(__dirname, 'runs'));
  fs.mkdirSync(root,{recursive:true});
  const dir = fs.mkdtempSync(path.join(root,name+'-'));
  const mark = (type,fields={}) => {
    const line = compact({type,utc:new Date().toISOString(),...fields});
    fs.appendFileSync(path.join(dir,'events.jsonl'),line+'\n');
    console.log(line);
  };
  const password = randomUUID();
  for (const f of ['server-launch.jar','libraries','versions','.fabric']) fs.symlinkSync(path.join(base,f),path.join(dir,f));
  fs.mkdirSync(path.join(dir,'mods'));
  for (const f of ['create.jar','fabric-api.jar']) fs.symlinkSync(path.join(base,'mods',f),path.join(dir,'mods',f));
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
  const server = spawn(java,['-XX:ActiveProcessorCount=4','-Xms512m','-Xmx1200m','-jar','server-launch.jar','nogui'],
    {cwd:dir,env,stdio:['pipe','pipe','pipe']});
  let rcon, ready = false;
  const writable = fs.createWriteStream(path.join(dir,'minecraft.log'));
  const closed = new Promise(resolve=>server.once('close',resolve));
  server.once('error', e => mark('server_error',{message:e.code || e.name}));
  for (const stream of [server.stdout,server.stderr]) stream.on('data', data => {
    writable.write(data);
    if (/Done \([\d.]+s\)!/.test(data.toString())) ready = true;
  });
  const command = (text) => rcon.send(text);
  const save = async (label) => {
    await command('save-all flush');
    const state = snapshot(path.join(dir,'world'),box);
    if (label) fs.writeFileSync(path.join(dir,label+'.json'),compact(state)+'\n');
    return state;
  };

  async function build(action) {
    const ops = plan(action);
    const started = performance.now();
    const responses = [];
    for (const op of ops) {
      const tag = op.rpm===undefined ? '' : '{ScrollValue:'+op.rpm+'}';
      responses.push(await command(`setblock ${op.at.join(' ')} ${op.state}${tag} replace`));
    }
    await wait(500);
    const state = await save();
    const mismatches = ops.flatMap(op=>{
      const actual = state.blocks[op.at.join()] || {Name:'minecraft:air'};
      const ok = actual.Name===op.id && Object.entries(op.props).every(([k,v])=>actual.Properties?.[k]===v);
      const entity = state.blockEntities.find(e=>[e.x,e.y,e.z].join()===op.at.join());
      if (ok && (op.rpm===undefined || entity?.ScrollValue===op.rpm)) return [];
      return [{at:op.at,wanted:op.state,requestedRpm:op.rpm,actual,actualRpm:entity?.ScrollValue}];
    });
    return {writes:ops.length, seconds:(performance.now()-started)/1000,mismatches,responses,kinetics:read(state)};
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
    await command('forceload add -10 -10 10 10');
    for (const [x,z,X,Z] of [[-8,-8,8,-8],[-8,8,8,8],[-8,-7,-8,7],[8,-7,8,7]]) {
      await command(`fill ${x} ${ground} ${z} ${X} ${ground+7} ${Z} minecraft:barrier`);
    }
    const baseline = await save('initial');
    assert(Object.values(baseline.blocks).every(b=>b.Name.startsWith('minecraft:')));
    // Invalid coordinates and arbitrary block-entity data never reach a server command.
    assert.throws(()=>plan({blocks:[{at:[8,0,0],state:'minecraft:stone'}]}),/outside/);
    assert.throws(()=>plan({blocks:[{at:[0,0,0],state:'minecraft:barrel{Items:[]}'}]}),/unsupported/);

    const start = performance.now();
    const elapsed = () => (performance.now()-start)/1000;
    mark('start',{name,dir,goal:target.goal,recipe:target.recipe,manualInterventions:0,
      knowledge:recipes,model:'deepseek-v4-pro',geometry:'contained fresh flat world'});
    const messages = [{role:'system',content:instructions},{role:'user',content:target.goal}];
    let verifiedAt, reply, stale = 0, previous = '';
    for (let turn=1; turn<=8 && elapsed()<240; turn++) {
      const output = await request(messages,Math.max(1,240000-(performance.now()-start)));
      const choice = output.choices[0];
      const answer = choice.message.content;
      mark('model',{turn,seconds:elapsed(),finish:choice.finish_reason,usage:output.usage,answer});
      messages.push({role:'assistant',content:answer || '{}'});
      let receipt;
      try {
        const a = JSON.parse(answer);
        assert(choice.finish_reason==='stop', 'incomplete JSON response');
        assert(a && typeof a==='object', 'expected one action');
        if (a.action==='build') {
          receipt = await build(a);
          verifiedAt = undefined;
        } else if (a.action==='test') {
          receipt = await test(a.at,'model-'+turn);
          verifiedAt = receipt.passed ? a.at : undefined;
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
      if (stale>=2) break;
      messages.push({role:'user',content:'Server receipt: '+compact(receipt)});
    }

    // No further model request is made. Refeed the same real apparatus with a fresh unit.
    const independent = verifiedAt ? await test(verifiedAt,'independent') : null;
    const final = await save('final');
    const outside = [...new Set([...Object.keys(baseline.blocks),...Object.keys(final.blocks)])].filter(k=>{
      const [x,y,z] = k.split(',').map(Number);
      return (Math.abs(x)>7 || Math.abs(z)>7 || y<ground || y>ground+7) &&
        compact(baseline.blocks[k])!==compact(final.blocks[k]);
    });
    const passed = Boolean(independent?.passed && outside.length===0);
    mark('result',{passed,seconds:elapsed(),reply:reply || null,modelPassed:Boolean(verifiedAt),
      independent,outside,kinetics:read(final)});
    if (!passed) process.exitCode=1;
  } finally {
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
