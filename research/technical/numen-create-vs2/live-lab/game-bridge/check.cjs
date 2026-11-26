const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {gunzipSync}=require('node:zlib');
const {setTimeout:wait}=require('node:timers/promises');
const {createRequire}=require('node:module');

module.exports=async function check(lab){
  // Inert floor prevents grass decay beneath opaque placements from obscuring the boundary check.
  await lab.cmd('fill -7 -61 5 8 -61 20 minecraft:stone');
  if(process.env.LAB_ENGINEERING_SMOKE==='ship' && process.env.LAB_ENGINEERING_VESSEL==='water'){
    for(const edge of ['-7 -60 5 -7 -58 20','8 -60 5 8 -58 20','-6 -60 5 7 -58 5','-6 -60 20 7 -58 20']){
      await lab.cmd('fill '+edge+' minecraft:stone');
    }
    await lab.cmd('fill -6 -60 6 7 -58 19 minecraft:water');
  }
  if(['dough','logistics','stationary'].includes(process.env.LAB_ENGINEERING_SMOKE)){
    for(const edge of ['-7 -60 5 -7 -60 20','8 -60 5 8 -60 20','-6 -60 5 7 -60 5','-6 -60 20 7 -60 20']){
      await lab.cmd('fill '+edge+' minecraft:stone');
    }
  }
  const baseline=await lab.save('engineering-empty');
  await lab.drive('local ok, why = pcall(function() return engineering.site.apply({{pos={x=-8,y=-59,z=11},state="minecraft:stone"}}) end); print("engineering_scope",ok,tostring(why))','engineering_scope');
  assert.match(lab.output(),/engineering_scope\s+false\s+[^\n]*outside the configured site/);
  await lab.cmd('gamemode survival LabBot');
  await lab.drive('local ok, why = pcall(function() return engineering.site.inspect({x=0,y=-59,z=11}) end); print("engineering_mode",ok,tostring(why))','engineering_mode');
  assert.match(lab.output(),/engineering_mode\s+false\s+[^\n]*creative companion/);
  await lab.cmd('gamemode creative LabBot');
  const unchanged=await lab.save('engineering-preflight');
  assert.deepEqual(unchanged.blocks,baseline.blocks);
  lab.mark('engineering_guards',{outOfScopeRejected:true,survivalRejected:true,worldUnchanged:true});
  await lab.cmd('deop LabOwner');
  if(process.env.LAB_ENGINEERING_SMOKE==='planning') return planningCheck(lab);
  if(process.env.LAB_ENGINEERING_SMOKE==='overload') return overloadCheck(lab);
  if(process.env.LAB_ENGINEERING_SMOKE==='routing') return routingCheck(lab);
  if(process.env.LAB_ENGINEERING_SMOKE==='feed') return feedCheck(lab,baseline);
  if(process.env.LAB_ENGINEERING_SMOKE==='permissions') return permissionCheck(lab,baseline);
  if(process.env.LAB_ENGINEERING_SMOKE==='ship') return shipCheck(lab,baseline);
  if(process.env.LAB_ENGINEERING_SMOKE==='line') return calibrateLine(lab,baseline);
  if(['dough','logistics','stationary'].includes(process.env.LAB_ENGINEERING_SMOKE)) return calibrateDough(lab,baseline,process.env.LAB_ENGINEERING_SMOKE);
  if(process.env.LAB_ENGINEERING_SMOKE==='1') return settingsCheck(lab);
  if(['1','vertical'].includes(process.env.LAB_ENGINEERING_LINE)) return lineCheck(lab,baseline);
  if(['1','clearance'].includes(process.env.LAB_ENGINEERING_PRESS)) return pressCheck(lab,baseline);
  const goal='在授权创造区 X=-6..-1、Y=-60..-55、Z=9..13 自选两个水平相邻空格，造一台由创造马达直接驱动的 Create 风扇。用 engineering.site.apply，一批提交两个格子。每格填 pos、state、faces；faces 写朝向邻接机器的一面，工具会选择合法的原生朝向。无需寻路或取材料。用 engineering.site.inspect 回读风扇实际 RPM。若已转，导出紧包围两格的 fan_demo 标准 NBT 并用 import_at 在区内 X=3..6 的空位重建，确认副本 RPM，简短回执。';
  const model=await lab.chat(goal,200000);
  const completed=Boolean(model&&!model.truncated);
  await lab.isolate();
  const final=await lab.save('engineering-result');
  const fans=final.blockEntities.flatMap(e=>final.blocks[[e.x,e.y,e.z].join(',')]?.Name==='create:encased_fan'
    ? [{at:[e.x,e.y,e.z],rpm:e.Speed,network:e.Network?.Id,source:e.Source}] : []);
  const file=path.join(lab.run,'world','generated','engineering','structures','fan_demo.nbt');
  let structure;
  if(fs.existsSync(file)){
    const nbt=createRequire(path.join(lab.base,'lab.cjs'))('prismarine-nbt');
    const saved=nbt.simplify(nbt.parseUncompressed(gunzipSync(fs.readFileSync(file))));
    structure={size:saved.size,version:saved.DataVersion,nativeBlocks:saved.blocks?.length,
      registry:saved.palette?.map(e=>e.Name),safeData:safeData(saved)};
  }
  const backupDir=path.dirname(file);
  const backups=fs.existsSync(backupDir)?fs.readdirSync(backupDir).filter(n=>n.startsWith('backup_')).length:0;
  const outside=outsideChanges(baseline,final);
  const pass=Boolean(completed&&fans.some(e=>e.at[0]<0&&Math.abs(e.rpm)>0)&&
    fans.some(e=>e.at[0]>=3&&Math.abs(e.rpm)>0)&&structure?.safeData&&structure.registry.includes('create:encased_fan')&&
    backups>=2&&outside.length===0);
  lab.mark('engineering_result',{passed:pass,fans,structure,backups,outside,modelReturned:completed});
  if(!pass) process.exitCode=1;
};


