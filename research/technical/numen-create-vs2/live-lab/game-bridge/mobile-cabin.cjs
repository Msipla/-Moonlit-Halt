const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {setTimeout:wait}=require('node:timers/promises');
const water=process.env.LAB_ENGINEERING_VESSEL==='water';
const pos=p=>`{x=${p[0]},y=${p[1]},z=${p[2]}}`;
const coord=p=>p.join(' ');
const ships='engineering_vessels.ship.';
const site='engineering.site.';

async function receipt(lab,label,expression) {
  await lab.drive(`print("${label}",${expression})`,'\n'+label);
  const line=lab.output().split('\n').find(s=>s.startsWith(label+'\t'));
  assert(line,label);
  return JSON.parse(line.slice(label.length).trim());
}

function cabin() {
  const d=water?-57:-54;
  const cells=new Map();
  const put=(x,y,z,kind='minecraft:oak_planks',rpm)=>cells.set([x,y,z].join(),[[x,y,z],kind,rpm]);
  for(let x=-2;x<=2;x++) for(let z=5;z<=17;z++) put(x,d,z);
  for(const x of [-2,2]) for(let z=9;z<=17;z++){
    put(x,d+1,z);
    put(x,d+2,z,z%3===1?'minecraft:oak_planks':'minecraft:glass');
  }
  for(let x=-2;x<=2;x++) for(let z=9;z<=17;z++) put(x,d+3,z);
  for(let x=-1;x<=1;x++) for(let y=d+1;y<=d+2;y++) put(x,y,17);
  const helm=[-1,d+1,9], mill=[1,d+2,14], input=[1,d+4,14], stock=[-1,d+1,13];
  put(...helm,'vs_eureka:oak_ship_helm[facing=north]');
  put(3,d,17,'vs_eureka:anchor');
  put(...stock,'minecraft:barrel');
  put(-1,d+1,15,'minecraft:oak_stairs[facing=east]');
  put(-1,d+1,16,'minecraft:oak_slab');
  put(1,d+1,14,'create:creative_motor[facing=up]',256);
  put(...mill,'create:millstone');
  put(1,d+3,14,'minecraft:hopper[facing=down]');
  put(...input,'minecraft:barrel');
  for (const x of water?[-3,3]:[-2,-1,0,1,2]) for(let z=water?5:9;z<=17;z++){
    if(water&&x===3&&z===17||!water&&x===1&&z===14) continue;
    put(x,d+(water?0:4),z,water?'vs_eureka:floater':'vs_eureka:balloon');
  }
  return {cells:[...cells.values()],walk:Array.from({length:11},(_,i)=>[0,d+1,i+6]),helm,mill,input,stock,d};
}

async function checkHullCap(lab) {
  const cells=[];
  for(let x=-8;x<8;x++) for(let z=-8;z<8;z++) for(const y of [-56,-55])
    cells.push([x,y,z,'minecraft:oak_planks']);
  cells[0][3]='vs_eureka:oak_ship_helm[facing=north]';
  cells.push([-9,-56,-8,'vs_eureka:anchor']);
  for(let i=0;i<cells.length;i+=32){
    const batch=cells.slice(i,i+32).map(([x,y,z,b])=>`{pos=${pos([x,y,z])},state="${b}"}`);
    const marker='limit_built_'+i;
    await lab.drive(`assert(${site}apply({${batch}}).cells==${batch.length}); print("${marker}")`,'\n'+marker);
  }
  const before=await lab.save('cap-before');
  await lab.drive(`local ok,r=pcall(function() return ${ships}assemble({x=-8,y=-56,z=-8}) end); print("limit_rejected",ok,tostring(r))`,'\nlimit_rejected');
  assert.match(lab.output(),/limit_rejected\s+false\s+[^\n]*hull exceeds 512 cells/);
  const after=await lab.save('cap-after');
  assert.deepEqual(after.blocks,before.blocks);
  await lab.drive(`assert(${site}apply({{pos={x=7,y=-55,z=7},state="minecraft:air",expected="minecraft:oak_planks"}}).cells==1); print("limit_trim")`,'\nlimit_trim');
  const allowed=await receipt(lab,'limit_accepted',ships+'assemble({x=-8,y=-56,z=-8})');
  assert.equal(allowed.cells,512);
  lab.mark('mobile_hull_cap',{rejectedCells:cells.length,acceptedCells:allowed.cells,
    denied:true,worldUnchangedOnRejection:true});
}

