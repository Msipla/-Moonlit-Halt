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
  if(process.env.LAB_ENGINEERING_SMOKE==='ship') return shipCheck(lab,baseline);
  if(process.env.LAB_ENGINEERING_SMOKE==='line') return calibrateLine(lab,baseline);
  if(process.env.LAB_ENGINEERING_SMOKE==='1') return settingsCheck(lab);
  if(process.env.LAB_ENGINEERING_LINE==='1') return lineCheck(lab,baseline);
  const goal='在授权创造施工区内给我造一台会转的 Create 风扇，先在 X=-6..-1、Y=-60..-55、Z=9..13 自选格建造。创造马达的轴端在 facing 一侧，风扇只在 facing 的反面接轴。直接用 engineering.site.apply(cells)，无需取物。cells 是由 {pos={x=...,y=...,z=...},state="create:..."} 组成的列表，空地默认无需 expected。用 engineering.site.inspect(pos) 回读实际转速。用 engineering.site.export("fan_demo",最小角,最大角) 保存紧包围原件的标准 NBT，再用 engineering.site.import_at("fan_demo",{x=3,y=-60,z=9}) 在空区重建。检查两台风扇的实际转速后用中文回执。';
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
  const powerGoal='我们分阶段建一条圆石→砂砾→燧石的自动产线。现在先在创造施工区 X=-7..-1、Y=-60..-49、Z=5..19，用一台创造马达驱动两台磨石。用重力传输，所以第一台明显高于第二台，给前后漏斗与溜槽留净空。磨石从下方接轴或和同高的竖轴小齿轮啮合。齿轮箱只能同时覆盖两个轴，faces 是你设计的接轴面。直接调用 engineering.site.apply 可写入 {pos={x=...,y=...,z=...},state="create:gearbox",faces={"down","east"}}，马达可用 rpm=64。用 inspect 实测两个磨石转速和网络。每轮只写简短 Lua 和必要修正。两台同网后用一句中文回执，物流在下一阶段。';
  const powered=await lab.chat(powerGoal,170000);
  const power=side(await lab.save('engineering-line-power'),'original');
  lab.mark('engineering_power',{...power,modelReturned:Boolean(powered&&!powered.truncated)});
  let model;
  if(power.shared) {
    const flowGoal='继续刚接通动力的石料产线。根据现有建筑选进料桶、漏斗、溜槽和成品桶，圆石→砂砾→燧石，用你选定的布局自动转运。直接用 engineering.site.apply 写入；重复改动的格子需 expected=原状态。feed(pos,"minecraft:cobblestone",3) 给空原料桶投料。inspect(pos).items 确认成品桶实际收集至少 3 燧石。用 export("stone_line",最小角,最大角) 保存紧包围结构，再 import_at("stone_line",{x=1,y=最小Y,z=最小Z}) 重建。用副本再投 3 圆石并核对成品。每轮执行简短 Lua，完成后一句话回执。';
    model=await lab.chat(flowGoal,230000);
  }
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
  const ready=x=>x.shared&&x.input&&x.out&&x.finished>=3&&x.drained;
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
  const passed=Boolean(replied&&independent?.passed&&structure?.safeData&&backups>=2&&outside.length===0);
  lab.mark('engineering_line_result',{passed,original,copy,structure,independent,backups,outside,modelReturned:replied});
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