async function planningCheck(lab) {
  const program = `
    local p=function(x,y,z) return {x=x,y=y,z=z} end
    local motor=p(-4,-56,9); local branch=p(-3,-56,9); local shaft=p(-2,-56,9)
    local vertical=p(-3,-55,9); local mill=p(-3,-54,9); local press=p(-1,-56,9)
    local depot=p(-1,-58,9)
    local foot=p(1,-60,9); local hall=p(2,-60,9)
    local survey=engineering.site.survey({foot,hall})
    assert(survey[1].floor_y==-61 and survey[1].feet_y==-60)
    local poor=engineering.site.plan({cells={{pos=hall,state="minecraft:stone"}},walk={foot,hall}})
    assert(#poor.clearance==1 and string.find(poor.clearance[1].reason,"feet blocked",1,true), tostring(poor))
    local first={points={motor,branch,vertical,mill},load="create:millstone"}
    local second={points={motor,branch,shaft,press},load="create:mechanical_press"}
    local route=engineering.site.route({first,second},64)
    assert(#route.cells==6 and #route.check.rejects==0,tostring(route))
    local cells=route.cells
    local pre=engineering.site.plan({cells=cells,walk={foot,hall},machines={press,mill}})
    assert(#pre.clearance==1 and string.find(pre.clearance[1].reason,"exactly two",1,true),tostring(pre))
    cells[#cells+1]={pos=depot,state="create:depot"}
    assert(#engineering.site.plan({cells=cells,walk={foot,hall},machines={press,mill}}).clearance==0)
    local receipt=engineering.site.apply(cells)
    assert(receipt.cells==7 and receipt.power_wait_ticks==4 and #receipt.warnings==0,tostring(receipt))
    local read=function(pos) return engineering.site.inspect(pos) end
    assert(read(press).power_status=="running" and read(mill).power_status=="running")
    assert(read(press).network==read(mill).network)
    local extra=p(4,-57,12); local extraLoad=p(5,-57,12)
    local other=engineering.site.route({{points={extra,extraLoad},load="create:encased_fan"}},32)
    assert(#other.check.rejects==0,tostring(other))
    engineering.site.apply(other.cells)
    assert(read(extraLoad).power_status=="running" and read(extraLoad).network~=read(mill).network)
    local axis=read(shaft).state
    local broken=engineering.site.apply({{pos=shaft,state="create:shaft[axis=z]",expected=axis}})
    local damaged=read(press)
    assert(damaged.power_status=="stale_source" or damaged.power_status=="disconnected",tostring(broken))
    assert(read(mill).power_status=="running" and read(extraLoad).power_status=="running")
    local fixed=engineering.site.apply({{pos=shaft,state=axis,expected=read(shaft).state}})
    assert(#fixed.warnings==0 and read(press).power_status=="running",tostring(fixed))
    print("planning_pass",receipt.power_wait_ticks,damaged.rpm,damaged.power_status,read(press).rpm,read(mill).rpm,read(extraLoad).rpm)
  `.replaceAll('\n',' ');
  await lab.drive(program,'\nplanning_pass');
  const text=lab.output().split('\n').find(line=>line.startsWith('planning_pass\t'));
  assert(text);
  lab.mark('engineering_planning',{passed:true,states:text.split('\t').slice(1), fixtureOnly:true});
}

async function routingCheck(lab) {
  await lab.drive(`local p=function(x,y,z) return {x=x,y=y,z=z} end
    local motor=p(-4,-58,9); local press=p(1,-57,10); local mill=p(-2,-54,11);
    local depot=p(1,-59,10); local gap=p(1,-58,10)
    local r=engineering.site.connect(motor,{{pos=press,state="create:mechanical_press"},
      {pos=mill,state="create:millstone",side_mesh=true}},64,{avoid={gap,depot,p(-2,-55,11)}});
    assert(#r.cells==0 or #r.check.rejects==0,tostring(r))
    `.replaceAll('\n',' ')+
    ' assert(#r.cells>=6,tostring(r)); local plan=engineering.site.plan({cells=r.cells,machines={mill}});'+
    ' assert(#plan.changes.rejects==0 and #plan.clearance==0,tostring(plan));'+
    ' local a=engineering.site.apply(r.cells); '+
    ' local pressPower=engineering.site.inspect(press); local millPower=engineering.site.inspect(mill);'+
    ' assert(a.cells==#r.cells and pressPower.power_status=="running" and millPower.power_status=="running" and pressPower.network==millPower.network);'+
    ' print("routing_pass",a.cells,pressPower.rpm,millPower.rpm)','\nrouting_pass');
  const line=lab.output().split('\n').find(l=>l.startsWith('routing_pass\t'));
  assert(line);
  await lab.drive(`local p=function(x,y,z) return {x=x,y=y,z=z} end
    local motor=p(-5,-56,16); local shaft=p(-4,-56,16); local fan=p(1,-56,16);
    local site=engineering.site;
    assert(site.apply({{pos=motor,state="create:creative_motor[facing=east]",rpm=64},
      {pos=shaft,state="create:shaft[axis=x]"}}).cells==2);
    local before=site.inspect(shaft);
    local branch=site.connect(shaft,{{pos=fan,state="create:encased_fan[facing=east]"}},64,{reuse=true});
    assert(#branch.cells>0 and #branch.check.rejects==0);
    local exact=site.route({{points={p(3,-51,19),p(4,-51,19)},load="create:encased_fan[facing=west]"}},64);
    assert(#exact.check.rejects>0,"cannot silently reverse fixed fan facing");
    for _,cell in ipairs(branch.cells) do assert(cell.pos.x~=-4 or cell.pos.y~=-56 or cell.pos.z~=16) end;
    assert(site.apply(branch.cells).cells==#branch.cells);
    local after=site.inspect(shaft); local load=site.inspect(fan);
    assert(after.state==before.state and after.rpm==before.rpm and load.power_status=="running" and load.network==after.network);
    print("reuse_pass",#branch.cells,load.rpm)`.replaceAll('\n',' '),'\nreuse_pass');
  const reuse=lab.output().split('\n').find(row=>row.startsWith('reuse_pass\t'));
  assert(reuse);
  lab.mark('engineering_routing',{passed:true,cells:Number(line.split('\t')[1]),rpm:line.split('\t').slice(2).map(Number),reuse:reuse.split('\t').slice(1).map(Number)});
}

