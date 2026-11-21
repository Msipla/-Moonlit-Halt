// Review, then observe the agent's real placement. Only the wheat input is written by the test.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {setTimeout: wait} = require('node:timers/promises');

const planFormat = '用一个 JSON 对象提交方案，省略长篇说明。字段为 bounds:[xmin,zmin,xmax,zmax]（屋体约7×7），floorY,roofY,door:[x,y,z]（门洞脚位），stand:[x,y,z]（磨石旁的站位），wheel:[x,y,z],facing,shafts:[[x,y,z],...],gearbox:[x,y,z],gearAxis,mill:[x,y,z],source:[x,y,z],basinY（导流井的实心底面Y），palette（说明地板、墙和屋顶材料）。shafts 按水轮往磨石方向排列。';
const planGeometry = ' 自行决定所有水平坐标。可采用地板 -60，脚位和水平传动 -59，磨石 -58，屋顶 -55，水槽底 -61。door.y=stand.y=floorY+1，站位严格在墙内且和室内磨石水平相邻。轮心严格在屋外，水槽底不能低于 -61。轮→轴→齿轮箱同高同轴穿过外墙，磨石在齿轮箱正上方。交卷前逐项核算。';
const same = (a,b)=>JSON.stringify(a)===JSON.stringify(b);
const move = ([x,y,z],dx,dy,dz)=>[x+dx,y+dy,z+dz];
const hex = (p)=>p.join(',');
const face = ([x,,z],[x0,z0,x1,z1])=> x===x0?'west':x===x1?'east':z===z0?'north':z===z1?'south':'interior';
const atRim = (p)=>(p.facing==='east'||p.facing==='west' ? 0 : 2);
const approachOf = (p)=>{
  const [dx,dz]={west:[-1,0],east:[1,0],north:[0,-1],south:[0,1]}[face(p.door,p.bounds)];
  return move(p.door,dx,0,dz);
};

function planForReview(plan,inScope,original) {
  if (original) {
    const changed=['door','stand','wheel','facing','shafts','gearbox','gearAxis','mill','source'];
    plan={...original,...Object.fromEntries(changed.map(key=>[key,plan[key]]))};
  }
  checkPlan(plan,inScope,original);
  return plan;
}

async function design(lab,label,initial,original) {
  const task = original
    ? '为已验收磨坊规划改造。沿用外包盒、地板和屋顶，入口换面，室内磨石挪位，把传动换成 Z 轴。自行选新位置。只交短 JSON，字段 door,stand,wheel,facing,shafts,gearbox,gearAxis,mill,source，坐标均为 [x,y,z]。轮、轴、箱同高相邻，箱轴 x，磨石正上方，水源在轮缘上一格。只审图，勿施工或解释。原方案 '+JSON.stringify(original)+'。'
    : '在你身边的空平地设计一间约7×7可进入的小水磨坊。用小水轮、至少一根传动轴和齿轮箱带动室内磨石，能在室内从上方投小麦、在旁边取面粉。外部水轮的水只能在封闭落水槽内，不能漫到地面。要有屋顶、入口和检修走道。你自行选坐标、朝向和材料，不需要整地。';
  const draft = (!original || process.env.LAB_MILL_VARIANT==='1') && process.env.LAB_MILL_DRAFT;
  let feedback = '', savedPlan;
  if (draft) {
    const previous=JSON.parse(fs.readFileSync(draft,'utf8'));
    let issue='';
    try { savedPlan=planForReview(previous,lab.inScope,original); }
    catch (error) { issue=error.message; }
    feedback=' 延续已提交的模型草案，只修正存在的问题并保留其他设计。草案 '+JSON.stringify(previous)+'。'+issue;
    if (original) feedback+=' 当前只修站位和门，保留其余草案。站位脚位必须和磨石水平相邻，不能占箱或轴。新门最好从同面沿墙移开旧水槽；干燥的外部接近列须有两格空位。';
  }
  for (let attempt=1;attempt<=2;attempt++) {
    let plan=savedPlan;
    savedPlan=undefined;
    if (plan) {
      lab.mark('mill_plan_draft_reused',{label,source:path.basename(draft)});
    } else {
      const details=original ? '' : ' 请读 create_kinetics 的 references/watermill.md。当前是审图阶段，只计划，禁止摆放、挖掘或放水。平地表面 y=-61，施工区 x/z=-20..20,y=-61..-40。'+planFormat+planGeometry;
      const reply = await lab.chat(task+details+feedback,185000);
      const planState = await lab.save(label+'-plan-'+attempt);
      assert(same(initial.blocks,planState.blocks),'World blocks changed before the design review');
      if (!reply || reply.status!==200 || reply.finish==='length') {
        lab.mark('mill_plan_blocked',{label,attempt,status:reply?.status,finish:reply?.finish});
        return null;
      }
      const match=reply.content.match(/\{[\s\S]*\}/);
      try { plan=planForReview(JSON.parse(match?.[0]??''),lab.inScope,original); }
      catch (error) {
        lab.mark('mill_plan_rejected',{label,attempt,reason:error.message});
        feedback=' 上一方案未通过审查，请只修正以下问题并重交完整方案。'+error.message;
        continue;
      }
    }
    const review = path.join(lab.run,label+'-review-'+attempt+'.json');
    lab.mark('mill_plan_review',{label,attempt,plan,review});
    const deadline=Date.now()+300000;
    while (!fs.existsSync(review) && Date.now()<deadline) await wait(500);
    assert(fs.existsSync(review),'Design review timed out');
    const decision=JSON.parse(fs.readFileSync(review,'utf8'));
    lab.mark('mill_review_decision',{label,attempt,...decision});
    if (decision.approved===true) return plan;
    feedback=' 上一方案未通过审查，请按审查意见修改并重交。'+decision.reason;
  }
  return null;
}

