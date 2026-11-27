const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {createRequire}=require('node:module');
const {gunzipSync}=require('node:zlib');
const {setTimeout:wait}=require('node:timers/promises');
const {snapshot}=require('../world.cjs');
const water=process.env.LAB_ENGINEERING_VESSEL==='water';
const pos=p=>`{x=${p[0]},y=${p[1]},z=${p[2]}}`;
const coord=p=>p.join(' ');
const ships='engineering_vessels.ship.';
const site='engineering.site.';

async function restoreWaterExam(lab, file) {
  const nbt=createRequire(path.join(lab.base,'lab.cjs'))('prismarine-nbt');
  const design=nbt.simplify(nbt.parseUncompressed(gunzipSync(fs.readFileSync(file))));
  assert.deepEqual(design.size,[6,4,13]);
  const origin=[-2,-57,-10];
  const cells=design.blocks.flatMap(({pos:offset,state,nbt:entity})=>{
    const block=design.palette[state];
    if(block.Name==='minecraft:air') return [];
    const properties=Object.entries(block.Properties??{}).filter(([k])=>k==='axis'||k==='facing');
    const spec=block.Name+(properties.length?'['+properties.map(([k,v])=>k+'='+v).join(',')+']':'');
    const at=offset.map((v,i)=>v+origin[i]);
    const rpm=block.Name==='create:creative_motor'?',rpm='+Number(entity.ScrollValue):'';
    return [`{pos=${pos(at)},state=${JSON.stringify(spec)}${rpm}}`];
  });
  assert.equal(cells.length,143);
  await lab.drive(`local r=my.create_survey.construct({${cells}}); assert(r.stage=="complete" and r.confirmed==143); print("restored_exam")`,'\nrestored_exam');
  const walk=Array.from({length:8},(_,i)=>[0,-55,2-i]);
  const drive=[[2,-56,1],[2,-55,1],[3,-55,1]];
  await lab.drive(`local r=my.create_survey.check_vessel({walk={${walk.map(pos)}},drive={${drive.map(pos)}}}); local gear, roof=false,false;
    for _,e in ipairs(r.rejects) do gear=gear or e.reason:find("not a hull component",1,true); roof=roof or e.reason:find("head blocked",1,true) end
    assert(not r.ok and gear and roof); print("restored_defects")`.replaceAll('\n',' '),'\nrestored_defects');
  lab.mark('mobile_repair_restored',{cells:143,walk,drive});
  return lab.save('mobile-repair-restored');
}

function oldRoof(baseline) {
  return Object.entries(baseline.blocks)
    .filter(([p,b])=>p.split(',')[1]==='-54'&&b.Name==='minecraft:oak_planks')
    .map(([p])=>p.split(',').map(Number));
}

function repairSurvey(baseline) {
  const partIds=new Set([
    'vs_eureka:oak_ship_helm','vs_eureka:anchor','minecraft:barrel',
    'create:creative_motor','create:millstone','create:cogwheel'
  ]);
  const parts=Object.entries(baseline.blocks)
    .filter(([,b])=>partIds.has(b.Name)).map(([p,b])=>`${p} ${b.Name}`);
  const roof=new Map();
  for(const [x,,z] of oldRoof(baseline)){
    if(!roof.has(z)) roof.set(z,[]);
    roof.get(z).push(x);
  }
  const rows=[...roof.entries()].sort((a,b)=>a[0]-b[0]).map(([z,xs])=>
    `z=${z} x=${xs.sort((a,b)=>a-b).join(',')}`);
  return '巡检回执：旧屋顶 oak_planks、y=-54，'+rows.join('；')+'。旧部件 '+parts.join('；')+'。';
}

const shipControls='engineering_vessels.ship API: assemble(舵坐标), inspect(), inspect_onboard(dx,dy,dz)（相对舵）, '+
  'drive(朝向,前进,升降,偏航,刻数<=100), anchor()。航行状态下一轮读取。';