async function overloadCheck(lab) {
  await lab.drive(`local a={x=0,y=-56,z=10}; local b={x=1,y=-56,z=10};
    local r=engineering.site.apply({{pos=a,state="create:creative_motor",faces={"east"},rpm=64},
      {pos=b,state="create:mechanical_press",faces={"west"}}});
    local press=engineering.site.inspect(b);
    assert(press.rpm==0 and press.overstressed and press.power_status=="overstressed",tostring(press));
    print("overload_pass",press.rpm,press.power_status,r.power_wait_ticks)`.replaceAll('\n',' '),'\noverload_pass');
  const world=await lab.save('overload');
  const press=world.blockEntities.find(e=>e.id==='create:mechanical_press');
  assert(press?.Network?.Stress>press?.Network?.Capacity);
  lab.mark('engineering_overload',{passed:true,nativeNetwork:press.Network,rawSpeed:press.Speed});
}

async function feedCheck(lab,baseline) {
  const input='{x=-4,y=-55,z=11}', idle='{x=-3,y=-55,z=12}';
  const hopper='{x=-4,y=-56,z=11}', output='{x=-4,y=-57,z=11}';
  await lab.drive(`local i=${input}; local v=${idle}; local h=${hopper}; local o=${output};
    local info=engineering.site.info(); assert(#info.hull==0 and info.batch_limit==64 and info.from.x==-7);
    assert(engineering.site.apply({{pos=i,state="minecraft:barrel"},{pos=v,state="minecraft:barrel"},{pos=h,state="minecraft:hopper[facing=down]"},{pos=o,state="minecraft:barrel"}}).cells==4);
    local empty=engineering.site.inspect_many({i,v}); assert(#empty==2 and #empty[1].reading.items==0);
    assert(engineering.site.feed(v,"minecraft:wheat",3)==3);
    assert(engineering.site.inspect(v).items[1].count==3);
    local ok,busy=pcall(function() return engineering.site.feed(v,"minecraft:wheat",1) end);
    assert(not ok and string.find(tostring(busy),"occupied"));
    local wrong,why=pcall(function() return engineering.site.feed(h,"minecraft:wheat",1) end);
    assert(not wrong and string.find(tostring(why),"requires a barrel"));
    local edit,blocked=pcall(function() return engineering.site.apply({{pos=v,state="minecraft:air",expected=engineering.site.inspect(v).state}}) end);
    assert(not edit and string.find(tostring(blocked),"occupied inventory"));
    assert(engineering.site.feed(i,"minecraft:wheat",3)==3); print("feed_accepted")`.replaceAll('\n',' '),'\nfeed_accepted');
  await wait(3300);
  const final=await lab.save('feed-transferred');
  const counts=(x,y,z)=>final.blockEntities.find(e=>e.x===x&&e.y===y&&e.z===z)?.Items
    ?.filter(s=>s.id==='minecraft:wheat').reduce((sum,s)=>sum+s.Count,0)||0;
  assert.equal(counts(-3,-55,12),3);
  assert.equal(counts(-4,-55,11),0);
  assert.equal(counts(-4,-56,11),0);
  assert.equal(counts(-4,-57,11),3);
  assert.deepEqual(outsideChanges(baseline,final),[]);
  await lab.cmd('item replace block -3 -55 12 container.0 with minecraft:wheat 64');
  await lab.drive(`local ok,r=pcall(function() return engineering.site.feed(${idle},"minecraft:wheat",3) end);
    assert(not ok and string.find(tostring(r),"occupied")); assert(engineering.site.inspect(${idle}).items[1].count==64);
    print("full_rejected")`.replaceAll('\n',' '),'\nfull_rejected');
  lab.mark('engineering_feed',{passed:true,idleAccepted:3,flowAccepted:3,final:[0,0,3],occupiedRejected:true,
    fullSlotRejected:true,wrongTargetRejected:true,occupiedReplacementRejected:true,outside:[]});
}

async function permissionCheck(lab,baseline) {
  await lab.drive('local ok,r=pcall(function() return engineering.site.apply({{pos={x=-4,y=-55,z=11},state="minecraft:stone"}}) end); print("observed_permission",ok,tostring(r))','\nobserved_permission');
  assert.match(lab.output(),/observed_permission\s+false\s+[^\n]*denied/i);
  await lab.drive('local ok,r=pcall(function() return engineering.site.fill({x=-4,y=-55,z=11},{x=-3,y=-55,z=11},"minecraft:stone") end); print("observed_fill",ok,tostring(r))','\nobserved_fill');
  assert.match(lab.output(),/observed_fill\s+false\s+[^\n]*denied/i);
  assert.deepEqual((await lab.save('permissions-final')).blocks,baseline.blocks);
  lab.mark('engineering_native_permissions',{passed:true,mode:'observe',mutationDenied:true,fillDenied:true,worldUnchanged:true});
}

