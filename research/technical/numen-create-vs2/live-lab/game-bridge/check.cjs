const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {gunzipSync}=require('node:zlib');
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
  const goal='在授权创造施工区内给我造一台会转的 Create 风扇，先在 X=-6..-1、Y=-60..-55、Z=9..13 自选格建造。创造马达的轴端在 facing 一侧，风扇只在 facing 的反面接轴。直接用 engineering.site.apply(cells)，无需取物。cells 是由 {pos={x=...,y=...,z=...},state="create:..."} 组成的列表，空地默认无需 expected。用 engineering.site.inspect(pos) 回读实际转速。用 engineering.site.export("fan_demo",最小角,最大角) 保存紧包围原件的标准 NBT，再用 engineering.site.import_at("fan_demo",{x=3,y=-60,z=9}) 在空区重建。检查两台风扇的实际转速后用中文回执。';
  const model=await lab.chat(goal,200000);
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
      registry:saved.palette?.map(e=>e.Name),noEntityData:!saved.entities?.length&&!saved.blocks?.some(e=>e.nbt)};
  }
  const backupDir=path.dirname(file);
  const backups=fs.existsSync(backupDir)?fs.readdirSync(backupDir).filter(n=>n.startsWith('backup_')).length:0;
  const outside=[...new Set([...Object.keys(baseline.blocks),...Object.keys(final.blocks)])].filter(k=>{
    const [x,y,z]=k.split(',').map(Number);
    return (x< -7||x>8||y< -60||y> -55||z<5||z>20)&&
      JSON.stringify(baseline.blocks[k])!==JSON.stringify(final.blocks[k]);
  });
  const pass=Boolean(model&&fans.some(e=>e.at[0]<0&&Math.abs(e.rpm)>0)&&
    fans.some(e=>e.at[0]>=3&&Math.abs(e.rpm)>0)&&structure?.noEntityData&&structure.registry.includes('create:encased_fan')&&
    backups>=2&&outside.length===0);
  lab.mark('engineering_result',{passed:pass,fans,structure,backups,outside,modelReturned:Boolean(model)});
  if(!pass) process.exitCode=1;
};
