const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {createRequire}=require('node:module');
const {gunzipSync}=require('node:zlib');
const {setTimeout:wait}=require('node:timers/promises');
const {snapshot}=require('../world.cjs');
const water=process.env.LAB_ENGINEERING_VESSEL==='water'||process.env.LAB_MOBILE_DRILL==='dsh-sub';
const pos=p=>`{x=${p[0]},y=${p[1]},z=${p[2]}}`;
const coord=p=>p.join(' ');
const ships='engineering_vessels.ship.';
const site='engineering.site.';
const find=(state,id)=>Object.entries(state.blocks)
  .filter(([,b])=>b.Name===id).map(([p,b])=>({pos:p.split(',').map(Number),properties:b.Properties??{}}));

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
  'drive(朝向,前进,升降,偏航,刻数), anchor()。前进、升降、偏航限 -1 到 1，持续 1 到 100 刻。航行状态下一轮读取。';
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

async function transferStages(lab, baseline, resume, medium=false) {
  const deckY=water?-56:-54;
  const place='只用直接施工。Lua 编辑格 {pos={x=x,y=y,z=z},state=id}。'+
    '循环生成去重数组，调用 my.create_survey.construct(cells)，打印确认格数后一句话报告。';
  let answer,base=baseline;
  if(!resume){
    if(medium){
      answer=await lab.chat('空池中型轮船设计，最后实际装配 300～500 格，整船上限 512。你选址、朝向、船形与分区。'+
        '需要驾驶、休息、生活备餐、储物、磨石区与连续可走路线。甲板 y=-56，水面 y=-58。'+
        '先用六行简报定下平面、分区和分段格数预算，留动力、舵锚、浮块余量。这一步只定方案。',100000);
      if(!answer||answer.truncated) return answer;
    }
    answer=await lab.chat(medium
      ? '按你刚提交的方案先施工收分甲板，自选位置、朝向与长宽。橡木甲板 y=-56，90 到 144 格，长宽各不超过 24。'+
        '先只做甲板，用循环生成一批方案跨64格。浮块与上层后续独立施工。'+place
      : (water?'只做东向小艇的收分甲板。':'只做南向飞行站的甲板。')+
      '空地，自选 x,z 和形状。橡木甲板 y='+deckY+'，15 到 72 格，长宽各不超过 12 格。'+
      (water?'水线 y=-58，甲板中段南侧留登舱面。浮块随后补。':'甲板西侧留入口，北端留观测面。另给整船屋顶和气球留方块预算。')+
      '只动手铺甲板。'+place,medium?180000:125000);
    base=await lab.save('transfer-deck');
  }
  const deck=find(base,'minecraft:oak_planks').filter(({pos:p})=>p[1]===deckY);
  const lower=[Infinity,deckY,Infinity],upper=[-Infinity,deckY,-Infinity];
  for(const {pos:p} of deck) for(let i=0;i<3;i++){
    lower[i]=Math.min(lower[i],p[i]);
    upper[i]=Math.max(upper[i],p[i]);
  }
  const span=medium?24:12;
  const passed=deck.length>=(medium?90:15)&&deck.length<=(medium?144:72)
    &&upper[0]-lower[0]<span&&upper[2]-lower[2]<span;
  lab.mark('mobile_transfer_deck',{passed,cells:deck.length,deckY,resumed:resume});
  if(!passed||(!resume&&(!answer||answer.truncated))) return answer;
  const site='甲板范围 x='+lower[0]+'..'+upper[0]+'、z='+lower[2]+'..'+upper[2]+'、y='+deckY+'。';
  const floats=medium?Math.ceil(deck.length/2):8;
  if(water&&find(base,'vs_eureka:floater').length<floats){
    answer=await lab.chat('甲板已读回，共 '+deck.length+' 格。'+site+
      (medium?'本次只在甲板下面 y=-57 分布':'本次只在甲板下面 y=-57、两条长边选位放')+
      ' vs_eureka:floater，确保和甲板相接，至少 '+floats+' 格。'+
      '保持所有现有结构。'+place,medium?110000:85000);
    const fitted=await lab.save('transfer-floaters');
    const supported=find(fitted,'vs_eureka:floater').filter(({pos:[x,y,z]})=>
      y===deckY-1&&fitted.blocks[[x,deckY,z]]?.Name==='minecraft:oak_planks');
    lab.mark('mobile_transfer_floaters',{passed:supported.length>=floats,cells:supported.length});
    if(supported.length<floats||!answer||answer.truncated) return answer;
  }
  const access=medium?'保留自己设计的登舱与分区路线，侧向通行，留露天工作面。'
    :water?'南舷进入后向东折向舱内，前方留露天工作面。':'西侧进入，北端留露天观测面。';
  const windowsIn=state=>find(state,'minecraft:glass_pane').length+find(state,'minecraft:glass').length;
  const existingCabin=resume&&find(base,'vs_eureka:oak_ship_helm').length===1;
  const draftFile=resume&&process.env.LAB_MOBILE_TRANSFER_DRAFT;
  const draft=draftFile
    ? '接续你自己上一轮的未施工草稿。先修草稿里去重后丢失的窗和撞墙的路径，补齐舵锚、桶'+
      (water?'':'和足够相连的气球')+'。下一条 Lua 直接调用 construct 完成候选批，不再重复查帮助。草稿代码 JSON 字符串 '+
      JSON.stringify(fs.readFileSync(draftFile,'utf8'))
    : '';
  if(existingCabin&&!windowsIn(base)){
    answer=await lab.chat('已保留你上一轮实际建成的短舱、舵、锚、储物和通道；独立读盘发现实体玻璃窗为零。'+site+
      '只修窗。挑两处已有舱壁头部高度 '+(deckY+2)+' 的橡木墙格，替换成 minecraft:glass 或 minecraft:glass_pane。'+
      '读取各自原状态作为 expected。同一条 Lua 提交这两格，保留其他方块。'+repairEdits,100000);
  }else if(!existingCabin){
    answer=await lab.chat((medium?'按原分区在实际甲板上做墙窗、舱顶和独立驾驶位。留休息、备餐、储物和磨石分区。'
      :'在你刚建的实际甲板上只做短舱、舵、锚与储物桶。')+site+access+
    '带侧窗和由边墙支撑的屋顶。入口和走道留两格净空，屋顶从甲板上第三格起。'+
    (medium?'用 vs_eureka:oak_ship_helm 和 vs_eureka:anchor，舵朝向、墙窗与屋顶由你决定。用同一坐标只保留最后方案，跨64格交给 construct。'
      :'用 vs_eureka:oak_ship_helm[facing='+(water?'east':'south')+'] 与 vs_eureka:anchor，侧置，材料自行选择。')+
    (water?'':'屋顶连入足够 vs_eureka:balloon，轻型结构每三到四普通格先配一个。')+
      '舵与储物贴边，给磨石留侧位。'+
      (medium?'用 construct 提交完整墙窗屋顶，再用 engineering.site.plan({walk=脚坐标}) 核对建成后的通道。plan 不接收超过 64 格的候选。'
        :'用 engineering.site.plan({walk=脚坐标}) 核对通路。')+place+draft,medium?240000:160000);
  }
  let cabin=await lab.save('transfer-cabin');
  if(medium&&!existingCabin&&answer&&!answer.truncated){
    answer=await lab.chat('只布置你规划的休息座、备餐台、生活储物和工作区。'+site+access+
      '沿通道贴边摆设，留脚和头净空。用现有白名单，座位可用橡木楼梯，备餐可用台面和食材桶。'+
      '不要添加无用途的填充。最终一句说明分区，另附 WALK: 后的单行 JSON 脚路线数组 [[x,y,z],...]。'+place,150000);
    cabin=await lab.save('transfer-interior');
  }
  let helms=find(cabin,'vs_eureka:oak_ship_helm');
  const roof=['minecraft:oak_planks','minecraft:oak_slab']
    .flatMap(id=>find(cabin,id)).filter(({pos:p})=>p[1]>=deckY+3).length;
  const windows=windowsIn(cabin);
  const lift=find(cabin,'vs_eureka:balloon').length+find(cabin,'vs_eureka:white_balloon').length;
  await lab.drive('local i=engineering.site.info(); print("transfer_hull",i.hull_limit,table.concat(i.hull," "))',
    '\ntransfer_hull');
  const palette=lab.output().split('\n').find(line=>line.startsWith('transfer_hull\t'));
  assert(palette,'expected native hull limits');
  const [,rawLimit,ids]=palette.split('\t');
  const limit=Number(rawLimit);
  const hull=new Set(ids.split(' '));
  const hullSize=Object.values(cabin.blocks).filter(b=>hull.has(b.Name)).length;
  const proper=helms.length===1&&(medium||helms[0].properties.facing===(water?'east':'south'))&&roof>=4&&windows>0
    &&find(cabin,'vs_eureka:anchor').length===1&&find(cabin,'minecraft:barrel').length>0
    &&(water||lift>=8)&&hullSize+4<=limit&&(!medium||hullSize+4<=500);
  lab.mark('mobile_transfer_cabin',{passed:proper,roof,windows,lift,helm:helms[0]?.pos??null,hullSize,limit});
  if(!proper||(!answer&&(!existingCabin||!windowsIn(base)))||answer?.truncated) return answer;

  let route=JSON.parse(process.env.LAB_MOBILE_TRANSFER_ROUTE||'null');
  if(medium){
    const verify=async (response, label, fallback)=>{
      let proposed;
      try {
        const line=response?.content?.match(/WALK\s*[:：]\s*(\[\[.+\]\])/u)?.[1];
        proposed=line?JSON.parse(line):fallback;
      } catch {}
      if(!Array.isArray(proposed)||proposed.length<2||proposed.length>32||
        !proposed.every(p=>Array.isArray(p)&&p.length===3&&p.every(Number.isInteger))) return null;
      const program='local check=engineering.site.plan({walk={'+proposed.map(pos).join(',')+'}}); '+
        'for _,e in ipairs(check.clearance) do print("'+label+'_issue",e.pos.x,e.pos.y,e.pos.z,e.reason) end; '+
        'print("'+label+'",#check.clearance)';
      await lab.drive(program,'\n'+label+'\t');
      const rows=lab.output().split('\n');
      const issues=rows.filter(row=>row.startsWith(label+'_issue\t')).map(row=>row.slice(label.length+7));
      const count=Number(rows.find(row=>row.startsWith(label+'\t'))?.split('\t')[1]);
      assert.equal(count,issues.length);
      return {proposed,clearance:issues};
    };
    const first=await verify(answer,'medium_walk_first',route);
    const helm=helms[0].pos;
    if(!first||first.clearance.length||helm[1]!==deckY+1){
      const heading=helms[0].properties.facing;
      const [dx,dz]={north:[0,1],south:[0,-1],east:[-1,0],west:[1,0]}[heading]??[0,0];
      const clear=([x,y,z])=>cabin.blocks[[x,y-1,z]]?.Name==='minecraft:oak_planks'
        &&!cabin.blocks[[x,y,z]]&&!cabin.blocks[[x,y+1,z]];
      const candidates=deck.map(({pos:[x,y,z]})=>[x,y+1,z]).filter(p=>{
        const [x,y,z]=p;
        return Math.abs(x-helm[0])+Math.abs(z-helm[2])<=3
          &&!first?.proposed.some(q=>coord(p)===coord(q))
          &&[p,[x+dx,y,z+dz],[x+2*dx,y,z+2*dz]].every(clear);
      }).slice(0,6);
      lab.mark('mobile_medium_access_repair',{helm,reportedRoute:first,candidates});
      const restore=helm[1]===deckY?'minecraft:oak_planks':'minecraft:air';
      const program='local old='+pos(helm)+'; local new=你选的坐标; local state=engineering.site.inspect(old).state; '+
        'print(my.create_survey.construct({{pos=old,state="'+restore+'",expected=state},{pos=new,state=state}}))';
      answer=await lab.chat('只修舵位。程序已核对驾驶舵和舵后两站的候选 '+candidates.map(pos).join('、')+
        '，舵保持 '+heading+'，中线走道在原舵底落点缺支撑。保留其余各区。'+
        '从候选挑一个，把下面 Lua 的“你选的坐标”直接换成 {x=…,y=…,z=…} 执行。'+program+
        '。确认两格后一句话报告。若改变走道，另附 WALK 后的脚路线 JSON。',120000);
      cabin=await lab.save('transfer-access-repaired');
      helms=find(cabin,'vs_eureka:oak_ship_helm');
      const fixed=await verify(answer,'medium_walk_fixed',first?.proposed);
      route=fixed&&!fixed.clearance.length?fixed.proposed:null;
    }else{
      route=first.proposed;
    }
    lab.mark('mobile_medium_walk',{route});
    if(!route||helms.length!==1||helms[0].pos[1]!==deckY+1) return answer;
  }
  const walk=route?'现有净空已读回，Lua 脚路线 walk={'+route.map(pos).join(',')+'}。':access;
  const slots=route?deck.map(({pos:[x,y,z]})=>[x,y+1,z]).filter(([x,y,z])=>
    !route.some(([rx,,rz])=>rx===x&&rz===z)&&
    route.some(([rx,,rz])=>Math.abs(rx-x)+Math.abs(rz-z)<=2)&&
    [0,1,2,3].every(dy=>!cabin.blocks[[x,y+dy,z]])): [];
  const open=slots.length?'程序已查到四格净空的柱底候选 '+slots.slice(0,6).map(pos).join('，')+'。自行挑一个。':'';
  const poweredMill=state=>state.blockEntities.find(b=>b.id==='create:millstone'&&b.Speed&&b.Network?.Stress<=b.Network?.Capacity);
  if(!medium||!poweredMill(cabin)){
    answer=await lab.chat('只补一个磨石工位。'+site+walk+
      '在露天甲板任选四格竖向净空的侧位，柱底马达 y='+(deckY+1)+'，避开走道。'+open+
      '用 print(my.create_survey.mill_column({at={x=x,y=y,z=z},walk=脚路线,rpm=128}))。'+
      '它会用原生检查和分批确认马达、磨石、漏斗及桶。然后 print(engineering.site.inspect(磨石坐标).rpm)，完成回一句。',130000);
  }
  const mill=poweredMill(await lab.save('transfer-workshop'));
  lab.mark('mobile_transfer_workshop',{passed:Boolean(mill),rpm:mill?.Speed??null});
  if(!mill||(!answer&&!existingCabin)||answer?.truncated) return answer;
  if(!water){
    const liftTarget=Math.ceil((hullSize-lift+4)/4);
    if(lift<liftTarget){
      answer=await lab.chat('磨石正常。装配前加气球。现有 '+lift+' 格，固定夹具实测配比下本次起步目标 '+liftTarget+' 格。'+
        site+'只在屋面正上方 y='+(deckY+4)+' 的未占用格补 '+(liftTarget-lift)+' 个 vs_eureka:balloon。'+
        '按下面格确实是屋面选位。已有气球、屋顶、通道和工位保留。'+place,110000);
      const ready=await lab.save('transfer-lift');
      const liftCount=find(ready,'vs_eureka:balloon').length+find(ready,'vs_eureka:white_balloon').length;
      lab.mark('mobile_transfer_lift',{passed:liftCount>=liftTarget,cells:liftCount,target:liftTarget});
      if(liftCount<liftTarget||!answer||answer.truncated) return answer;
    }
  }
  const helm=helms[0].pos;
  return lab.chat((medium?'舵朝 '+helms[0].properties.facing+'，':'')+'实际舵坐标 '+pos(helm)+'，磨石相对舵 '+[mill.x,mill.y,mill.z].map((v,i)=>v-helm[i]).join(',')+
    '。'+shipControls+'现在装配、实测船体与磨石、推进与偏航，然后锚停简报。'+
    (water?'':'装配后先读两次实际高度，核对离地悬停。'),135000);
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

function hullScaffolds(snapshot, core) {
  const shell=new Set(['minecraft:oak_planks','minecraft:oak_stairs','minecraft:glass','minecraft:barrel','mobilebase:sub_core']);
  const sides=[[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]];
  const hull=[core],seen=new Set([core.join(',')]),scaffolds=[];
  for(let i=0;i<hull.length;i++){
    for(const side of sides){
      const next=hull[i].map((value,axis)=>value+side[axis]);
      const key=next.join(',');
      if(seen.has(key)) continue;
      seen.add(key);
      const block=snapshot.blocks[key]?.Name;
      if(block==='minecraft:cobblestone') scaffolds.push(next);
      if(shell.has(block)) hull.push(next);
    }
  }
  return {hull,scaffolds};
}

async function dshTrial(lab, sub, resume) {
  const deckY=sub?-49:-58;
  const family=sub?'潜艇':'房车';
  const core='mobilebase:'+(sub?'sub':'land')+'_core';
  if(!resume){
    await lab.save('dsh-autonomous-empty');
    await lab.chat('第一阶段只铺'+family+'底板。场地已读回为空，'+
      (sub?'池内 x=-17..17、z=-20..19，水面 y=-50，艇底必须贴水。':'平路 y=-61，给原生四轮留出底下和两侧空间。')+
      '自行选址与朝向。甲板 y='+deckY+'，长 9～13 格、宽 5～7 格，离边缘至少三格。'+
      '第一条 Lua 用二维循环填入橡木板 {pos={x=x,y=y,z=z},state="minecraft:oak_planks"} 数组。'+
      '在同一条调用 print(my.create_survey.construct(cells))，成功后一句话报行进方向。',125000);
  }
  const base=await lab.save('dsh-autonomous-deck');
  const deck=find(base,'minecraft:oak_planks').map(p=>p.pos).filter(p=>p[1]===deckY);
  lab.mark('dsh_autonomous_deck',{sub,cells:deck.length});
  if(deck.length<35) return;
  const [left,right,front,back]=[Math.min(...deck.map(p=>p[0])),Math.max(...deck.map(p=>p[0])),
    Math.min(...deck.map(p=>p[2])),Math.max(...deck.map(p=>p[2]))];
  const box='x='+left+'..'+right+'、z='+front+'..'+back+'、y='+deckY+'。';
  const write='Lua 用去重循环生成 {pos={x=x,y=y,z=z},state=id}，最后 print(my.create_survey.construct(cells))。';
  if(sub){
    const installed=find(base,core).map(({pos:p})=>pos(p)).join('、');
    if(!find(base,'minecraft:glass').length){
      const perimeter='local cells={}; for x=x1,x2 do for z=z1,z2 do '+
        'if x==x1 or x==x2 or z==z1 or z==z2 then for dy=1,2 do '+
        'cells[#cells+1]={pos={x=x,y='+deckY+'+dy,z=z},state=dy==2 and "minecraft:glass" or "minecraft:oak_planks"} '+
        'end end end end; print(my.create_survey.construct(cells))';
      await lab.chat('只做模型选址的现有潜艇侧壁和端壁。实测甲板 '+box+
        '选短舱边界 x1,x2,z1,z2，外圈围两格高，下橡木、上玻璃。下一阶段才加顶。'+
        (installed?'保留已装核心 '+installed+'，墙面与驾驶位错开。':'留独立露天驾驶位。')+
        '起始 local x1,x2,z1,z2=你选的四个整数；然后立刻执行现成外圈 Lua：'+perimeter+
        '。按回执短报。',135000);
    }
    const walls=await lab.save('dsh-autonomous-sub-walls');
    const glass=find(walls,'minecraft:glass').filter(({pos:p})=>p[1]===deckY+2);
    if(glass.length<4) return;
    const xs=glass.map(p=>p.pos[0]),zs=glass.map(p=>p.pos[2]);
    if(!find(walls,'minecraft:barrel').length) await lab.chat('侧壁观察窗已读回，范围 x='+Math.min(...xs)+'..'+Math.max(...xs)+
      '、z='+Math.min(...zs)+'..'+Math.max(...zs)+'。用两层 for 循环填同范围屋顶 y='+(deckY+3)+
      '，自己选择顶部出入口，保留驾驶外部接近面。先装屋顶，再加一只侧置桶和一个休息座。'+
      write+'用同一 Lua 提交这两部分，简短回报。',130000);
  }else{
    await lab.chat('底板已读回。实际边界 '+box+'沿用你选的布局，建墙窗、屋顶、侧置休息座与储物桶。'+
      '留侧门与沿车长的两格净高过道。预留驾驶区。核心上方净空，旁边留可站两格及自动舵锚位置。'+
      '保留甲板完整，不提前放轴、机件或舵锚。使用橡木板、橡木楼梯、玻璃和木桶。'+write+
      '报告主要通行点和核心坐标。',165000);
  }
  const cabin=await lab.save('dsh-autonomous-cabin');
  if(!find(cabin,core).length){
    await lab.chat('请只安装一个 '+core+'，朝向沿你规划的行进方向。用原生 numen.build.place({{name="'+core+
      '[facing=north]",pos={x=x,y=y,z=z}}})（north 替换成所选方向），放在自己选的甲板驾驶区 y='+String(deckY+1)+
      '。放完用 numen.scan.block 回读并报告位置。下一阶段再点击核心。',90000);
  }
  let before=await lab.save('dsh-autonomous-prelaunch');
  const cores=find(before,core).map(p=>p.pos);
  lab.mark('dsh_autonomous_cabin',{sub,windows:find(cabin,'minecraft:glass').length,
    storage:find(cabin,'minecraft:barrel').length,cores});
  if(cores.length!==1) return;
  const [x,y,z]=cores[0];
  if(sub){
    const facing=before.blocks[cores[0]].Properties.facing;
    const [dx,dz]={north:[1,0],east:[0,1],south:[-1,0],west:[0,-1]}[facing];
    const anchor=[x+dx,y,z+dz];
    const occupied=before.blocks[anchor];
    if(occupied&&occupied.Name!=='vs_eureka:anchor'){
      lab.mark('dsh_autonomous_anchor_conflict',{anchor,block:occupied.Name});
      await lab.chat('核心右侧的原生锚位 '+pos(anchor)+' 被占。保留墙窗和内饰，只清这一格。'+
        'local p='+pos(anchor)+'; print(my.create_survey.construct({{pos=p,state="minecraft:air",expected=engineering.site.inspect(p).state}}))',90000);
      before=await lab.save('dsh-autonomous-anchor-cleared');
      if(before.blocks[anchor]) return;
    }
    const {hull,scaffolds}=hullScaffolds(before,cores[0]);
    if(scaffolds.length){
      lab.mark('dsh_autonomous_scaffolds',{connected:scaffolds});
      await lab.chat('只清与船壳相邻的自有寻路圆石接点。逐一由原生权限检查筛选，拒绝项保留。'+
        '直接运行 local cells={}; local s=engineering.site; for _,p in ipairs({'+scaffolds.map(pos).join(',')+
        '}) do local e={pos=p,state="minecraft:air",expected="minecraft:cobblestone"}; '+
        'if #s.check({e}).rejects==0 then cells[#cells+1]=e end end; '+
        'if #cells>0 then print(my.create_survey.construct(cells)) end。只用直接施工，回报确认格数。',95000);
      const cleaned=await lab.save('dsh-autonomous-scaffolds-cleared');
      const lost=hull.filter(p=>JSON.stringify(before.blocks[p])!==JSON.stringify(cleaned.blocks[p]));
      lab.mark('dsh_autonomous_hull_preserved',{passed:lost.length===0,damaged:lost});
      if(lost.length) return;
      before=cleaned;
    }
  }
  const distance=([a,b])=>Math.abs(a-x)+Math.abs(b-z);
  const stand=[[left-2,z],[right+2,z],[x,front-2],[x,back+2]].sort((a,b)=>distance(a)-distance(b))
    .find(([a,b])=>a>=-20&&a<=20&&b>=-23&&b<=21&&
      (!before.blocks[[a,deckY,b]]||before.blocks[[a,deckY,b]].Name==='minecraft:stone')&&
      [y,y+1].every(height=>!before.blocks[[a,height,b]])&&
      [[a-1,deckY,b],[a+1,deckY,b],[a,deckY,b-1],[a,deckY,b+1],[a,deckY-1,b]]
        .every(p=>!before.blocks[p]||before.blocks[p].Name==='minecraft:water'));
  if(!stand) return;
  const [side,end]=stand;
  // The isolated inspector stand stays physically disconnected from the hull.
  if(!before.blocks[[side,deckY,end]]) await lab.cmd('setblock '+coord([side,deckY,end])+' minecraft:stone');
  await lab.cmd('tp LabBot '+[side+0.5,y,end+0.5].join(' '));
  // A creative placement can leave the core item selected. DSH needs an empty main hand.
  await lab.cmd('clear LabBot');
  try {
    await lab.chat('你已站在独立试车台。当前核心真实坐标 '+pos(cores[0])+
      '。两次点击都放在同一条 Lua，预览十秒内确认。执行 local p='+pos(cores[0])+
      '; print(numen.use.block(p)); print(numen.use.block(p)); print(numen.scan.block(p).name)'+
      '。保持空手。回报原地核心是否移走。',125000);
    const raw=await lab.cmd('engineering_test_ship_pose');
    const data=raw.split('LAB_SHIP_POSE ',2)[1];
    const ship=data?JSON.parse(data):null;
    const count=id=>ship?.blocks.filter(part=>part.block===id).length??0;
    lab.mark('dsh_autonomous_assembled',{sub,cells:ship?.blocks.length??0,position:ship?.position??null,
      wheels:count('trackwork:med_simple_wheel'),floaters:count('vs_eureka:floater'),
      engine:count('vs_eureka:engine'),helm:count('vs_eureka:oak_ship_helm'),core:count(core)});
  } finally {
    await lab.isolate();
    await lab.save('dsh-autonomous-final');
  }
}

module.exports=async function(lab) {
  const resume=Boolean(process.env.LAB_MOBILE_MODEL==='1'&&process.env.LAB_MOBILE_RESUME);
  const drill=process.env.LAB_MOBILE_DRILL;
  const subTrial=drill==='dsh-sub';
  if(!resume){
    await lab.cmd('fill -20 -61 -23 20 -61 21 minecraft:stone');
    if(water){
      const waterline=subTrial?-50:-58;
      for(const a of [`-18 -60 -21 -18 ${waterline} 20`,`18 -60 -21 18 ${waterline} 20`,
        `-17 -60 -21 17 ${waterline} -21`,`-17 -60 20 17 ${waterline} 20`])
        await lab.cmd('fill '+a+' minecraft:stone');
      await lab.cmd(`fill -17 -60 -20 17 ${waterline} 19 minecraft:water`);
    }
  }
  await lab.cmd('time set 4000');
  await lab.cmd('deop LabOwner');
  if(process.env.LAB_MOBILE_MODEL==='1'&&(subTrial||drill==='dsh-land')) return dshTrial(lab,subTrial,resume);
  const medium=drill==='water-medium';
  const stepTransfer=drill==='water-transfer-stages'||drill==='air-transfer-stages'||medium;
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
      answer=await transferStages(lab,baseline,resume,medium);
    }else{
      answer=await lab.chat(prompt,waterPractice?175000:resume?285000:420000);
    }
    await lab.isolate();
    if(drill==='water-exam'||drill==='water-exam-coached'||waterPractice||repair||transfer){
      const raw=await lab.cmd('engineering_test_ship_pose');
      const data=raw.split('LAB_SHIP_POSE ',2)[1];
      ship=data?JSON.parse(data):null;
      lab.mark('mobile_exam_pose',{ship});
      if(medium) lab.mark('mobile_medium_assembled',{cells:ship?.blocks.length??0,
        inScale:Boolean(ship&&ship.blocks.length>=300&&ship.blocks.length<=500)});
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