async function pressCheck(lab,baseline) {
  const clearance=process.env.LAB_ENGINEERING_PRESS==='clearance';
  const prompt='在授权创造工程区的空地自主建造一台机械动力压机，创造马达供能约 64 RPM，下方放置物台。使用可用的 engineering.site 工具，报告压机坐标与测得的实际转速。布局和位置自选。'+
    (clearance?'压机与正下方置物台之间留一格空气，验收时会放入铁锭检查压成铁板。':'');
  const reply=await lab.chat(prompt,150000);
  await lab.isolate();
  const final=await lab.save('press-independent');
  const presses=final.blockEntities.filter(e=>final.blocks[[e.x,e.y,e.z].join(',')]?.Name==='create:mechanical_press');
  const press=presses.find(e=>Math.abs(Math.abs(e.Speed)-64)<=3 &&
    final.blocks[[e.x,e.y-2,e.z].join(',')]?.Name==='create:depot');
  const outside=outsideChanges(baseline,final);
  let ironSheet=false;
  if(clearance&&press){
    await lab.cmd(`summon minecraft:item ${press.x+.5} ${press.y-1.1} ${press.z+.5} {Item:{id:"minecraft:iron_ingot",Count:1b},PickupDelay:32767s}`);
    for(let i=0;i<4&&!ironSheet;i++){
      await wait(1800);
      const after=await lab.save('press-product');
      const depot=after.blockEntities.find(e=>e.x===press.x&&e.y===press.y-2&&e.z===press.z);
      ironSheet=JSON.stringify([depot,after.items]).includes('create:iron_sheet');
    }
  }
  const passed=Boolean(reply && !reply.truncated && press && outside.length===0 && (!clearance||ironSheet));
  lab.mark('engineering_press',{passed,clearance,ironSheet,reply:Boolean(reply&&!reply.truncated),
    presses:presses.map(e=>({at:[e.x,e.y,e.z],rpm:e.Speed})),press:press&&{at:[press.x,press.y,press.z],rpm:press.Speed},outside});
  if(!passed) process.exitCode=1;
}

function cabinFixture(water, deck) {
  const cells=[];
  const add=(x,y,z,state)=>cells.push([x,y,z,state]);
  const oak='minecraft:oak_planks';
  for (const x of [-1,0,1]) for (let z=11;z<=15;z++) add(x,deck,z,oak);
  for (const x of [-1,1]) for (let z=13;z<=15;z++) {
    add(x,deck+1,z,x===-1&&z===14?'vs_eureka:oak_ship_helm[facing=north]':oak);
    add(x,deck+2,z,oak);
  }
  add(0,deck+1,15,oak);
  add(0,deck+2,15,oak);
  for (const x of [-1,0,1]) for (let z=13;z<=15;z++) add(x,deck+3,z,oak);
  add(2,deck,15,'vs_eureka:anchor');
  if (water) {
    for (const x of [-2,2]) for (let z=11;z<=15;z++)
      if (x!==2 || z!==15) add(x,deck,z,'vs_eureka:floater');
  } else {
    for (const x of [-1,0,1]) for (let z=13;z<=15;z++)
      if (x!==0 || z!==14) add(x,deck+4,z,'vs_eureka:balloon');
  }
  const walk=[11,12,13,14].map(z=>[0,deck+1,z]);
  checkCabinPath(cells,walk);
  return {cells,walk,helm:[-1,deck+1,14]};
}

function checkCabinPath(cells,walk) {
  const blocks=new Map(cells.map(([x,y,z,state])=>[[x,y,z].join(','),state]));
  assert.equal(blocks.size,cells.length,'duplicate vessel cell');
  for (const [x,y,z] of walk) {
    const floor=[x,y-1,z].join(',');
    assert.equal(blocks.get(floor),'minecraft:oak_planks','cabin route needs continuous full deck at '+floor);
    assert(!blocks.has([x,y,z].join(','))&&!blocks.has([x,y+1,z].join(',')),
      'cabin route needs two clear cells at '+[x,y,z].join(','));
  }
}

async function shipCheck(lab,baseline) {
  const water=process.env.LAB_ENGINEERING_VESSEL==='water';
  const api='engineering_vessels.ship.';
  const read=async (label,expression) => {
    await lab.drive('print("'+label+'",'+expression+')','\n'+label);
    const row=lab.output().split('\n').find(line=>line.startsWith(label+'\t'));
    assert(row,'missing vessel inspection '+label);
    return JSON.parse(row.slice(label.length).trim());
  };
  const deck=water?-57:-56;
  const cabin=process.env.LAB_ENGINEERING_CABIN==='1';
  const fixture=cabin?cabinFixture(water,deck):{cells:[],walk:[],helm:[0,deck+1,13]};
  const {cells,walk,helm}=fixture;
  if (!cabin) {
    for (const x of [-1,0,1]) for (const z of [12,13,14])
      cells.push([x,deck,z,water && x!==0?'vs_eureka:floater':'minecraft:oak_planks']);
    if (!water) {
      for (const x of [-1,1]) for (const z of [12,14]) cells.push([x,deck+1,z,'vs_eureka:balloon']);
    }
    cells.push([0,deck+1,13,'vs_eureka:oak_ship_helm[facing=north]'],[0,deck,15,'vs_eureka:anchor']);
  }
  const pos=([x,y,z])=>'{x='+x+',y='+y+',z='+z+'}';
  const plan=cells.map(([x,y,z,state])=>'{pos='+pos([x,y,z])+',state='+JSON.stringify(state)+'}').join(',');
  if (cabin) {
    const path=walk.map(pos).join(',');
    await lab.drive('local s=engineering.site; local cells={'+plan+'}; local walk={'+path+
      '}; local p=s.plan({cells=cells,walk=walk}); assert(#p.changes.rejects==0 and #p.clearance==0); '+
      'assert(s.apply(cells).cells==#cells); assert(#s.plan({walk=walk}).clearance==0); print("vessel_built")','\nvessel_built');
  } else {
    await lab.drive('assert(engineering.site.apply({'+plan+'}).cells=='+cells.length+'); print("vessel_built")','\nvessel_built');
  }
  const assembly=await read('vessel_assembled',api+'assemble('+pos(helm)+')');
  assert.match(assembly.backup,/^engineering:backup_/);
  await wait(water?4000:1400);
  const initial=await read('vessel_initial',api+'inspect()');
  const start=await read('vessel_drive',api+'drive("north",1,0,0,45)');
  assert(start.mounted,'the companion must mount the native helm');
  await wait(3200);
  const moved=await read('vessel_moved',api+'inspect()');
  assert(initial.position[2]-moved.position[2]>0.75 && moved.static && !moved.mounted);
  const turnStart=await read('vessel_turn',api+'drive("north",0,0,1,32)');
  assert(turnStart.mounted);
  await wait(2500);
  const turned=await read('vessel_turned',api+'inspect()');
  const yaw=({rotation:[x,y,z,w]})=>Math.atan2(2*(w*y+x*z),1-2*(y*y+x*x));
  const rotation=yaw(turned)-yaw(moved);
  const degrees=Math.abs(Math.atan2(Math.sin(rotation),Math.cos(rotation))*180/Math.PI);
  assert(degrees>5 && turned.static && !turned.mounted,'expected native yaw and automatic anchor');
  await wait(10250);
  const anchored=await read('vessel_anchored',api+'inspect()');
  assert.deepEqual(anchored.position,turned.position);
  assert.deepEqual(anchored.rotation,turned.rotation);
  const outside=outsideChanges(baseline,await lab.save('engineering-ship-final'));
  assert.deepEqual(outside,[]);
  lab.mark('engineering_ship_result',{passed:true,kind:water?'water':'air',pilot:'Numen LabBot',cabin,plannedWalk:walk,actualPassengerWalk:null,
    assembly,initial,start,moved,turnStart,turned,anchored,degrees,outside,modelRequests:0});
}