function checkPlan(p,inScope,original) {
  const keys=['door','stand','wheel','gearbox','mill','source'];
  const asArray = (pos)=>pos && !Array.isArray(pos) ? [pos.x,pos.y,pos.z] : pos;
  for(const key of keys) p[key]=asArray(p[key]);
  if(Array.isArray(p.shafts)) p.shafts=p.shafts.map(asArray);
  const cells=keys.map(key=>p[key]);
  assert(Array.isArray(p.shafts)&&p.shafts.length>=1,'Specify a straight run with at least one shaft');
  assert([...cells,...p.shafts].every(c=>Array.isArray(c)&&c.length===3&&c.every(Number.isInteger)&&inScope(c)),
    'All listed cells must be integer positions inside the construction volume');
  assert(Array.isArray(p.bounds)&&p.bounds.length===4&&p.bounds.every(Number.isInteger),'Invalid room bounds');
  const [x0,z0,x1,z1]=p.bounds;
  const inside=([x,,z])=>x>x0&&x<x1&&z>z0&&z<z1;
  assert([x1-x0+1,z1-z0+1].every(n=>n>=6&&n<=8),'Use a roofed room of about 7 by 7');
  assert(inScope([x0,p.floorY,z0])&&inScope([x1,p.roofY,z1]),'Room exceeds construction limits');
  assert(Number.isInteger(p.floorY)&&Number.isInteger(p.roofY)&&p.floorY>=-61&&p.floorY<=-59&&p.roofY>=p.mill[1]+3,
    'Provide a walkable floor just above ground and enough clearance for wheat above the mill');
  assert(p.door[0]>=x0&&p.door[0]<=x1&&p.door[2]>=z0&&p.door[2]<=z1&&face(p.door,p.bounds)!=='interior'
    &&!([x0,x1].includes(p.door[0])&&[z0,z1].includes(p.door[2])),'Put the entrance in a wall, away from the corner');
  assert(inside(p.stand)&&inside(p.mill),'Stand and mill must both be strictly inside the surface walls');
  assert(p.stand[1]===p.door[1]&&p.door[1]===p.floorY+1,'Both door and stand Y must be floorY+1');
  assert(Math.abs(p.stand[0]-p.mill[0])+Math.abs(p.stand[2]-p.mill[2])===1
    &&Math.abs(p.stand[1]-p.mill[1])<=1,'The stand must be next to the mill and at a usable height');
  assert(p.wheel[0]<x0||p.wheel[0]>x1||p.wheel[2]<z0||p.wheel[2]>z1,'Put the wheel outside the room');
  assert(['east','west','north','south'].includes(p.facing),'Wheel needs a horizontal facing');
  const axle=atRim(p);
  assert(p.gearAxis===(axle===0?'z':'x'),'Gearbox must expose both the drive axis and Y');
  const driveline=[p.wheel,...p.shafts,p.gearbox];
  assert(new Set(driveline.map(hex)).size===driveline.length,
    'The gearbox replaces the end of the linear route. Remove any shaft listed in the gearbox cell, and keep the remaining shaft between the wheel and gearbox.');
  assert(!driveline.some(c=>same(c,p.stand)),'Leave the walking and maintenance cell free of both shafts and gearbox');
  const step=driveline[1][axle]-driveline[0][axle];
  assert(Math.abs(step)===1 && driveline.slice(1).every((c,i)=>c.every((v,d)=>v===driveline[i][d]+(d===axle?step:0))),
    'Adjacent aligned shafts must join the axle face to the gearbox in a straight line');
  assert(same(p.mill,move(p.gearbox,0,1,0)),'Put the millstone directly above the gearbox');
  const other=axle===0?2:0;
  assert(p.source[axle]===p.wheel[axle] && p.source[1]===p.wheel[1]+1
    && Math.abs(p.source[other]-p.wheel[other])===1, 'Source must sit one block up and over the rim');
  assert(Number.isInteger(p.basinY)&&p.basinY>=-61&&p.basinY<p.wheel[1],
    'Basin bottom must be in bounds (Y >= -61) and below the wheel');
  if (original) {
    assert(same(original.bounds,p.bounds)&&p.floorY===original.floorY&&p.roofY===original.roofY,'Keep the original building footprint, floor and roof');
    assert(face(p.door,p.bounds)!==face(original.door,original.bounds)&&!same(p.mill,original.mill)
      &&axle!==atRim(original),'Change the entrance wall, indoor mill position and drive axis');
  }
}

