const assert = require('node:assert/strict');
const {setTimeout: wait} = require('node:timers/promises');

const casing = 'minecraft:barrel';
const amount = 3;
const target = {
  goal:'从空地自主建造 Create 自动面团产线。用同一台创造马达驱动磨石和水洗风扇。用原料桶自动进料，磨石将小麦变为面粉，传输到水洗工位，再把面团自动存进另一个桶。处理至少三份小麦，期间不要人工转移物料。请用实际库存和动力读数自验。',
  recipe:'milling/wheat + splashing/wheat_flour', recipes:['milling/wheat','splashing/wheat_flour'],
  input:'minecraft:wheat', intermediate:'create:wheat_flour', output:'create:dough',
  loads:['create:millstone','create:encased_fan'], line:true,
};
const stone = {
  goal:'从空地设计并施工 Create 两段自动石料产线。用同一创造马达驱动两台磨石，分别把圆石磨成砂砾、把砂砾磨成燧石。设置原料桶和成品桶，自动转运。投料至少三份，记录两个负载实际转速及成品增量。',
  recipe:'milling/cobblestone + milling/gravel', recipes:['milling/cobblestone','milling/gravel'],
  input:'minecraft:cobblestone', intermediate:'minecraft:gravel', output:'minecraft:flint',
  loads:['create:millstone','create:millstone'], line:true,
};
const knowledge = [
  'Before writing, reserve the full material path with gravity in mind: the second process must sit lower than the first output if you use chutes and hoppers, and keep space below each machine and above its next input. Place the loads at elevations your power layout can actually reach, then verify shared power before building transfer parts. Do not resend a complete layout to change one cell.',
  'For an automated chain, test is {"action":"test","at":[input barrel xyz],"out":[finished barrel xyz]}. Separate barrels; build both and choose positions. Assay deposits 3 units of the task input in the input barrel only; it never moves intermediates.',
  'Read any uncertain site using {"action":"observe"} for actual blocks, rotations, filters and inventories. World and power are real game state.',
  'Native logistics: a hopper extracts from the inventory directly ABOVE it and pushes into the block it FACES. A hopper aimed into the side of a millstone can feed it. A millstone allows processed output to be extracted. Chutes transport down; a horizontal row of chutes will not carry items sideways.',
  'A millstone only accepts a shaft from BELOW, keeping its top free for input, or meshes with an adjacent small cog of axis=y on the same height. Do not put a shaft above a millstone. Design actual shaft contacts for each cell. A shaft only connects its opposite ends. A gearbox exposes four shaft faces on two axes. It cannot expose X, Y and Z at the same cell. Feed and branch within one plane, or turn at separate gearboxes. Two loads can branch along opposite directions on the same axis. A fan only exposes a shaft at its back.',
  'Smart-chute example fields in a block entry: "state":"create:smart_chute[powered=false]","filter":"create:dough". The filter is a separate field, not a state property. A smart chute extracts from above and sends downward, including to or from a depot. Filter before the depot to keep byproducts out and filter the finished output to avoid removing flour too soon.',
  'For water washing on a depot, prefer a sideways fan so you can keep the downward logistics independent. A sideways fan can wash items held by a depot one cell BELOW the air stream after it passes through water. Keep the active air line clear of solid blocks. Use a contained source. Leave room on both sides of named devices for feeding and power.',
];
const reduced = (value) => JSON.stringify(value);
const atKey = (p) => p.join(',');
const cardinal={east:[1,0,0],west:[-1,0,0],up:[0,1,0],down:[0,-1,0],south:[0,0,1],north:[0,0,-1]};
const shift=(at,direction)=>at.map((n,i)=>n+cardinal[direction][i]);
const local = (p,ground) => [p[0],p[1]-ground,p[2]];
const entityAt = (state,p) => state.blockEntities.find(e=>e.x===p[0]&&e.y===p[1]&&e.z===p[2]);
function stacks(e) {
  return [e?.Items, e?.InputInventory?.Items, e?.OutputInventory?.Items,
    e?.OutputBuffer?.Items, e?.HeldItem?.Item, e?.Item, e?.Stack].flat().filter(x=>x?.id && x.Count>0);
}
function survey(state,ground) {
  const motors=Object.entries(state.blocks).flatMap(([k,b])=>{
    if(b.Name!=='create:creative_motor') return [];
    const shaftFace=b.Properties?.facing;
    const path=cardinal[shaftFace];
    if(!path) return [];
    const p=k.split(',').map(Number);
    return [{motor:local(p,ground),shaftFace,sample:[1,2].map(distance=>{
      const target=p.map((coordinate,i)=>coordinate+distance*path[i]);
      const seen=state.blocks[atKey(target)];
      return {at:local(target,ground),id:seen?.Name||'minecraft:air',state:seen?.Properties,
        rpm:entityAt(state,target)?.Speed};
    })}];
  });
  const blockedTransfers=state.blockEntities.flatMap(e=>{
    const at=[e.x,e.y,e.z];
    const b=state.blocks[atKey(at)];
    if(b?.Name!=='minecraft:hopper'||!cardinal[b.Properties?.facing]) return [];
    const items=stacks(e);
    if(!items.length || state.blocks[atKey(shift(at,b.Properties.facing))]) return [];
    return [{at:local(at,ground),facing:b.Properties.facing,items,
      neighbors:Object.keys(cardinal).map(face=>({face,id:state.blocks[atKey(shift(at,face))]?.Name||'minecraft:air'}))}];
  });
  return {
    motorPorts:motors,blockedTransfers,
    cells:Object.entries(state.blocks).flatMap(([k,b])=>{
      const p=k.split(',').map(Number);
      if(p[1]<ground || b.Name==='minecraft:barrier' || b.Name==='minecraft:water' && b.Properties?.level!=='0') return [];
      const e=entityAt(state,p);
      const destination = b.Name==='minecraft:hopper' && cardinal[b.Properties?.facing]
        ? shift(p,b.Properties.facing) : null;
      return [{at:local(p,ground),id:b.Name,state:b.Properties,filter:e?.Filter?.id,items:stacks(e),
        ...(destination ? {output:{at:local(destination,ground),id:state.blocks[atKey(destination)]?.Name||'minecraft:air'}} : {})}];
    }),
  };
}