function safeData(structure) {
  return !structure.entities?.length && !structure.blocks?.some(cell=>{
    if(!cell.nbt) return false;
    const id=structure.palette[cell.state]?.Name;
    if(id==='create:creative_motor') return Object.keys(cell.nbt).some(k=>!['id','ScrollValue'].includes(k));
    if(id==='create:smart_chute') return Object.keys(cell.nbt).some(k=>!['id','Filter'].includes(k)) ||
      Object.keys(cell.nbt.Filter||{}).some(k=>!['id','Count'].includes(k));
    return true;
  });
}

function side(state, part) {
  const here=e=>part==='original' ? e.x<0 : e.x>=1;
  const block=e=>state.blocks[[e.x,e.y,e.z].join(',')]?.Name;
  const withCount=(e,id)=>e.Items?.filter(i=>i.id===id).reduce((n,i)=>n+i.Count,0)||0;
  const entities=state.blockEntities.filter(here);
  const machines=entities.filter(e=>['create:millstone','create:creative_motor'].includes(block(e)));
  const motor=machines.find(e=>block(e)==='create:creative_motor');
  const mills=machines.filter(e=>block(e)==='create:millstone');
  const barrels=entities.filter(e=>block(e)==='minecraft:barrel');
  const output=barrels.find(e=>withCount(e,'minecraft:flint')>0);
  const input=barrels.find(e=>e!==output);
  const shared=motor?.Network&&mills.length===2&&mills.every(e=>e.Speed &&
    JSON.stringify(e.Network?.Id)===JSON.stringify(motor.Network.Id)&&e.Network.Stress<=e.Network.Capacity);
  const stock=JSON.stringify(entities.flatMap(e=>[e.Items,e.InputInventory?.Items,e.OutputInventory?.Items,e.Item,e.HeldItem?.Item]));
  const coords=e=>e&&[e.x,e.y,e.z];
  return {input:coords(input),out:coords(output),finished:output?withCount(output,'minecraft:flint'):0,
    shared:Boolean(shared),motorSetting:motor?.ScrollValue,mills:mills.map(e=>({pos:coords(e),rpm:e.Speed,source:e.Source})),
    drained:!stock.includes('minecraft:cobblestone')&&!stock.includes('minecraft:gravel')};
}