function inspection(state,p) {
  const water = Object.entries(state.blocks).filter(([,b])=>b.Name==='minecraft:water')
    .map(([cell,b])=>({pos:cell.split(',').map(Number),level:Number(b.Properties?.level)}));
  const sources=water.filter(w=>w.level===0);
  const leaks=water.filter(({pos:[x,y,z]})=>x!==p.source[0]||z!==p.source[2]||y>p.source[1]||y<=p.basinY);
  const cells=[p.wheel,...p.shafts,p.gearbox,p.mill];
  const expected=['create:water_wheel',...p.shafts.map(()=>'create:shaft'),'create:gearbox','create:millstone'];
  const drive=cells.map((pos,i)=>{
    const ent=state.blockEntities.find(e=>same([e.x,e.y,e.z],pos));
    return {pos,expected:expected[i],block:state.blocks[pos],rpm:ent?.Speed,
      stressed:ent?.Network?.Stress>ent?.Network?.Capacity,flow:ent?.FlowScore,network:ent?.Network?.Id};
  });
  const mill=state.blockEntities.find(e=>same([e.x,e.y,e.z],p.mill));
  const flour=(mill?.OutputInventory?.Items??[]).filter(i=>i.id==='create:wheat_flour').reduce((n,i)=>n+i.Count,0);
  const rim=water.find(c=>same(c.pos,move(p.source,0,-1,0)));
  const expectedAxis=atRim(p)===0?'x':'z';
  const shaftChecks=drive.slice(1,-2).every(d=>d.block?.Properties?.axis===expectedAxis);
  const network=drive[0].network;
  const rpmOkay=network!==undefined&&drive.every(d=>d.block?.Name===d.expected&&Number.isFinite(d.rpm)
    &&d.rpm!==0&&!d.stressed&&same(d.network,network));
  const contained=sources.length===1&&same(sources[0].pos,p.source)&&Boolean(rim?.level>0)&&leaks.length===0;
  const exactDrive=shaftChecks&&drive[0].block?.Properties?.facing===p.facing
    &&drive.at(-2).block?.Properties?.axis===p.gearAxis;
  return {drive,water:water.length,sources,rim,leaks,flour,timer:mill?.Timer,
    passed:rpmOkay&&contained&&exactDrive};
}