function diagnose(rows,state,ground,target) {
  const positions=new Map(rows.map(row=>[atKey(row.at),row]));
  const identification=at=>state.blocks[atKey([at[0],at[1]+ground,at[2]])]?.Name;
  const axes=[[1,0,0,'east','west'],[0,1,0,'up','down'],[0,0,1,'south','north']];
  const cutPairs=[];
  for (const a of rows) {
    for (const [x,y,z,face,opposite] of axes) {
      const b=positions.get(atKey(a.at.map((n,i)=>n+[x,y,z][i])));
      if(!b || a.adjacentLinks?.some(p=>atKey(p)===atKey(b.at))) continue;
      cutPairs.push({at:a.at,other:b.at,facesToward:[face,opposite],
        hasThoseFaces:[a.faces.includes(face),b.faces.includes(opposite)],
        powered:[a.rpm!==0,b.rpm!==0]});
    }
  }
  const devices=rows.map(r=>({...r,id:identification(r.at)}));
  const loads=devices.filter(r=>r.id==='create:creative_motor'||target.loads.includes(r.id));
  const motor=loads.find(r=>r.id==='create:creative_motor'&&r.network!==undefined&&r.rpm!==0);
  const connected=loads.filter(r=>r!==motor&&r.network===motor?.network&&r.rpm!==0);
  const shared=motor&&target.loads.every(id=>connected.filter(r=>r.id===id).length>=target.loads.filter(x=>x===id).length);
  return {status:shared?'shared':'incomplete',loads,devices,cutPairs};
}

