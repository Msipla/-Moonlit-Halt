const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {gunzipSync}=require('node:zlib');
const {setTimeout:wait}=require('node:timers/promises');
const {createRequire}=require('node:module');

module.exports=async function check(lab){
  // Inert floor prevents grass decay beneath opaque placements from obscuring the boundary check.
  await lab.cmd('fill -7 -61 5 8 -61 20 minecraft:stone');
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
  if(process.env.LAB_ENGINEERING_SMOKE==='feed') return feedCheck(lab,baseline);
  if(process.env.LAB_ENGINEERING_SMOKE==='permissions') return permissionCheck(lab,baseline);
  if(process.env.LAB_ENGINEERING_SMOKE==='ship') return shipCheck(lab,baseline);
  if(process.env.LAB_ENGINEERING_SMOKE==='line') return calibrateLine(lab,baseline);
  if(process.env.LAB_ENGINEERING_SMOKE==='1') return settingsCheck(lab);
  if(process.env.LAB_ENGINEERING_LINE==='1') return lineCheck(lab,baseline);
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
  assert.deepEqual((await lab.save('permissions-final')).blocks,baseline.blocks);
  lab.mark('engineering_native_permissions',{passed:true,mode:'observe',mutationDenied:true,worldUnchanged:true});
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

async function shipCheck(lab,baseline) {
  const api='engineering_vessels.ship.';
  const read=async (label,expression) => {
    await lab.drive('print("'+label+'",'+expression+')','\n'+label);
    const row=lab.output().split('\n').find(line=>line.startsWith(label+'\t'));
    assert(row,'missing vessel inspection '+label);
    return JSON.parse(row.slice(label.length).trim());
  };
  const cells=[];
  for (const x of [-1,0,1]) for (const z of [12,13,14]) cells.push([x,-56,z,'minecraft:oak_planks']);
  for (const x of [-1,1]) for (const z of [12,14]) cells.push([x,-55,z,'vs_eureka:balloon']);
  cells.push([0,-55,13,'vs_eureka:oak_ship_helm[facing=north]'],[0,-56,15,'vs_eureka:anchor']);
  const plan=cells.map(([x,y,z,state])=>'{pos={x='+x+',y='+y+',z='+z+'},state='+JSON.stringify(state)+'}').join(',');
  await lab.drive('assert(engineering.site.apply({'+plan+'}).cells=='+cells.length+'); print("vessel_built")','\nvessel_built');
  const assembly=await read('vessel_assembled',api+'assemble({x=0,y=-55,z=13})');
  assert.match(assembly.backup,/^engineering:backup_/);
  await wait(1400);
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
  lab.mark('engineering_ship_result',{passed:true,pilot:'Numen LabBot',assembly,initial,start,moved,turnStart,turned,anchored,degrees,outside,modelRequests:0});
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
  const goal='从授权工程区左半的空地 X=-7..-1、Y=-60..-49、Z=5..19 自主造圆石→砂砾→燧石自动产线。先用 engineering.site.info() 一次确认区域。自行选坐标，先安排原料桶、磨石和漏斗直到成品桶，规划要给驱动留空。漏斗从上方吸取，向其 facing 推送；同高的 Y 轴小齿轮可侧驱磨石而保留底部出料。两台磨石由同一个 64 RPM 创造马达驱动。一批 apply 施工后用 inspect_many 读两机和两桶，feed 原料桶 3 圆石，确认至少 3 燧石自动进入成品桶再回执。若已完成，可导出 stone_line 原版结构并在右半区原位高度重建，再投料验证。请实际施工，只查缺少的知识。';
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
  const lua=`local guide=engineering.site.guide("mechanical_arm"); assert(string.find(guide,"Ponder")); local motor={x=-5,y=-57,z=11}; local fan={x=-4,y=-57,z=11}; local chute={x=-3,y=-57,z=11}; local barrel={x=-2,y=-57,z=11};
    local batch=engineering.site.apply({{pos=motor,state="create:creative_motor",faces={"east"},rpm=64},
      {pos=fan,state="create:encased_fan",faces={"west"}},{pos=chute,state="create:smart_chute",filter="minecraft:gravel"},
      {pos=barrel,state="minecraft:barrel"}});
    assert(batch.cells==4); assert(engineering.site.feed(barrel,"minecraft:cobblestone",3)==3);
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
  lab.mark('engineering_settings_result',{passed:true,modelRequests:0,fanRpm:fans.map(f=>f.Speed),
    copies:structure.blocks.length,nativeSettings:structure.blocks.flatMap(b=>b.nbt?[b.nbt]:[])});
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
  let evidence;
  for(let i=0;i<25;i++){
    await wait(3300);
    const sampled=await lab.save('engineering-calibrated-line');
    const original=side(sampled,'original'), copy=side(sampled,'copy');
    evidence={original,copy,passed:[original,copy].every(x=>x.shared&&x.motorSetting===64&&x.finished===3&&x.drained)};
    if(evidence.passed) break;
  }
  const outside=outsideChanges(baseline,await lab.save('engineering-calibrated-line-final'));
  evidence.passed&&=outside.length===0;
  lab.mark('engineering_calibrated_line',{...evidence,outside,modelRequests:0});
  if(!evidence.passed) process.exitCode=1;
}

function outsideChanges(baseline,final) {
  return [...new Set([...Object.keys(baseline.blocks),...Object.keys(final.blocks)])].filter(k=>{
    const [x,y,z]=k.split(',').map(Number);
    return (x< -7||x>8||y< -60||y> -49||z<5||z>20)&&
      JSON.stringify(baseline.blocks[k])!==JSON.stringify(final.blocks[k]);
  });
}