function access(state,p,original) {
  const [x0,z0,x1,z1]=p.bounds;
  const name = (pos)=>state.blocks[pos]?.Name;
  const pass = (pos)=>!name(pos)||['minecraft:air','minecraft:cave_air'].includes(name(pos))
    || (name(pos)?.endsWith('_door')&&!name(pos).endsWith('iron_door'));
  const headroom = (pos)=>pass(pos)&&pass(move(pos,0,1,0));
  const level = (pos)=>headroom(pos)&&!pass(move(pos,0,-1,0))&&name(move(pos,0,-1,0))!=='minecraft:water';
  const approach = approachOf(p);
  const reachable = new Set();
  const pending = level(p.door) && (level(approach)||level(move(approach,0,-1,0))) ? [p.door]:[];
  while(pending.length) {
    const cell=pending.pop();
    if(reachable.has(hex(cell))) continue;
    reachable.add(hex(cell));
    for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]) {
      const next=move(cell,dx,0,dz);
      if(next[0]>x0&&next[0]<x1&&next[2]>z0&&next[2]<z1&&level(next)) pending.push(next);
    }
  }
  let roofCovered=0, interior=0;
  // A pitched roof can rise above its planned eaves.
  const roofLimit=Math.min(-40,p.roofY+Math.ceil(Math.max(x1-x0+1,z1-z0+1)/2)+2);
  for(let x=x0+1;x<x1;x++) for(let z=z0+1;z<z1;z++) {
    interior++;
    for(let y=p.roofY-1;y<=roofLimit;y++) if(!pass([x,y,z])&&name([x,y,z])!=='minecraft:water') {
      roofCovered++;break;
    }
  }
  const entranceMoved= !original || !headroom(original.door);
  const millTopClear=pass(move(p.mill,0,1,0))&&pass(move(p.mill,0,2,0));
  return {standReachable:reachable.has(hex(p.stand)),walkableCells:reachable.size,roofCovered,interior,
    millTopClear,entranceMoved,passed:reachable.has(hex(p.stand))&&roofCovered===interior&&millTopClear&&entranceMoved};
}

function missingChuteWalls(state,p) {
  const missing=[];
  const [x,top,z]=p.source;
  const check=(at)=>{
    if(same(at,p.wheel)) return;
    const block=state.blocks[at]?.Name;
    if(!block||block==='minecraft:water'||block==='minecraft:air') missing.push(at);
  };
  check([x,p.basinY,z]);
  for(let y=p.basinY+1;y<=top;y++) {
    for (const [dx,dz] of [[-1,0],[1,0],[0,-1],[0,1]]) check([x+dx,y,z+dz]);
  }
  return missing;
}

function retainedShell(before,after,p) {
  const [x0,z0,x1,z1]=p.bounds;
  const floorChanges=[],roofChanges=[];
  for (const cell of new Set([...Object.keys(before.blocks),...Object.keys(after.blocks)])) {
    if (same(before.blocks[cell],after.blocks[cell])) continue;
    const [x,y,z]=cell.split(',').map(Number);
    if (y===p.floorY&&x>=x0&&x<=x1&&z>=z0&&z<=z1) floorChanges.push(cell);
    if (y>=p.roofY-1&&x>=x0-1&&x<=x1+1&&z>=z0-1&&z<=z1+1) roofChanges.push(cell);
  }
  return {floorChanges,roofChanges,passed:floorChanges.length===0&&roofChanges.length===0};
}

const retired = (state,p)=>{
  const machine=inspection(state,p);
  return machine.drive.every(d=>d.block?.Name!==d.expected)&&machine.water===0;
};