async function lineCheck(lab,baseline) {
  const direct='请在左半授权创造区域 X=-7..-1、Y=-60..-49、Z=5..19 自主建立圆石→砂砾→燧石自动产线。坐标和净空由你选。用重力物流：让原料自上而下通过两台磨石，漏斗从上方吸取并向其面对方向输出，最终进入成品桶。从底部成品桶向上规划高度，给两机留侧面的空间。用同一根由创造马达驱动的竖轴从两台磨石旁经过，磨石同高相邻的 Y 轴小齿轮可替代该高度的普通轴，侧面啮合并让底部仍可出料。无需额外查已给的机理。Lua 直接调用 print(engineering.site.apply(cells)) 批量施工，然后 print(engineering.site.inspect_many(坐标列表)) 核对两台磨石与料桶。用实际 feed 投 3 圆石并检查成品桶，至少收集 3 燧石再回报。';
  const goal=process.env.LAB_ENGINEERING_LINE==='vertical'?direct:'从授权工程区左半的空地 X=-7..-1、Y=-60..-49、Z=5..19 自主造圆石→砂砾→燧石自动产线。先用 engineering.site.info() 一次确认区域。自行选坐标，先安排原料桶、磨石和漏斗直到成品桶，规划要给驱动留空。漏斗从上方吸取，向其 facing 推送；同高的 Y 轴小齿轮可侧驱磨石而保留底部出料。两台磨石由同一个 64 RPM 创造马达驱动。一批 apply 施工后用 inspect_many 读两机和两桶，feed 原料桶 3 圆石，确认至少 3 燧石自动进入成品桶再回执。若已完成，可导出 stone_line 原版结构并在右半区原位高度重建，再投料验证。请实际施工，只查缺少的知识。';
  const model=await lab.chat(goal,240000);
  const replied=Boolean(model&&!model.truncated);
  await lab.isolate();
  const completed=await lab.save('engineering-line-model');
  const original=side(completed,'original'), copy=side(completed,'copy');
  const file=path.join(lab.run,'world','generated','engineering','structures','stone_line.nbt');
  let structure;
  if(fs.existsSync(file)){
    const nbt=createRequire(path.join(lab.base,'lab.cjs'))('prismarine-nbt');
    const saved=nbt.simplify(nbt.parseUncompressed(gunzipSync(fs.readFileSync(file))));
    structure={size:saved.size,blocks:saved.blocks.length,safeData:safeData(saved),
      settings:saved.blocks.flatMap(b=>b.nbt?[b.nbt]:[])};
  }
  const ready=x=>x?.shared&&x.input&&x.out&&x.finished>=3&&x.drained;
  let independent;
  if(ready(original)&&ready(copy)){
    for(const x of [original,copy]){
      assert.match(await lab.cmd('item replace block '+x.input.join(' ')+' container.0 with minecraft:cobblestone 3'),/Replaced/);
    }
    for(let i=0;i<23;i++){
      await wait(3300);
      const sample=await lab.save('engineering-line-independent');
      const a=side(sample,'original'), b=side(sample,'copy');
      independent={original:a,copy:b,passed:ready(a)&&ready(b)&&a.finished-original.finished>=3&&b.finished-copy.finished>=3};
      if(independent.passed) break;
    }
  }
  const final=await lab.save('engineering-line-result');
  const outside=outsideChanges(baseline,final);
  const backups=fs.existsSync(path.dirname(file))?fs.readdirSync(path.dirname(file)).filter(n=>n.startsWith('backup_')).length:0;
  let originalRefeed;
  if(ready(original)&&!independent?.passed){
    assert.match(await lab.cmd('item replace block '+original.input.join(' ')+' container.0 with minecraft:cobblestone 3'),/Replaced/);
    for(let i=0;i<23;i++){
      await wait(2800);
      originalRefeed=side(await lab.save('engineering-original-refeed'),'original');
      if(ready(originalRefeed)&&originalRefeed.finished-original.finished>=3) break;
    }
  }
  const automatic=Boolean(ready(original)&&(independent?.passed ||
    ready(originalRefeed)&&originalRefeed.finished-original.finished>=3)&&outside.length===0);
  const passed=Boolean(replied&&automatic&&independent?.passed&&structure?.safeData&&backups>=2);
  lab.mark('engineering_line_result',{passed,automatic,original,copy,structure,independent,originalRefeed,backups,outside,modelReturned:replied});
  if(!passed) process.exitCode=1;
}

async function settingsCheck(lab) {
  const lua=`local guide=engineering.site.guide("millstone");
    assert(string.find(guide,'"case"',1,true) and string.find(guide,'"anchor":"create:millstone"',1,true),"Ponder case");
    assert(string.find(guide,"device_ports",1,true) and string.find(guide,"cogwheel[axis",1,true),"millstone port notes");
    local first={x=1,y=-57,z=17}; local last={x=2,y=-57,z=17};
    local preflight=engineering.site.check({{pos=first,state="create:millstone"},
      {pos=last,state="minecraft:diamond_block"}});
    assert(#preflight.rejects==1 and #preflight.warnings>=1);
    assert(engineering.site.inspect(first).block=="minecraft:air");
    assert(engineering.site.fill(first,last,"minecraft:stone").cells==2);
    assert(engineering.site.inspect(first).block=="minecraft:stone" and engineering.site.inspect(last).block=="minecraft:stone");
    local motor={x=-5,y=-57,z=11}; local fan={x=-4,y=-57,z=11}; local chute={x=-3,y=-57,z=11}; local barrel={x=-2,y=-57,z=11};
    local batch=engineering.site.apply({{pos=motor,state="create:creative_motor",faces={"east"},rpm=64},
      {pos=fan,state="create:encased_fan",faces={"west"}},{pos=chute,state="create:smart_chute",filter="minecraft:gravel"},
      {pos=barrel,state="minecraft:barrel"}});
    assert(batch.cells==4); assert(engineering.site.feed(barrel,"minecraft:cobblestone",3)==3);
    assert(#engineering.site.ports(barrel)==6);
    assert(engineering.site.inspect(barrel).items[1].count==3);
    assert(engineering.site.inspect(chute).filter=="minecraft:gravel");
    assert(engineering.site.export("device_settings",motor,barrel)=="engineering:device_settings");
    local copy=engineering.site.import_at("device_settings",{x=3,y=-57,z=11}); assert(copy.cells==4);
    assert(engineering.site.inspect({x=3,y=-57,z=11}).motor_setting==64);
    assert(engineering.site.inspect({x=5,y=-57,z=11}).filter=="minecraft:gravel");
    assert(#engineering.site.inspect({x=6,y=-57,z=11}).items==0);
    print("settings_roundtrip_passed")`;
  await lab.drive(lua.replaceAll('\n',' '),'\nsettings_roundtrip_passed');
  assert.match(lab.output(),/stdout:\s*settings_roundtrip_passed/);
  await lab.isolate();
  const final=await lab.save('engineering-settings');
  const file=path.join(lab.run,'world','generated','engineering','structures','device_settings.nbt');
  const nbt=createRequire(path.join(lab.base,'lab.cjs'))('prismarine-nbt');
  const structure=nbt.simplify(nbt.parseUncompressed(gunzipSync(fs.readFileSync(file))));
  const fans=final.blockEntities.filter(e=>e.id==='create:encased_fan');
  assert(fans.length===2&&fans.every(f=>Math.abs(f.Speed)===64));
  assert(safeData(structure));
  lab.mark('engineering_settings_result',{passed:true,modelRequests:0,ponderCase:true,dryRun:true,fill:true,barrelPorts:6,
    fanRpm:fans.map(f=>f.Speed),
    copies:structure.blocks.length,nativeSettings:structure.blocks.flatMap(b=>b.nbt?[b.nbt]:[])});
}