const repairEdits='Lua 每格 {pos={x=x,y=y,z=z},state=id,expected=engineering.site.inspect(p).state}。'+
  '把你的坐标编辑数组传入 my.create_survey.construct(edits)，print(r.stage,r.confirmed)。直接执行并一行报告。';

function waterRepairReadiness(before,after) {
  const name=(x,y,z)=>after.blocks[[x,y,z]]?.Name;
  const route=Array.from({length:8},(_,i)=>2-i);
  const roof=oldRoof(before).map(([x,,z])=>{
    const y=[-53,-52,-51].find(y=>name(x,y,z)==='minecraft:oak_planks');
    return y===undefined?null:[x,y,z];
  });
  const helm=Object.entries(after.blocks)
    .filter(([,b])=>b.Name==='vs_eureka:oak_ship_helm')
    .map(([p])=>p.split(',').map(Number));
  const mill=after.blockEntities.find(b=>b.id==='create:millstone'&&b.x===3&&b.y===-55&&b.z===1);
  const rpm=mill?.Network?.Stress>mill?.Network?.Capacity?0:(mill?.Speed??0);
  const preservedIds=new Set(['vs_eureka:floater','vs_eureka:anchor','minecraft:barrel']);
  const preserved=Object.entries(before.blocks).filter(([p,b])=>preservedIds.has(b.Name)||
    (p.split(',')[1]==='-56'&&b.Name==='minecraft:oak_planks'&&p!=='3,-56,1'));
  const checks={
    route:route.every(z=>name(0,-56,z)==='minecraft:oak_planks'&&!name(0,-55,z)&&!name(0,-54,z)),
    roof:roof.every(Boolean),
    helm:helm.length===1&&helm[0][0]!==0,
    preserved:preserved.every(([p,b])=>JSON.stringify(after.blocks[p])===JSON.stringify(b)),
    mill:Boolean(rpm&&mill.Network&&name(3,-56,1)==='create:creative_motor'),
    intake:name(3,-53,1)==='minecraft:barrel'&&after.blocks['3,-54,1']?.Properties?.facing==='down'&&
      name(3,-54,1)==='minecraft:hopper'
  };
  return {ready:Object.values(checks).every(Boolean),checks,helm:helm[0]??null,rpm,route,roof};
}

function repairOnboard(world,prepared,ship) {
  const helm=ship.blocks.find(c=>c.block==='vs_eureka:oak_ship_helm');
  assert(helm,'expected the assembled helm');
  const shift=helm.pos.map((v,i)=>v-prepared.helm[i]);
  const relocate=p=>p.map((v,i)=>v+shift[i]);
  const blocks=new Map(ship.blocks.map(c=>[c.pos.join(','),c.block]));
  const name=(...p)=>blocks.get(relocate(p).join(','));
  const millPos=relocate([3,-55,1]);
  const saved=snapshot(world,[...millPos,...millPos]);
  const mill=saved.blockEntities.find(e=>e.id==='create:millstone');
  const rpm=mill?.Network?.Stress>mill?.Network?.Capacity?0:(mill?.Speed??0);
  return {roof:prepared.roof.every(p=>name(...p)==='minecraft:oak_planks'),
    route:prepared.route.every(z=>name(0,-56,z)==='minecraft:oak_planks'&&!name(0,-55,z)&&!name(0,-54,z)),
    rpm,anchored:ship.stationary,cells:ship.blocks.length};
}

async function receipt(lab,label,expression) {
  await lab.drive(`print("${label}",${expression})`,'\n'+label);
  const line=lab.output().split('\n').find(s=>s.startsWith(label+'\t'));
  assert(line,label);
  return JSON.parse(line.slice(label.length).trim());
}