module.exports=async function(lab) {
  const resume=Boolean(process.env.LAB_MOBILE_MODEL==='1'&&process.env.LAB_MOBILE_RESUME);
  if(!resume){
    await lab.cmd('fill -20 -61 -23 20 -61 21 minecraft:stone');
    if(water){
      for(const a of ['-18 -60 -21 -18 -58 20','18 -60 -21 18 -58 20','-17 -60 -21 17 -58 -21','-17 -60 20 17 -58 20'])
        await lab.cmd('fill '+a+' minecraft:stone');
      await lab.cmd('fill -17 -60 -20 17 -58 19 minecraft:water');
    }
  }
  await lab.cmd('time set 4000');
  await lab.cmd('deop LabOwner');
  const baseline=await lab.save('mobile-empty');
  if(process.env.LAB_MOBILE_CAP==='1') return checkHullCap(lab);

  if(process.env.LAB_MOBILE_MODEL==='1'){
    const scenario=water?'河道补给站，包含驾驶位、储物货位和有顶的休息间。':'山脊观测救援站，包含驾驶位、医用储物和有顶的两人休息区。';
    const prompt=resume
      ? '接续隔离存档里你之前自主建造的补给船。保留船体和现有储物。只做这几步：查实际舵、锚、浮块、走道和加工件；将舵移出舱内两格净空走道，保持原有室内用途；核对船体允许搬运的部件，磨石用兼容的最短动力连接；尽快装配、核对在水面与短程推进、转向、锚停。按实际回执推进，助手原地施工，不扩大布局或重做百科勘察。'
      : '在授权工程区自主设计并完成一座'+(water?'水面':'飞行')+'移动基地。'+scenario+
        '先读 create_kinetics 的移动基地参考。留连续两格净高通道和侧置的实际磨石工位。自选位置、材料、朝向和布局。一次勘察后尽快开始直接施工，保持助手原地。逐批检查，核对方块、通道和磨石的实际转速。最后装配，实际推进转向并锚停。未实测的功能说明待验。';
    const answer=await lab.chat(prompt,resume?285000:420000);
    await lab.isolate();
    const final=await lab.save('mobile-model-final');
    lab.mark('mobile_model_result',{answer,fixture:false,resume,placedBlockDelta:Object.keys(final.blocks)
      .filter(p=>JSON.stringify(final.blocks[p])!==JSON.stringify(baseline.blocks[p])).length});
    return;
  }

  const {cells,helm,walk,mill,input,stock,d}=cabin();
  for(let i=0;i<cells.length;i+=32){
    const label='mobile_built_'+i;
    const batch=cells.slice(i,i+32).map(([p,b,rpm])=>`{pos=${pos(p)},state=${JSON.stringify(b)}${rpm?',rpm='+rpm:''}}`);
    await lab.drive(`local s=engineering.site; local cells={${batch}}; assert(#s.check(cells).rejects==0); assert(s.apply(cells).cells==#cells); print("${label}")`,'\n'+label);
  }
  await lab.drive(`assert(#${site}plan({walk={${walk.map(pos)}}}).clearance==0); print("mobile_clear")`,'\nmobile_clear');
  const unrelated=[water?4:3,d,6];
  await lab.cmd(`setblock ${coord(unrelated)} minecraft:oak_planks`);
  await lab.drive(`local ok,msg=pcall(function() return ${ships}assemble(${pos(helm)}) end); print("mobile_unowned",ok,tostring(msg))`,'\nmobile_unowned');
  assert.match(lab.output(),/mobile_unowned\s+false\s+[^\n]*placed by the calling companion/);
  assert.match(await lab.cmd(`execute if block ${coord(unrelated)} minecraft:oak_planks`),/Test passed/);
  await lab.cmd(`setblock ${coord(unrelated)} minecraft:air`);
  await lab.drive('local ok,msg=pcall(function() return engineering.site.apply({{pos={x=-21,y=-57,z=10},state="minecraft:oak_planks"}}) end); print("mobile_outside",ok,tostring(msg))','\nmobile_outside');
  assert.match(lab.output(),/mobile_outside\s+false\s+[^\n]*outside the configured site/);
  await lab.cmd(`item replace block ${coord(stock)} container.2 with minecraft:gold_ingot 7`);

  let seq=0;
  function millRead(expression) {
    return (async()=>{
      const label='mobile_mill_'+seq++;
      await lab.drive(`local r=${expression}; local n=0; for _,s in ipairs(r.items) do if s.item=="minecraft:cobblestone" and s.inventory=="OutputInventory" then n=n+s.count end end; print("${label}",r.rpm or 0,n,r.power_status or "unknown")`,'\n'+label);
      const line=lab.output().split('\n').find(s=>s.startsWith(label+'\t'));
      assert(line);
      const [,rpm,product,power]=line.split('\t');
      return {rpm:Number(rpm),product:Number(product),power};
    })();
  }
  const ground=()=>millRead(site+'inspect('+pos(mill)+')');
  const onboard=()=>millRead(ships+'inspect_onboard('+mill.map((v,i)=>v-helm[i]).join(',')+')');

  async function assay(stage, target, source, measure, start){
    assert.match(await lab.cmd(`item replace block ${coord(source)} container.0 with minecraft:andesite 1`),/Replaced/);
    const rows=[];
    for(let i=0;i<22;i++){
      await wait(175);
      const read=await measure();
      const status=start?await receipt(lab,'mobile_motion_'+seq++,ships+'inspect()'):null;
      rows.push({...read,status});
      if(read.product>=target) break;
    }
    const last=rows.at(-1);
    const travel=start&&Math.hypot(last.status.position[0]-start.position[0],last.status.position[2]-start.position[2]);
    const passed=last.product===target&&last.rpm===256&&last.power==='running'&&(!start||(!last.status.static&&travel>.10));
    lab.mark('mobile_assay',{stage,rows,travel,passed});
    assert(passed,'Milling receipt failed during '+stage);
  }

  await assay('ground-first',1,input,ground);
  await assay('ground-repeat',2,input,ground);
  const assembly=await receipt(lab,'mobile_assembled',ships+'assemble('+pos(helm)+')');
  assert.equal(assembly.cells,cells.length);
  await wait(2600);
  await receipt(lab,'mobile_anchor_initial',ships+'anchor()');
  const raw=await lab.cmd('engineering_test_ship_pose');
  const scene=JSON.parse(raw.split('LAB_SHIP_POSE ',2)[1]);
  const relocated=scene.blocks.find(b=>b.block==='create:millstone')?.pos;
  assert(relocated&&scene.blocks.some(b=>b.block==='create:creative_motor'));
  const supply=input.map((v,i)=>relocated[i]+v-mill[i]);
  const cargo=stock.map((v,i)=>relocated[i]+v-mill[i]);
  const getCargo=()=>lab.cmd(`data get block ${coord(cargo)} Items`);
  const after=await onboard();
  assert.deepEqual(after,{rpm:256,product:2,power:'running'});
  assert.match(await getCargo(),/gold_ingot.*Count: 7b|Count: 7b.*gold_ingot/);
  await assay('assembled-first',3,supply,onboard);
  await assay('assembled-repeat',4,supply,onboard);
  for(let i=5;i<=6;i++){
    const start=await receipt(lab,'mobile_drive_'+i,ships+'drive("north",0.3,0,0,100)');
    assert(start.mounted);
    await assay('moving-'+i,i,supply,onboard,start);
    await receipt(lab,'mobile_park_'+i,ships+'anchor()');
    await wait(650);
  }
  await assay('post-stop-first',7,supply,onboard);
  await assay('post-stop-repeat',8,supply,onboard);
  assert.match(await getCargo(),/gold_ingot.*Count: 7b|Count: 7b.*gold_ingot/);
  lab.mark('mobile_workshop_result',{passed:true,cells:assembly.cells,fixture:true,initialOnboard:after,
    unownedRejected:true,outsideRejected:true,stock:7});
  if(process.env.LAB_MOBILE_GRAPHICS==='1'){
    const manual=await receipt(lab,'mobile_handover',ships+'handover()');
    assert(manual.manual&&manual.static);
    await lab.cmd('gamemode survival LabOwner');
    const file=path.join(lab.run,'mobile-client-result.json');
    lab.mark('mobile_graphics_ready',{file});
    for(let i=0;i<900&&!fs.existsSync(file);i++) await wait(200);
    const result=JSON.parse(fs.readFileSync(file));
    lab.mark('mobile_graphics_result',result);
    assert(result.passed);
    await lab.cmd('gamemode creative LabOwner');
    const anchor=await receipt(lab,'mobile_manual_stop',ships+'anchor()');
    assert(anchor.static&&!anchor.cruising&&!anchor.manual);
    await wait(500);
    const settled=await receipt(lab,'mobile_manual_settled',ships+'inspect()');
    await wait(850);
    const parked=await receipt(lab,'mobile_manual_parked',ships+'inspect()');
    assert.deepEqual(settled.position,parked.position);
  }
  const final=await lab.save('mobile-final');
  const bounds=lab.engineeringBounds;
  const outside=[...new Set([...Object.keys(baseline.blocks),...Object.keys(final.blocks)])].filter(p=>
    p.split(',').map(Number).some((v,i)=>v<bounds[i]||v>bounds[i+3])&&
    JSON.stringify(baseline.blocks[p])!==JSON.stringify(final.blocks[p]));
  assert.deepEqual(outside,[]);
  lab.mark('mobile_no_outside_changes',{outside});
};