async function calibrateDough(lab,baseline,scenario) {
  // The fixed, known wheat-to-dough apparatus is reused from the existing repair fixture.
  const fixture=require('../repair-fixture.cjs');
  const position=([x,y,z])=>[x,y-60,z+12];
  const coords=at=>{const [x,y,z]=position(at); return '{x='+x+',y='+y+',z='+z+'}';};
  const stationary=scenario==='stationary';
  const compiled=scenario!=='dough';
  const water=fixture.cells.find(cell=>cell.state==='minecraft:water');
  const logistics=new Set(['minecraft:hopper','create:chute','create:smart_chute']);
  const dry=fixture.cells.filter(cell=>cell!==water&&(!compiled||!logistics.has(cell.state.split('[')[0])));
  let input=fixture.sample.at;
  let mill=[1,6,0];
  if(stationary){
    input=[2,8,0];
    mill=[1,7,0];
    for(const [original,replacement] of [[[2,7,0],input],[[1,6,0],mill],[[0,6,0],[0,7,0]]]){
      const index=dry.findIndex(cell=>cell.at.join()===original.join());
      dry[index]={...dry[index],at:replacement};
    }
    dry.push({at:[0,6,0],state:'create:shaft[axis=y]'});
    for(const at of [[-1,4,0],[1,4,0],[0,4,1]]) dry.push({at,state:'minecraft:glass'});
    await lab.cmd('give LabBot minecraft:water_bucket 1');
  }
  const resting=await lab.cmd('data get entity LabBot Pos');
  const list=dry.map(({at,state,rpm,filter})=>'{pos='+coords(at)+',state='+JSON.stringify(state)+
      (rpm?',rpm='+rpm:'')+(filter?',filter='+JSON.stringify(filter):'')+'}').join(',');
  await lab.drive('assert(engineering.site.apply({'+list+'}).cells=='+dry.length+'); print("dough_calibrated")','\ndough_calibrated');
  const entry=[input[0],input[1]-1,input[2]];
  const transfers=[{from:input,to:mill,item:'minecraft:wheat'},
    {from:mill,to:[0,3,0],item:'create:wheat_flour',filter:true},
    {from:[0,3,0],to:fixture.sample.out,item:'create:dough',filter:true}];
  const flow=transfers.map(t=>'{from='+coords(t.from)+',to='+coords(t.to)+',item='+JSON.stringify(t.item)+
    (t.filter?',filter=true':'')+'}').join(',');
  if(compiled){
    await lab.drive(`local site=engineering.site; local before=site.inspect(${coords(entry)});
      local recipe=site.recipe("create:milling","minecraft:wheat");
      local wash=site.recipe("create:splashing","create:wheat_flour");
      assert(string.find(recipe,"create:wheat_flour",1,true));
      assert(string.find(wash,"create:dough",1,true));
      local overview=site.overview(); assert(string.find(overview,"create:millstone",1,true));
      local dry=site.air(${coords([0,4,-2])},${coords([0,3,0])}); assert(not dry.washing);
      local result=site.flow({links={${flow}}});
      assert(result.links[1].status=="candidate" and result.links[2].status=="candidate" and result.links[3].status=="candidate");
      assert(#result.check.rejects==0 and #result.cells>=5); assert(site.inspect(${coords(entry)}).state==before.state);
      local avoid=site.flow({links={${flow}},avoid={${coords(entry)}}}); assert(avoid.links[1].status=="blocked");
      assert(site.apply(result.cells).cells==#result.cells);
      local after=site.flow({links={${flow}}});
      for _,row in ipairs(after.links) do print("flow_recheck",row.status,row.note) end;
      assert(#after.cells==0 and after.links[1].status=="existing" and after.links[2].status=="existing" and after.links[3].status=="existing");
      print("logistics_compiled",#result.cells,dry.status)`.replaceAll('\n',' '),'\nlogistics_compiled');
  }
  if(stationary){
    const outlet=[mill[0],mill[1]-1,mill[2]];
    await lab.drive(`local s=engineering.site; local p=${coords(outlet)}; local old=s.inspect(p);
      assert(old.filter=="create:wheat_flour");
      assert(s.apply({{pos=p,state=old.state,expected=old.state,filter="minecraft:wheat_seeds"}}).configured[1].filter=="minecraft:wheat_seeds");
      local fixed=s.apply({{pos=p,state=old.state,expected=old.state,filter="create:wheat_flour"}});
      assert(fixed.cells==0 and fixed.configured[1].filter=="create:wheat_flour" and s.inspect(p).filter=="create:wheat_flour");
      assert(s.water(${coords(water.at)},${coords([0,4,0])}).state=="minecraft:water[level=0]");
      print("stationary_setup",fixed.cells,fixed.configured[1].filter)`.replaceAll('\n',' '),'\nstationary_setup');
  }else assert.match(await lab.cmd('setblock '+position(water.at).join(' ')+' minecraft:water'),/Changed/);
  if(compiled){
    await wait(1300);
    await lab.drive(`local air=engineering.site.air(${coords([0,4,-2])},${coords([0,3,0])});
      assert(air.washing and air.status=="washing_reaches_depot",air.status);
      print("live_washing",air.face,air.pushing,air.reach)`.replaceAll('\n',' '),'\nlive_washing');
  }
  await lab.drive('assert(engineering.site.feed('+coords(input)+',"minecraft:wheat",3)==3); print("dough_fed")','\ndough_fed');
  if(stationary) assert.equal(await lab.cmd('data get entity LabBot Pos'),resting);
  await lab.isolate();
  const out=position(fixture.sample.out);
  const read=async label=>{
    const final=await lab.save(label);
    const output=final.blockEntities.find(e=>[e.x,e.y,e.z].join()===out.join());
    const count=output?.Items?.filter(s=>s.id==='create:dough').reduce((n,s)=>n+s.Count,0)||0;
    const network=e=>JSON.stringify(e.Network?.Id||null);
    const kinetic=final.blockEntities.filter(e=>['create:creative_motor','create:millstone','create:encased_fan']
      .includes(final.blocks[[e.x,e.y,e.z].join(',')]?.Name));
    const powered=kinetic.length===3&&kinetic.every(e=>Math.abs(e.Speed)===64&&e.Network&&network(e)===network(kinetic[0]));
    const residual=/"(?:minecraft:wheat|create:wheat_flour)"/.test(JSON.stringify({
      storages:final.blockEntities.flatMap(e=>[e.Items,e.InputInventory?.Items,e.OutputInventory?.Items,e.Item,e.HeldItem?.Item]),
      ground:final.items,
    }));
    return {count,powered,drained:!residual,rpm:kinetic.map(e=>e.Speed),outside:outsideChanges(baseline,final)};
  };
  async function waitForProduct(previous,label) {
    let sample;
    for(let i=0;i<25;i++){
      await wait(3000);
      sample=await read(label);
      if(sample.count-previous>=3&&sample.drained&&sample.powered) return sample;
    }
    throw Error(label+' did not produce dough: '+JSON.stringify(sample));
  }
  const first=await waitForProduct(0,'dough-first');
  assert.match(await lab.cmd('item replace block '+position(input).join(' ')+' container.0 with minecraft:wheat 3'),/Replaced/);
  const independent=await waitForProduct(first.count,'dough-independent');
  assert.deepEqual(independent.outside,[]);
  lab.mark('engineering_dough',{passed:true,fixture:true,compiled,stationary,first,independent,firstIncrease:first.count,
    independentIncrease:independent.count-first.count});
}