async function build(lab,label,initial,original,before=initial) {
  const prior= !original && process.env.LAB_MILL_REVIEWED;
  const plan=prior ? reviewedPlan(prior,lab) : await design(lab,label,initial,original);
  if (!plan) return null;
  const approved=JSON.stringify(plan);
  let current=initial;
  if (original&&!retired(initial,original)) {
    await lab.chat('执行已审定的改造方案 '+approved+'。此步只撤销原水源和传动 '+JSON.stringify({
      source:original.source,wheel:original.wheel,shafts:original.shafts,gearbox:original.gearbox,mill:original.mill,
    })+'。用实心块封旧水源并等待消退，numen.work.dig 只拆列出的旧机械。保留地板屋顶。直接调用 Lua。',180000);
    await wait(5000);
    current=await lab.save(label+'-retired');
    const old=inspection(current,original);
    const removed=retired(current,original);
    lab.mark('mill_retired',{label,removed,old});
    if (!removed) return null;
  }
  const hasParts=(s)=>inspection(s,plan).drive.every(d=>d.block?.Name===d.expected);
  if (original || !hasParts(current)) {
    const axis=atRim(plan)===0?'x':'z';
    await lab.chat('按已审方案 '+approved+' 施工。当前只放干态水轮、轴、齿轮箱、磨石四类方块，不施工房屋或放水。'+
      '用 numen.build.place，一项一个 pos，方块名 create:water_wheel[facing='+plan.facing+
      ']、create:shaft[axis='+axis+']、create:gearbox[axis='+plan.gearAxis+
      ']、create:millstone。直接发 Lua，不查帮助或输出长计划。施工后读回这几格。',180000);
    current=await lab.save(label+'-dry');
    lab.mark('mill_dry',{label,drive:inspection(current,plan).drive});
    if(!hasParts(current)) return null;
  }
  const gaps=missingChuteWalls(current,plan);
  if (gaps.length) {
    await lab.chat('当前只封水槽，发 Lua 施工，本步不新增水源。轮心 '+hex(plan.wheel)+'、预定源 '+hex(plan.source)+
      '，槽底 Y='+plan.basinY+'。独立检查发现还缺实心隔水方块 '+JSON.stringify(gaps)+
      '，请用 numen.build.place 按 {name="minecraft:stone",pos={x=…,y=…,z=…}} 放置。不要覆盖水柱、传动或磨石。已有源保留，封完等旧溢流消退。',180000);
    current=await lab.save(label+'-chute');
    const remaining=missingChuteWalls(current,plan);
    lab.mark('mill_chute',{label,remaining,missingBefore:gaps});
    if(remaining.length) return null;
  }
  if (!inspection(current,plan).sources.length) {
    await lab.chat('水槽已封。只做供水，用 createlab_water.source.place 在 '+hex(plan.source)+
      ' 放一个源，轮心 '+hex(plan.wheel)+'。等水流后读水轮、轴、齿轮箱和磨石 RPM。直接发 Lua。',140000);
  }
  await wait(5000);
  const wet=inspection(await lab.save(label+'-powered'),plan);
  lab.mark('mill_powered',{label,...wet});
  if(!wet.passed) return null;
  const powered=await lab.save(label+'-shell-start');
  const shell=access(powered,plan,original);
  if (!shell.passed) {
    const roofOnly=!original&&shell.standReachable&&shell.millTopClear&&shell.roofCovered<shell.interior;
    await lab.chat('机械已通过验证，保留已工作的轴、磨石和水槽。方案 '+approved+'。'+
      (original?'入口换面，用 numen.work.dig 打通新门两格净高，连同外侧脚位 '+hex(approachOf(plan))+' 的两格净高（若已空不要动）。封原门 '+hex(original.door)+' 的余空，保留穿墙传动、原地板和屋顶。'
        :roofOnly?'已有完整地板和墙，只完成尚未落盘的云杉屋顶。不要重造墙或地板。'
          :'建地板、墙和屋顶。')+
      '入口通往磨石旁站位，要有两格净高并留投料孔。用 numen.build.place，直接分层发 Lua 实施。',330000);
  }
  const built=await lab.save(label+'-built');
  const machine=inspection(built,plan), layout=access(built,plan,original);
  const retained=original ? retainedShell(before,built,original) : undefined;
  lab.mark('mill_built',{label,machine,layout,retained});
  if (!machine.passed||!layout.passed||retained?.passed===false) return null;
  const [x,y,z]=plan.mill;
  await lab.cmd(`summon minecraft:item ${x+.5} ${y+1.35} ${z+.5} {Item:{id:"minecraft:wheat",Count:1b},PickupDelay:32767s}`);
  await wait(11000);
  const after=await lab.save(label+'-fed');
  const finished=inspection(after,plan);
  const produced=finished.flour-machine.flour;
  const passed=finished.passed&&produced>0;
  lab.mark('mill_result',{label,passed,produced,machine:finished,layout,retained});
  return passed ? {plan,world:after}:null;
}

function reviewedPlan(filepath,lab) {
  const entries=fs.readFileSync(filepath,'utf8').trim().split('\n').map(JSON.parse);
  const decision=entries.findLast(e=>e.type==='mill_review_decision'&&e.label==='initial');
  assert(decision?.approved===true,'The previous run has no approved initial plan');
  const review=entries.findLast(e=>e.type==='mill_plan_review'&&e.label==='initial'&&e.attempt===decision.attempt);
  assert(review,'The previous approval has no matching model plan');
  const plan=review.plan;
  checkPlan(plan,lab.inScope,null);
  lab.mark('mill_plan_reused',{plan,sourceRun:path.basename(path.dirname(filepath))});
  return plan;
}

