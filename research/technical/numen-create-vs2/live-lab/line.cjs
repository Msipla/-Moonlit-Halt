const assert = require('node:assert/strict');
const {setTimeout: wait} = require('node:timers/promises');

const casing = 'minecraft:barrel';
const product = 'create:dough';
const intermediate = 'create:wheat_flour';
const input = 'minecraft:wheat';
const amount = 3;
const target = {
  goal:'从空地自主建造 Create 自动面团产线。用同一台创造马达驱动磨石和水洗风扇。用原料桶自动进料，磨石将小麦变为面粉，传输到水洗工位，再把面团自动存进另一个桶。处理至少三份小麦，期间不要人工转移物料。请用实际库存和动力读数自验。',
  recipe:'milling/wheat + splashing/wheat_flour', input, output:product, line:true,
};
const knowledge = [
  'For a new line, first build only the motor, transmission and both loads. Check Build kinetics for a single network with both loads rotating. Correct power first, then add feed, washing and output paths in successive batches. Do not resend a full layout when only one connection or cell is wrong.',
  'For an automated chain, test is {"action":"test","at":[input barrel xyz],"out":[finished barrel xyz]}. Separate barrels; build both and choose positions. Assay deposits 3 wheat in the input barrel only; it never moves intermediates.',
  'Read any uncertain site using {"action":"observe"} for actual blocks, rotations, filters and inventories. World and power are real game state.',
  'Native logistics: a hopper extracts from the inventory directly ABOVE it and pushes into the block it FACES. A hopper aimed into the side of a millstone can feed it. A millstone allows processed output to be extracted. Chutes transport down; a horizontal row of chutes will not carry items sideways.',
  'A millstone meshes with an adjacent vertical (axis=y) small cog. Set axis explicitly on every shaft and gearbox; the gearbox axis is the pair of faces without shafts and the other four can connect. A fan must receive a shaft along its facing axis from behind.',
  'Smart-chute example fields in a block entry: "state":"create:smart_chute[powered=false]","filter":"create:dough". The filter is a separate field, not a state property. A smart chute extracts from above and sends downward, including to or from a depot. Filter before the depot to keep byproducts out and filter the finished output to avoid removing flour too soon.',
  'A sideways fan can wash items held by a depot one cell BELOW the air stream after it passes through water. Keep the active air line clear of solid blocks. Use a contained source. Leave room on both sides of named devices for feeding and power.',
];
const reduced = (value) => JSON.stringify(value);
const atKey = (p) => p.join(',');
const local = (p,ground) => [p[0],p[1]-ground,p[2]];
const entityAt = (state,p) => state.blockEntities.find(e=>e.x===p[0]&&e.y===p[1]&&e.z===p[2]);
function stacks(e) {
  return [e?.Items, e?.InputInventory?.Items, e?.OutputInventory?.Items,
    e?.OutputBuffer?.Items, e?.HeldItem?.Item, e?.Item, e?.Stack].flat().filter(x=>x?.id && x.Count>0);
}
function survey(state,ground) {
  const cardinal={east:[1,0,0],west:[-1,0,0],up:[0,1,0],down:[0,-1,0],south:[0,0,1],north:[0,0,-1]};
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
  return {
    motorPorts:motors,
    cells:Object.entries(state.blocks).flatMap(([k,b])=>{
      const p=k.split(',').map(Number);
      if(p[1]<ground || b.Name==='minecraft:barrier' || b.Name==='minecraft:water' && b.Properties?.level!=='0') return [];
      const e=entityAt(state,p);
      return [{at:local(p,ground),id:b.Name,state:b.Properties,filter:e?.Filter?.id,items:stacks(e)}];
    }),
  };
}

function drive(state,read) {
  const devices=read(state);
  const motor=devices.find(e=>e.id==='create:creative_motor'&&Math.abs(e.rpm)>0);
  const loads=['create:millstone','create:encased_fan'].map(id=>devices.find(e=>e.id===id));
  const shared=motor?.network!==undefined && loads.every(e=>e && Math.abs(e.rpm)>0 &&
    reduced(e.network)===reduced(motor.network) && e.stress<=e.capacity);
  return {shared:Boolean(shared),devices:devices.filter(e=>e.rpm!==undefined)};
}
function stock(state,p,id) {
  return stacks(entityAt(state,p)).filter(s=>s.id===id).reduce((n,s)=>n+s.Count,0);
}
async function assay(action, label, lab) {
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
  const driveBefore=drive(before,lab.read);
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
  for(let i=0;i<23;i++){
    await wait(3300);
    state=await lab.save();
    const power=drive(state,lab.read);
    const sample={
      input:lab.count(state,input), intermediate:lab.count(state,intermediate),
      finished:stock(state,b,product)-baseline,
      inputBarrel:stock(state,a,input),
    };
    flow.push(sample);
    if(!sample.input&&!sample.intermediate&&sample.finished>=amount&&power.shared) break;
  }
  state=await lab.save(label+'-after');
  const power=drive(state,lab.read), transferred=stock(state,b,product)-baseline;
  const passed=Boolean(transferred>=amount&&!lab.count(state,input)&&!lab.count(state,intermediate)&&power.shared);
  if(passed) lab.pending=undefined;
  return {passed,
    feed:action.at,out:action.out,inserted:amount,outputBefore:baseline,outputAfter:baseline+transferred,
    power,flow,site:survey(state,lab.ground)};
}

async function inject({before,ground,command,save,read}) {
  const power=drive(before,read);
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
  const observed=drive(broken,read);
  assert(!observed.shared,'injected fault did not interrupt the common drive');
  return {cell:part.at,original,newState:broken.blocks[key] || {Name:'minecraft:air'},observed};
}

module.exports={target,knowledge,survey,assay,inject};