async function repairComponents(lab,baseline) {
  const steps=[
    ['helm',90000,'只修舵位。原舵在(0,-55,-3)，中心 x=0 是通道。选 x=1 或 -1、z=-5..-1、y=-55 的空位。'+
      '放 vs_eureka:oak_ship_helm[facing=north]，从中间移除旧舵。同一条 Lua 生成这两个编辑，保留原甲板。'],
    ['roof',150000,'只修低屋顶。舵位已实测侧置。'+repairSurvey(baseline)+
      '把原低屋顶抬一格，留 x=0,z=2 到 -5、脚 y=-55 和头 y=-54 净空。选侧柱位置连接屋面与甲板。保留舵和家具。'],
    ['mill',150000,'只修现有磨石动力。旧马达(2,-56,1)、侧齿轮(2,-55,1)、磨石(3,-55,1)。'+
      '保留磨石，自行安排上竖直直驱并设马达 rpm，原侧齿轮改船身。磨石上从下到上接向下漏斗和桶。'+
      '同条 Lua 施工并用 engineering.site.inspect(磨石坐标).rpm 读转速。']
  ];
  let answer,prepared;
  for(const [step,time,prompt] of steps){
    answer=await lab.chat(prompt+repairEdits,time);
    prepared=waterRepairReadiness(baseline,await lab.save('mobile-repair-'+step));
    const passed=prepared.checks[step]&&prepared.checks.preserved&&
      (step!=='roof'||prepared.checks.route)&&(step!=='mill'||prepared.ready);
    lab.mark('mobile_repair_component',{step,passed,...prepared});
    if(!passed||!answer||answer.truncated) return {answer,prepared};
  }
  return {answer,prepared};
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

const cabinDrill='在授权区做一段紧凑的飞船生活舱练习，不造整船。方块限 x=-3..3、z=-6..0，甲板 y=-55。沿 x=0 从 z=0 上甲板，经 z=-1 的门口，沿两格净高走道到 z=-4。门后有顶、有侧窗。驾驶舵和一个可从走道使用的储物桶必须摆侧边。两侧轮廓至少有一处收窄或折线。先读 create_kinetics 的移动基地参考；自行设计，只用直接施工，以实际通道和完工简报结束。';
const cabinCoaching='甲板高度指方块所在 Y，脚在 Y+1、头在 Y+2，屋面从 Y+3 开始。读参考后，下一条 Lua 用坐标去重的 put 函数生成你自己的甲板、舵桶、侧窗和屋面，结尾调用 my.create_survey.construct(cells) 并打印 stage、batches 和 confirmed。按回执续建，最后用 plan 核对走道。';
const cabinFinish='继续上一轮的小舱室构件。独立读回：49 格橡木甲板已完成，五步中心走道净空，舵、桶、顶与侧窗尚缺。保留甲板主路线；现在直接生成并检查一批舵、桶、墙窗和顶，舵与桶要可从走道旁使用。加一段侧身轮廓收分。Lua 每格形如 {pos={x=x,y=y,z=z},state=方块状态}，调用 engineering.site.check(cells) 通过后直接 apply(cells)，不要重扫。最后用 plan({walk=脚位置列表})核对连通并报告。前一题原限定范围继续有效，甲板 y=-55，入口从(0,-54,0)通往(0,-54,-4)。';
const helmDrill='小练习，只在已建的甲板放一件驾驶舵和一件储物桶，不做别的。甲板已核对 x=-3..3、z=-6..0、y=-55，并且没有任何物件，中心 x=0 是净空走道。两组件自行选择 x=1 或 -1 的侧槽位，y=-54，z 取 -4 到 -2，可从走道走近。舵用 vs_eureka:oak_ship_helm[facing=north]，桶用 minecraft:barrel。一条 Lua 用 {pos={x=x,y=y,z=z},state=方块字符串} 的两格数组，engineering.site.check 无拒绝就立即 apply。下一条核对后简报。不需全场勘察或查手册。';
const canopyDrill='在上一题的甲板、舵和桶上加一个短舱雨棚。甲板 y=-55、x=-3..3、z=-6..0，舵桶位于 x=±1、y=-54、z=-3。只做 z=-4..-1 的透光有边框雨棚，两列侧边支撑和至少两个侧窗，x=0 保留两格净空，保留原甲板、舵和桶。自行选边框宽度、收窄方式及条纹；雨棚顶 y=-52 或 -51。候选格 {pos={x=x,y=y,z=z},state=方块字符串}。用一批 engineering.site.check，合格直接 apply，查一次走道并简报。只写新增方块。';
const liftDrill='做一段单独的飞行升力练习，只造可装配的小甲板、侧舵、侧锚和连上的气球，不装修。区域空旷，甲板在 y=-54 附近，自选足够紧凑的范围和朝向。用候选格检查占用并直接分批施工。气球从约每 3 个普通方块配 1 个起步。放齐两层功能后立即装配，查悬停，短推进、转向、锚停。最后报告实际值，不扩建。';
const waterExam='陌生实机考试：在空池造一艘浅滩取样艇。自行选址、朝向、比例和布局，做一个收窄的船头、带侧窗的短舱、一条两格净高的登舱路线、可交互的储物桶和一个边侧磨石。用 create_kinetics 移动基地参考，控制在小艇规模。只做直接施工，先用候选格检查占用，不重扫。磨石先用竖直直驱，舵锚离开走道。建全必要部件和连上的浮块，核实通道和动力后真实装配，实测短推进、转向、锚停并报告。只有实际通过的步骤才写通过。考官会独立验收玩家离席和运动中投料。';
const waterConstructionCoaching='补充施工方法。取样一列确定水线，浮块放在水面紧邻的第一层空气，装配后再看实际浮态。第一段施工 Lua 用参考的坐标去重 put 函数安排自己的布局，同一段结尾调用 my.create_survey.construct(cells)。甲板上方两格留空，屋面从第三格开始，竖直动力件放在水线之上。按回执继续。';
const transferWater='空池造一艘东向巡检艇。南舷登舱，步行路线先穿入舱并向东转弯。短舱有顶和窗，前方留露天工作甲板。'+
  '自选位置、长宽和内饰。水面 y=-58，浮块先铺 y=-57。舵、锚、储物、磨石放侧位。';
const transferAir='空地造一座南向飞行巡检站。西侧进入有顶有窗的休息间，储物和磨石放过道两侧，北端留露天观测平台。'+
  '自选位置、高度、比例和布局。甲板离地，升力从每 3 到 4 个普通方块配一个连在船身上的气球起步，实际核对悬停。';
const transferWork='造竖直直驱磨石和桶、向下漏斗进料。一个施工 Lua 编写去重的编辑数组，'+
  '每格 {pos={x=x,y=y,z=z},state=id,rpm=可选转速}。用 my.create_survey.construct(编辑,{walk=脚路线,drive=马达到磨石}) 预检和施工。'+
  '打印进度和错误，现场读回路线和磨石，再装配。实际读船载转速、推进、偏航和锚停，按实测简报。';

async function transferStages(lab) {
  const deckY=water?-56:-54;
  const place='只用直接施工。Lua 编辑格 {pos={x=x,y=y,z=z},state=id}。'+
    '循环生成去重数组，调用 my.create_survey.construct(cells)，打印确认格数后一句话报告。';
  let answer=await lab.chat((water?'只做东向小艇的收分甲板和浮块。':'只做南向飞行站的甲板。')+
    '空地，自选 x,z 和大小。橡木甲板 y='+deckY+'。'+
    (water?'水线 y=-58，浮块 vs_eureka:floater 铺在两侧甲板正下方 y=-57。甲板中段南侧留登舱面。':
      '甲板西侧留入口、北端留观测面。气球、屋顶和设备交给后续。')+
    '本次先动手铺甲板，不需要整船设计。'+place,125000);
  const base=await lab.save('transfer-deck');
  const find=(state,id)=>Object.entries(state.blocks)
    .filter(([,b])=>b.Name===id).map(([p,b])=>({pos:p.split(',').map(Number),properties:b.Properties??{}}));
  const deck=find(base,'minecraft:oak_planks').filter(({pos:p})=>p[1]===deckY);
  const floaters=find(base,'vs_eureka:floater').length;
  const passed=deck.length>=15&&(!water||floaters>=8);
  lab.mark('mobile_transfer_deck',{passed,cells:deck.length,floaters,deckY});
  if(!passed||!answer||answer.truncated) return answer;

  const lower=deck[0].pos.slice(),upper=lower.slice();
  for(const {pos:p} of deck) for(let i=0;i<3;i++){
    lower[i]=Math.min(lower[i],p[i]);
    upper[i]=Math.max(upper[i],p[i]);
  }
  const site='甲板范围 x='+lower[0]+'..'+upper[0]+'、z='+lower[2]+'..'+upper[2]+'、y='+deckY+'。';
  const access=water?'南舷进入后向东折向舱内，前方留露天工作面。':'西侧进入，北端留露天观测面。';
  answer=await lab.chat('在你刚建的实际甲板上只做短舱、舵、锚与储物桶。'+site+access+
    '带侧窗和由边墙支撑的屋顶。入口和走道留两格净空，屋顶从甲板上第三格起。'+
    '用 vs_eureka:oak_ship_helm[facing='+(water?'east':'south')+
    '] 与 vs_eureka:anchor，侧置，材料自行选择。'+
    (water?'':'屋顶连入足够 vs_eureka:balloon，轻型结构每三到四普通格先配一个。')+
    '舵与储物贴边，给磨石留侧位。用 engineering.site.plan({walk=脚坐标}) 核对通路。'+place,160000);
  const cabin=await lab.save('transfer-cabin');
  const helms=find(cabin,'vs_eureka:oak_ship_helm');
  const roof=find(cabin,'minecraft:oak_planks').filter(({pos:p})=>p[1]>=deckY+3).length;
  const lift=find(cabin,'vs_eureka:balloon').length+find(cabin,'vs_eureka:white_balloon').length;
  const proper=helms.length===1&&helms[0].properties.facing===(water?'east':'south')&&roof>=4
    &&find(cabin,'vs_eureka:anchor').length===1&&find(cabin,'minecraft:barrel').length>0&&(water||lift>=8);
  lab.mark('mobile_transfer_cabin',{passed:proper,roof,lift,helm:helms[0]?.pos??null});
  if(!proper||!answer||answer.truncated) return answer;

  answer=await lab.chat('只在你的短舱侧边补一个磨石工位。'+site+access+
    '同一竖列放朝上马达、磨石、朝下漏斗和供料桶。马达和加工设备的坐标自行选择，留走道。'+
    '用 my.create_survey.construct(edits,{walk=完整脚路线,drive=马达到磨石坐标}) 检查并施工。'+
    '用 check_vessel 读陆上 RPM，完成回一句。装配留给下一步。',130000);
  const work=await lab.save('transfer-workshop');
  const mill=work.blockEntities.find(b=>b.id==='create:millstone'&&b.Speed&&b.Network?.Stress<=b.Network?.Capacity);
  lab.mark('mobile_transfer_workshop',{passed:Boolean(mill),rpm:mill?.Speed??null});
  if(!mill||!answer||answer.truncated) return answer;
  const helm=helms[0].pos;
  return lab.chat('实际舵坐标 '+pos(helm)+'，磨石相对舵 '+[mill.x,mill.y,mill.z].map((v,i)=>v-helm[i]).join(',')+
    '。'+shipControls+'现在装配、实测船体与磨石、推进与偏航，然后锚停简报。',135000);
}

const waterRigSetup='续做原存档的浅滩取样艇，只练舵锚和装配。独立读回已铺 29 橡木甲板在 y=-56，下方 29 浮块，艇首在负 Z，甲板 x=±2、z=-4..0 段完整。两件新增方块：舵 vs_eureka:oak_ship_helm[facing=north] 与锚 vs_eureka:anchor，自己选 x=±1 的边位，z=-3..-1，y=-55。保留 x=0 两格净高。用 {pos={x=x,y=y,z=z},state=方块字符串} 一次 engineering.site.check 无拒绝后直接 apply。用 engineering_vessels.ship.assemble(舵坐标) 装配，成功立即 inspect()。';
const waterRig=waterRigSetup+'先实测短推进、再短转向，调用签名 drive("north",0.3,0,0,100)，再 anchor()。逐步读回实际值然后报告，暂不加舱或磨石，不重扫。';
const waterTurn=waterRigSetup+'只复测船身转向。用 drive(\"north\",0.15,0,0.8,100)，第四参数才是偏航控制。下一回合直接读 inspect() 的 rotation 四元数，与舵机装载时的值比较；再 anchor() 并报告。只做舵锚、装配、偏航和锚停，不重扫。';

function scoreCabinDrill(baseline, final) {
  const blocks=final.blocks;
  const name=(x,y,z)=>blocks[[x,y,z].join(',')]?.Name;
  const route=[0,-1,-2,-3,-4].map(z=>({z,
    open:!name(0,-54,z)&&!name(0,-53,z),
    floor:name(0,-55,z)==='minecraft:oak_planks'}));
  const changes=[...new Set([...Object.keys(blocks),...Object.keys(baseline.blocks)])]
    .filter(p=>JSON.stringify(blocks[p])!==JSON.stringify(baseline.blocks[p]));
  const together=(id)=>changes.filter(p=>blocks[p]?.Name===id).map(p=>p.split(',').map(Number));
  const positions=together('vs_eureka:oak_ship_helm');
  const storage=together('minecraft:barrel');
  const usable=points=>points.length===1&&points.every(([x,y,z])=>y===-54&&z>=-4&&z<=-2&&
    (Math.abs(x)===1||Math.abs(x)===2&&!name(Math.sign(x),-54,z)));
  const roof=[-1,-2,-3,-4].every(z=>Boolean(name(0,-52,z)||name(0,-51,z)));
  const sideGlassCount=changes.filter(p=>{
    const [x,y,z]=p.split(',').map(Number);
    return x!==0 && y>=-54 && y<=-53 && z<=-2 && ['minecraft:glass','minecraft:glass_pane'].includes(blocks[p]?.Name);
  }).length;
  const sideGlass=sideGlassCount>0;
  const widths=Array.from({length:7},(_,i)=>Array.from({length:7},(_,x)=>name(x-3,-55,-i))
    .filter(b=>b==='minecraft:oak_planks').length);
  const variedOutline=new Set(widths.filter(n=>n>0)).size>1;
  const allowed=changes.every(p=>{const [x,y,z]=p.split(',').map(Number);return Math.abs(x)<=3&&z>=-6&&z<=0&&y>=-55&&y<=-51;});
  const pass=route.every(p=>p.floor&&p.open)&&usable(positions)&&usable(storage)&&roof&&sideGlass&&variedOutline&&allowed;
  return {pass,usable:usable(positions)&&usable(storage),route,helm:positions,storage,roof,sideGlass,sideGlassCount,widths,variedOutline,allowed,cells:changes.length};
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
  const drill=process.env.LAB_MOBILE_DRILL;
  const stepTransfer=drill==='water-transfer-stages'||drill==='air-transfer-stages';
  const transfer=drill==='water-transfer'||drill==='air-transfer'||stepTransfer;
  const componentRepair=drill==='water-repair-components';
  const repair=drill==='water-repair'||componentRepair;
  const baseline=repair
    ? await restoreWaterExam(lab,process.env.LAB_MOBILE_REPAIR_NBT)
    : await lab.save('mobile-empty');
  if(process.env.LAB_MOBILE_CAP==='1') return checkHullCap(lab);
  const drills={cabin:cabinDrill,'cabin-coached':cabinDrill+cabinCoaching,'cabin-finish':cabinFinish,helm:helmDrill,canopy:canopyDrill};
  if(Object.hasOwn(drills,drill)){
    const prompt=drills[drill];
    const answer=await lab.chat(prompt,165000);
    await lab.isolate();
    const final=await lab.save('mobile-drill-final');
    const score=scoreCabinDrill(baseline,final);
    const clearRoute=score.route.every(p=>p.floor&&p.open);
    let pass=score.pass;
    if(drill==='helm') pass=score.usable&&clearRoute&&score.allowed;
    if(drill==='canopy'){
      const kept=Object.entries(baseline.blocks).every(([p,b])=>JSON.stringify(final.blocks[p])===JSON.stringify(b));
      pass=score.roof&&score.sideGlassCount>=2&&clearRoute&&score.allowed&&kept;
    }
    lab.mark('mobile_drill_result',{...score,pass,drill,replied:Boolean(answer&&!answer.truncated)});
    return;
  }
  if(drill==='lift'){
    const answer=await lab.chat(liftDrill,190000);
    await lab.isolate();
    const observe=async()=>{
      const pos=await lab.cmd('engineering_test_ship_pose');
      const row=pos.split('LAB_SHIP_POSE ',2)[1];
      return row?JSON.parse(row):null;
    };
    const first=await observe();
    await wait(950);
    const last=await observe();
    await lab.save('mobile-lift-final');
    const delta=last&&first?Math.hypot(...last.position.map((v,i)=>v-first.position[i])):null;
    const count=last?.blocks.reduce((a,b)=>a+Number(/balloon/.test(b.block)),0)??0;
    lab.mark('mobile_lift_result',{ship:last?.id??null,cells:last?.blocks.length??0,balloons:count,
      pos:last?.position??null,drift:delta,anchored:last?.stationary??null,
      hoverAboveGround:Boolean(last&&!last.stationary&&last.position[1]>-57&&delta<0.3),replied:Boolean(answer&&!answer.truncated)});
    return;
  }

  if(process.env.LAB_MOBILE_MODEL==='1'){
    const scenario=water?'河道补给站，包含驾驶位、储物货位和有顶的休息间。':'山脊观测救援站，包含驾驶位、医用储物和有顶的两人休息区。';
    const prompts={'water-exam':waterExam,'water-exam-coached':waterExam+waterConstructionCoaching,
      'water-rig':waterRig,'water-turn':waterTurn,
      'water-transfer':transferWater+transferWork+shipControls,'air-transfer':transferAir+transferWork+shipControls};
    const repairPrompt='修你原来的浅滩取样艇，保留船底、浮块、原储物和船舱用途。甲板 y=-56，中心路线 x=0、脚 y=-55、z=2 到 -5。自行修正低屋顶和中间舵位，让屋顶通过立柱连入船身。磨石在(3,-55,1)，侧齿轮会留在岸上。用船用竖直直驱、输入桶和漏斗，从磨石库存取货。一次 Lua 自行生成编辑，用 my.create_survey.construct(edits,{walk=脚坐标数组,drive=马达到磨石坐标数组}) 检查并施工，已有格使用精确 expected。再用 check_vessel 检查成品及实际转速。核对装配格数和船上磨石转速，完成短推进、偏航、锚停和简报。';
    const prompt=(repair?repairPrompt:prompts[drill])??(resume
      ? '接续隔离存档里你之前自主建造的补给船。保留船体和现有储物。只做这几步：查实际舵、锚、浮块、走道和加工件；将舵移出舱内两格净空走道，保持原有室内用途；核对船体允许搬运的部件，磨石用兼容的最短动力连接；尽快装配、核对在水面与短程推进、转向、锚停。按实际回执推进，助手原地施工，不扩大布局或重做百科勘察。'
      : '在授权工程区自主设计并完成一座'+(water?'水面':'飞行')+'移动基地。'+scenario+
        '先读 create_kinetics 的移动基地参考。留连续两格净高通道和侧置的实际磨石工位。自选位置、材料、朝向和布局。一次勘察后尽快开始直接施工，保持助手原地。逐批检查，核对方块、通道和磨石的实际转速。最后装配，实际推进转向并锚停。未实测的功能说明待验。');
    const waterPractice=drill==='water-rig'||drill==='water-turn';
    let answer,prepared,ship;
    if(componentRepair){
      ({answer,prepared}=await repairComponents(lab,baseline));
      lab.mark('mobile_repair_readiness',prepared);
      if(prepared.ready&&answer&&!answer.truncated){
        const offset=[3,-55,1].map((v,i)=>v-prepared.helm[i]);
        answer=await lab.chat('你的修复已通过独立读回，陆上磨石 '+prepared.rpm+' RPM。现在只装配与实航。'+
          '实测舵坐标 '+pos(prepared.helm)+'，磨石相对舵 '+offset.join(',')+'。'+shipControls+
          '先装配，下一回合读船体与磨石，再试短推进和偏航，锚停读回，最后简报。用 print 输出。',180000);
      }
    }else if(stepTransfer){
      answer=await transferStages(lab);
    }else{
      answer=await lab.chat(prompt,waterPractice?175000:resume?285000:420000);
    }
    await lab.isolate();
    if(drill==='water-exam'||drill==='water-exam-coached'||waterPractice||repair||transfer){
      const raw=await lab.cmd('engineering_test_ship_pose');
      const data=raw.split('LAB_SHIP_POSE ',2)[1];
      ship=data?JSON.parse(data):null;
      lab.mark('mobile_exam_pose',{ship});
    }
    const final=await lab.save('mobile-model-final');
    if(prepared?.ready&&ship) lab.mark('mobile_repair_onboard',repairOnboard(path.join(lab.run,'world'),prepared,ship));
    lab.mark('mobile_model_result',{answer,fixture:false,resume,placedBlockDelta:Object.keys(final.blocks)
      .filter(p=>JSON.stringify(final.blocks[p])!==JSON.stringify(baseline.blocks[p])).length});
    return;
  }

  const {cells,helm,walk,mill,input,stock,d}=cabin();
  const planned=cells.map(([p,b,rpm])=>`{pos=${pos(p)},state=${JSON.stringify(b)}${rpm?',rpm='+rpm:''}}`);
  if(process.env.LAB_MOBILE_BATCH==='off'){
    for(let i=0;i<cells.length;i+=32){
      const label='mobile_built_'+i;
      const batch=planned.slice(i,i+32);
      await lab.drive(`local s=engineering.site; local cells={${batch}}; assert(#s.check(cells).rejects==0); assert(s.apply(cells).cells==#cells); print("${label}")`,'\n'+label);
    }
  }else{
    const options=`{walk={${walk.map(pos)}},drive={${pos([mill[0],mill[1]-1,mill[2]])},${pos(mill)}}}`;
    const motorIndex=cells.findIndex(([,state])=>state.startsWith('create:creative_motor'));
    const bad=planned.slice();
    bad[motorIndex]=bad[motorIndex].replace(/create:creative_motor\[facing=up\]/,'create:cogwheel[axis=y]').replace(',rpm=256','');
    const [x,y,z]=walk[0];
    bad.push(`{pos=${pos([x,y+1,z])},state="minecraft:oak_planks"}`);
    await lab.drive(`local r=my.create_survey.construct({${bad}},${options}); local gear, roof=false,false;
      for _,e in ipairs(r.rejects) do gear=gear or e.reason:find("not a hull component",1,true); roof=roof or e.reason:find("head blocked",1,true) end
      assert(r.stage=="vessel" and r.written==0 and gear and roof); print("mobile_preflight_rejected")`.replaceAll('\n',' '),'\nmobile_preflight_rejected');
    assert.deepEqual((await lab.save('mobile-preflight')).blocks,baseline.blocks);
    await lab.drive(`local r=my.create_survey.construct({${planned}},${options}); print("mobile_batches",r.stage,r.batches,r.confirmed,r.written); if r.error then print(r.error) end; for _,e in ipairs(r.rejects or {}) do print(e.pos,e.reason) end`,'\nmobile_batches');
    const row=lab.output().split('\n').find(line=>line.startsWith('mobile_batches\t'));
    const [,stage,batches,confirmed,written]=row.split('\t');
    lab.mark('mobile_batch_result',{fixture:true,stage,batches:Number(batches),confirmed:Number(confirmed),written:Number(written)});
    assert.equal(stage,'complete');
    assert(Number(batches)>1);
    assert.equal(Number(confirmed),cells.length);
    assert.equal(Number(written),cells.length);
    await lab.drive(`local r=my.create_survey.check_vessel(${options}); assert(r.ok and r.mill_rpm==256); print("mobile_vessel_verified")`,'\nmobile_vessel_verified');
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