async function test(lab) {
  await lab.cmd('gamerule randomTickSpeed 0');
  const startingState=await lab.save('mill-start');
  if (process.env.LAB_MILL_VARIANT==='1') {
    assert(process.env.LAB_MILL_WORLD&&process.env.LAB_MILL_REVIEWED,'Provide an accepted world and its reviewed plan');
    const original=reviewedPlan(process.env.LAB_MILL_REVIEWED,lab);
    const accepted=process.env.LAB_MILL_BEFORE;
    const before=accepted ? JSON.parse(fs.readFileSync(accepted,'utf8')) : startingState;
    const machine=inspection(before,original), layout=access(before,original,null);
    assert(machine.passed&&machine.flour>0&&layout.passed,'The baseline must already pass every check');
    if (accepted) {
      assert(retired(startingState,original)&&retainedShell(before,startingState,original).passed,
        'The copied world must have finished retiring the old machine with the floor and roof intact');
    }
    lab.mark('mill_variant_resumed',{machine,layout,retired:!!accepted});
    return build(lab,'revised',startingState,original,before);
  }
  const initial=await build(lab,'initial',startingState,null);
  if (initial) await build(lab,'revised',initial.world,initial.plan);
}

const pos = (entity) => [entity.x, entity.y, entity.z];
const itemCount = (entity,id) => (entity?.OutputInventory?.Items ?? [])
  .filter(item => item.id === id).reduce((sum,item) => sum + item.Count,0);
const water = (state) => Object.entries(state.blocks).filter(([,b])=>b.Name==='minecraft:water')
  .map(([cell,b])=>[cell,b.Properties.level]).sort();
const block = (state,position) => state.blocks[position]?.Name ?? 'minecraft:air';

function measure(state, start, original, sideDrive) {
  const mills = state.blockEntities.filter(e => e.id === 'create:millstone');
  const mill = mills.length === 1 ? mills[0] : null;
  const machineCells = new Map(state.blockEntities.map(e => [pos(e).join(),e]));
  const route = [];
  const seen = new Set();
  for (let part = mill; part && !seen.has(pos(part).join());) {
    seen.add(pos(part).join());
    route.push({id:part.id,pos:pos(part),rpm:part.Speed,network:part.Network?.Id,
      stress:part.Network?.Stress,capacity:part.Network?.Capacity});
    if (same(pos(part),original.wheel) || !part.Source) break;
    part = machineCells.get([part.Source.X,part.Source.Y,part.Source.Z].join());
  }
  const wheel = route.at(-1);
  const onlyOriginalWheel = state.blockEntities.filter(e=>e.id==='create:water_wheel').length===1
    && wheel?.id === 'create:water_wheel' && same(wheel.pos,original.wheel)
    && wheel.network !== undefined && route.every(e => same(e.network,wheel.network)
      && Number.isFinite(e.rpm) && e.rpm !== 0 && e.stress <= e.capacity);
  const [x0,z0,x1,z1] = original.bounds;
  const depth = ([x,,z]) => Math.min(x-x0,x1-x,z-z0,z1-z);
  const moved = Boolean(mill && depth(pos(mill))>depth(original.mill)
    && block(state,original.mill)==='minecraft:air');
  const input = route[1];
  const sideCog = sideDrive && mill && input && same(pos(mill),original.mill)
    && input.pos[1]===mill.y && Math.abs(input.pos[0]-mill.x)+Math.abs(input.pos[2]-mill.z)===1
    && block(state,input.pos)==='create:cogwheel'
    && state.blocks[input.pos]?.Properties?.axis==='y' ? input.pos : null;
  const door = [original.door,[original.door[0],original.door[1]+1,original.door[2]]];
  const preserved = {
    ...retainedShell(start,state,original),
    entrance:door.every(cell=>same(start.blocks[cell],state.blocks[cell])),
    water:same(water(start),water(state)),
    wheel:same(start.blocks[original.wheel],state.blocks[original.wheel]),
  };
  let accessCheck=null, usePosition=null;
  if (mill) {
    for (const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]) {
      const stand = [mill.x+dx, original.door[1], mill.z+dz];
      if (depth(stand)<=0 || Math.abs(stand[1]-mill.y)>1) continue;
      const result = access(state,{...original,mill:pos(mill),stand},null);
      if (result.passed) { accessCheck=result; usePosition=stand; break; }
    }
  }
  return {millCount:mills.length,mill:mill&&pos(mill),moved,onlyOriginalWheel,route,
    flour:itemCount(mill,'create:wheat_flour'),processing:mill?.Timer,preserved,accessCheck,usePosition,
    ...(sideDrive ? {sideCog} : {}),
    passesLayout:(sideDrive ? Boolean(sideCog) : moved)&&onlyOriginalWheel&&Object.values(preserved).slice(2).every(Boolean)&&Boolean(accessCheck)};
}