function drive(state,read,target) {
  const devices=read(state);
  const motor=devices.find(e=>e.id==='create:creative_motor'&&Math.abs(e.rpm)>0);
  const loads = [];
  for(const id of target.loads){
    const entry=devices.find(e=>e.id===id&&!loads.includes(e));
    if(entry) loads.push(entry);
  }
  const shared=motor?.network!==undefined && loads.length===target.loads.length && loads.every(e=>e && Math.abs(e.rpm)>0 &&
    reduced(e.network)===reduced(motor.network) && e.stress<=e.capacity);
  return {shared:Boolean(shared),devices:devices.filter(e=>e.rpm!==undefined)};
}
function stock(state,p,id) {
  return stacks(entityAt(state,p)).filter(s=>s.id===id).reduce((n,s)=>n+s.Count,0);
}
async function assay(action, label, lab) {
  const target=lab.target;
  const {input,intermediate,output:product}=target;
  const a=lab.position(action.at), b=lab.position(action.out);
  assert(atKey(a)!==atKey(b), 'input and finished barrels must be separate');
  const before=await lab.save(label+'-before');
  for(const cell of [a,b]) assert(before.blocks[atKey(cell)]?.Name===casing,'expected barrel at '+atKey(cell));
  const pending=lab.pending;
  const stranded=lab.count(before,input)+lab.count(before,intermediate);
  if (pending?.out!==undefined && atKey(b)!==pending.out && stranded) {
    return {passed:false,reason:'a batch is still inside the line; keep its output barrel while repairing'};
  }
  const baseline=pending?.out===atKey(b) ? pending.baseline : stock(before,b,product);
  const driveBefore=drive(before,lab.read,target);
  if (!driveBefore.shared) return {passed:false,reason:'both loads must turn on one motor network',drive:driveBefore};
  if(!pending){
    assert(!stranded,'unexpected input already in the line');
    const cmd=`item replace block ${a.join(' ')} container.0 with ${input} ${amount}`;
    const response=await lab.command(cmd);
    assert(/Replaced/.test(response),'input barrel did not accept the sample: '+response);
    lab.pending={out:atKey(b),baseline};
  }
  let state;
  const flow=[];
  let drainedSamples=0;
  for(let i=0;i<23;i++){
    await wait(3300);
    state=await lab.save();
    const power=drive(state,lab.read,target);
    const sample={
      input:lab.count(state,input), intermediate:lab.count(state,intermediate),
      finished:stock(state,b,product)-baseline,
      inputBarrel:stock(state,a,input),
    };
    flow.push(sample);
    drainedSamples=sample.input||sample.intermediate?0:drainedSamples+1;
    if(drainedSamples&&sample.finished>=amount&&power.shared || drainedSamples>=3) break;
  }
  state=await lab.save(label+'-after');
  const power=drive(state,lab.read,target), transferred=stock(state,b,product)-baseline;
  const passed=Boolean(transferred>=amount&&!lab.count(state,input)&&!lab.count(state,intermediate)&&power.shared);
  const drained = !lab.count(state,input)&&!lab.count(state,intermediate);
  if(drained) lab.pending=undefined;
  return {passed,...(!passed&&drained ? {reason:'all feed drained but less than the required product reached the chosen finished barrel'} : {}),
    feed:action.at,out:action.out,inserted:amount,outputBefore:baseline,outputAfter:baseline+transferred,
    power,flow,site:survey(state,lab.ground)};
}

async function inject({before,ground,command,save,read,target}) {
  const power=drive(before,read,target);
  assert(power.shared,'fault injection requires a working shared drive');
  const motor=power.devices.find(e=>e.id==='create:creative_motor');
  const shaft=power.devices.find(e=>e.id==='create:shaft'&&reduced(e.source)===reduced(motor.at));
  const part=shaft || power.devices.find(e=>e.id==='create:gearbox');
  assert(part,'no eligible drive segment for isolated repair experiment');
  const [x,y,z]=part.at;
  const key=atKey([x,y+ground,z]);
  const original=before.blocks[key];
  const response=await command(`setblock ${x} ${y+ground} ${z} minecraft:air replace`);
  assert.match(response,/Changed/);
  await wait(3500);
  const broken=await save('fault');
  const observed=drive(broken,read,target);
  assert(!observed.shared,'injected fault did not interrupt the common drive');
  return {cell:part.at,original,newState:broken.blocks[key] || {Name:'minecraft:air'},observed};
}

async function injectLogistics(sample,before,lab) {
  const {position,ground,command,save,read,target}=lab;
  const input=position(sample.at), out=position(sample.out), at=shift(input,'down');
  const block=before.blocks[atKey(at)];
  assert(block?.Name==='minecraft:hopper','expected a hopper below the existing input barrel');
  const misaligned=Object.keys(cardinal).find(face=>face!=='up'&&face!==block.Properties?.facing&&
    !before.blocks[atKey(shift(at,face))]&&shift(at,face)[1]>=ground);
  assert(misaligned,'no empty receiving face for the controlled blockage');
  assert.match(await command('setblock '+at.join(' ')+' minecraft:hopper[facing='+misaligned+'] replace'),/Changed/);
  const baseline=stock(before,out,target.output);
  assert.match(await command('item replace block '+input.join(' ')+' container.0 with '+target.input+' '+amount),/Replaced/);
  await wait(3500);
  const halted=await save('logistics-fault');
  const stillWaiting=stock(halted,at,target.input)+stock(halted,input,target.input);
  const power=drive(halted,read,target);
  assert(stillWaiting===amount&&stock(halted,out,target.output)===baseline&&power.shared,
    'expected all input trapped at the entry while power stayed healthy');
  return {directions:{before:block.Properties.facing,after:misaligned},location:local(at,ground),
    waiting:stillWaiting,unchangedOutput:baseline,sharedPower:power.shared,pending:{out:atKey(out),baseline}};
}

module.exports={target,stone,knowledge,survey,assay,inject,injectLogistics,diagnose,drive};