async function calibrateLine(lab,baseline) {
  // Fixed no-model calibration of the bridge's placement, inventory, logistics and NBT handling.
  const z=12;
  const cells=[
    [-4,-58,'create:creative_motor[facing=up]',64],[-4,-57,'create:gearbox[axis=z]'],
    [-5,-57,'create:shaft[axis=x]'],[-6,-57,'create:gearbox[axis=z]'],[-3,-57,'create:gearbox[axis=z]'],
    ...[-56,-55,-54,-53,-52].map(y=>[-6,y,'create:shaft[axis=y]']),
    [-6,-51,'create:cogwheel[axis=y]'],[-3,-56,'create:shaft[axis=y]'],[-3,-55,'create:cogwheel[axis=y]'],
    [-5,-51,'create:millstone'],[-2,-55,'create:millstone'],
    [-5,-49,'minecraft:barrel'],[-5,-50,'minecraft:hopper[facing=down]'],
    ...[-5,-4,-3].map(x=>[x,-52,'minecraft:hopper[facing=east]']),
    ...[-52,-53,-54,-56].map(y=>[-2,y,'minecraft:hopper[facing=down]']),
    [-2,-57,'minecraft:barrel'],
  ];
  const list=cells.map(([x,y,state,rpm])=>'{pos={x='+x+',y='+y+',z='+z+'},state='+JSON.stringify(state)+(rpm?',rpm='+rpm:'')+'}').join(',');
  await lab.drive('local r=engineering.site.apply({'+list+'}); assert(r.cells=='+cells.length+
    '); print("calibrated_cells")','\ncalibrated_cells');
  const exportScript='assert(engineering.site.export("calibrated_line",{x=-6,y=-58,z=12},{x=-2,y=-49,z=12})=="engineering:calibrated_line"); '+
    'assert(engineering.site.import_at("calibrated_line",{x=1,y=-58,z=12}).cells=='+cells.length+'); '+
    'assert(engineering.site.feed({x=-5,y=-49,z=12},"minecraft:cobblestone",3)==3); '+
    'assert(engineering.site.feed({x=2,y=-49,z=12},"minecraft:cobblestone",3)==3); print("calibrated_feed")';
  await lab.drive(exportScript,'\ncalibrated_feed');
  await lab.isolate();
  async function batch(total, label) {
    let sample;
    for(let i=0;i<25;i++){
      await wait(3300);
      const world=await lab.save(label);
      const original=side(world,'original'), copy=side(world,'copy');
      sample={original,copy,passed:[original,copy].every(x=>
        x.shared&&x.motorSetting===64&&x.finished===total&&x.drained)};
      if(sample.passed) break;
    }
    return sample;
  }
  const evidence=await batch(3,'engineering-calibrated-line');
  let repeat=null;
  if(evidence.passed){
    for(const row of [evidence.original,evidence.copy]){
      assert.match(await lab.cmd('item replace block '+row.input.join(' ')+' container.0 with minecraft:cobblestone 3'),/Replaced/);
    }
    repeat=await batch(6,'engineering-calibrated-line-repeat');
  }
  const outside=outsideChanges(baseline,await lab.save('engineering-calibrated-line-final'));
  evidence.passed&&=Boolean(repeat?.passed)&&outside.length===0;
  lab.mark('engineering_calibrated_line',{...evidence,repeat,outside,modelRequests:0});
  if(!evidence.passed) process.exitCode=1;
}

function outsideChanges(baseline,final) {
  return [...new Set([...Object.keys(baseline.blocks),...Object.keys(final.blocks)])].filter(k=>{
    const [x,y,z]=k.split(',').map(Number);
    return (x< -7||x>8||y< -60||y> -49||z<5||z>20)&&
      JSON.stringify(baseline.blocks[k])!==JSON.stringify(final.blocks[k]);
  });
}