async function testAutonomous(lab, sideDrive = false) {
  await lab.cmd('gamerule randomTickSpeed 0');
  const before = await lab.save('autonomous-before');
  const original = reviewedPlan(process.env.LAB_MILL_REVIEWED,lab);
  assert(inspection(before,original).passed && access(before,original,null).passed,
    'The inherited watermill needs to pass the read-only control');
  const prompt = sideDrive
    ? '请改造这座能工作的 Create 磨坊。磨石留在原位置，改用一个和磨石同高、水平相邻的小齿轮从侧面直接带动它，动力仍只能来自原来的水轮。保留原供水、入口、地板和屋顶，让人还能从原入口进入、使用磨石并从上方投料。自行勘察、规划和施工，用实际动力链以及投料前后的新产物验证改造，修复施工时误改的地方。最后告诉我你在游戏里验证了什么。'
    : '把这座已经能磨粉的水磨坊做一次小改造：把现在的磨石从靠外墙的位置搬到室内更靠中央的位置，原磨石那一格腾空。保留原来的水轮、供水、入口、地板和屋顶，仍然只用原水轮带动磨石。从原入口走进去要能使用新磨石，磨石上方要能投料。你可以按需查现有 create_kinetics 机械参考。请自己勘察、设计、施工和检查，发现问题就按现场反馈自行修复。完成后告诉我你实际验证了什么。';
  lab.mark('autonomous_start',{interventions:0,ownerMessages:1,limitSeconds:420,
    ...(sideDrive ? {objective:'side-drive'} : {})});
  const started = performance.now();
  const answer = await lab.chat(prompt,420000);
  await verifyAutonomous(lab,before,original,sideDrive,started,answer);
}

async function checkSideDrive(lab) {
  await lab.cmd('gamerule randomTickSpeed 0');
  const before = await lab.save('autonomous-before');
  const original = reviewedPlan(process.env.LAB_MILL_REVIEWED,lab);
  assert(inspection(before,original).passed && access(before,original,null).passed,
    'The inherited watermill needs to pass the read-only control');
  const [dx,dz] = [Math.sign(original.mill[0]-original.wheel[0]),Math.sign(original.mill[2]-original.wheel[2])];
  assert(Math.abs(dx)+Math.abs(dz)===1,'The reference drive must be straight');
  const cog = move(original.mill,dx,0,dz);
  const gear = move(original.gearbox,dx,0,dz);
  assert(!before.blocks[cog] && !before.blocks[gear],'Reference side-drive cells must be empty');
  const edits = [
    [original.gearbox,`create:shaft[axis=${dx ? 'x' : 'z'}]`],
    [gear,`create:gearbox[axis=${original.gearAxis}]`],
    [cog,'create:cogwheel[axis=y]'],
  ];
  const started = performance.now();
  lab.mark('side_drive_control',{mode:'fixture-only',interventions:edits.length});
  for (const [cell,state] of edits) {
    assert(lab.inScope(cell),'Reference edit is outside the construction scope');
    const result = await lab.cmd(`setblock ${cell.join(' ')} ${state}`);
    assert.match(result,/^Changed the block/);
  }
  await wait(800);
  await verifyAutonomous(lab,before,original,true,started,null,edits.length);
}

async function verifyAutonomous(lab,before,original,sideDrive,started,answer,interventions=0) {
  const elapsed = (performance.now()-started)/1000;
  await lab.isolateMillAgent();
  const final = await lab.save('autonomous-after');
  const result = measure(final,before,original,sideDrive);
  const outsideChanges = [...new Set([...Object.keys(before.blocks),...Object.keys(final.blocks)])]
    .filter(cell=>!lab.inScope(cell.split(',').map(Number))&&!same(before.blocks[cell],final.blocks[cell]));
  const changes = [...new Set([...Object.keys(before.blocks),...Object.keys(final.blocks)])]
    .filter(cell=>!same(before.blocks[cell],final.blocks[cell]))
    .map(cell=>({pos:cell,before:before.blocks[cell],after:final.blocks[cell]}));
  const incidentalChanges = changes.filter(c=>[c.before,c.after].some(b=>b&&!b.Name.startsWith('create:')));
  lab.mark('autonomous_inspection',{elapsed,finish:answer?.finish,status:answer?.status,
    interventions,result,outsideChanges,incidentalChanges,changes});
  if (!result.passesLayout || outsideChanges.length) {
    lab.mark('autonomous_result',{passed:false,feed:'not-run',elapsed});
    return;
  }
  // A finished agent may have left a wheat item falling toward the mill.
  const looseWheat = final.items.filter(e=>e.item.id==='minecraft:wheat').length;
  if (looseWheat) {
    await lab.cmd('kill @e[type=minecraft:item,x=-24,y=-64,z=-24,dx=48,dy=29,dz=48,nbt={Item:{id:"minecraft:wheat"}}]');
  }
  const assayStart = looseWheat ? await lab.save('autonomous-assay-start') : final;
  const assayMill = assayStart.blockEntities.find(e=>e.id==='create:millstone');
  const wheatPending = assayStart.items.some(e=>e.item.id==='minecraft:wheat')
    || assayStart.blockEntities.some(e=>(e.Items ?? []).some(item=>item.id==='minecraft:wheat'))
    || assayMill.Timer>0 || assayMill.InputInventory?.Items?.length>0;
  if (wheatPending) {
    lab.mark('autonomous_result',{passed:false,feed:'pending-agent-wheat',elapsed,looseWheat});
    return;
  }
  const [x,y,z] = result.mill;
  await lab.cmd(`summon minecraft:item ${x+.5} ${y+1.35} ${z+.5} {Item:{id:"minecraft:wheat",Count:1b},PickupDelay:32767s}`);
  await wait(11000);
  const fedState = await lab.save('autonomous-fed');
  const fed = measure(fedState,before,original,sideDrive);
  const produced = fed.flour-itemCount(assayMill,'create:wheat_flour');
  const changesDuringAssay = [...new Set([...Object.keys(assayStart.blocks),...Object.keys(fedState.blocks)])]
    .filter(cell=>!same(assayStart.blocks[cell],fedState.blocks[cell]));
  lab.mark('autonomous_result',{passed:fed.passesLayout&&produced>0&&!changesDuringAssay.length,
    produced,final:fed,elapsed,looseWheat,changesDuringAssay,
    assaySeconds:(performance.now()-started)/1000-elapsed});
}

async function checkIsolation(lab) {
  const initial = await lab.save('isolation-before');
  const first = [0,-59,12], late = [0,-59,13];
  assert(!initial.blocks[first]&&!initial.blocks[late], 'Test cells must start empty');
  await lab.cmd('numen drive LabBot numen.build.place({{name="minecraft:gold_block",pos={x=0,y=-59,z=12}}}) '+
    'numen.time.wait(6) numen.build.place({{name="minecraft:gold_block",pos={x=0,y=-59,z=13}}})');
  await lab.waitForLog('on the server (',5000);
  await wait(1600);
  const pending = await lab.save('isolation-pending');
  assert.equal(pending.blocks[first]?.Name,'minecraft:gold_block','The Lua program must perform its first write');
  assert(!pending.blocks[late], 'The late write must still be pending');
  await lab.isolateMillAgent();
  await wait(6500);
  const isolated = await lab.save('isolation-after');
  assert(!isolated.blocks[late], 'The kicked companion performed the late write');
  assert(same(pending.blocks, isolated.blocks), 'Blocks changed during isolation');
  lab.mark('isolation_check',{passed:true,initialWrite:first,blockedLateWrite:late,sameBlocks:true});
}

module.exports={test,testAutonomous,checkIsolation,checkSideDrive};
