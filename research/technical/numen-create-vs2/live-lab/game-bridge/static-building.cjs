const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const stages = [
  ['layout', 90000, '加载 building_design、shape 与 create_kinetics 的 static-building 参考。只观察一次场地，只交方案，暂不写入。自选位置、外轮廓、风格、材料、楼层标高、房间、交通与分区格数预算。标出入口、楼梯和阳台的连接关系。'],
  ['lower', 240000, '沿用你的方案，做基础、一楼围护和主要通道。保持楼梯预留和设备通路。程序化生成分区列表，用施工回执确认。'],
  ['upper', 300000, '沿用实际一楼，完成二楼楼板、楼梯、门窗、阳台和屋顶。分开施工。重点回读梯段净空、上下端落脚、阳台出入口及屋架接缝。'],
  ['services', 210000, '完成两层分区内饰、门灯与实际可运行的 Create 工位。说明投料、取产物与获得转速的位置，保留考官独立投料。'],
  ['finish', 180000, '以实际读回复核各层连通、门窗、家具以及机器，检查立面、屋顶和照明。按缺口做局部修整，保留已验证的结构与库存。'],
  ['report', 90000, '结束施工。根据已读回的事实交最终简报。列出入口、楼梯首尾、各功能区、阳台及轮流验证投料和取物的坐标，标明未验证项。'],
];

function delta(before, after, bounds) {
  const materials = {};
  const levels = {};
  let newCells = 0;
  let changedExisting = 0;
  let outside = 0;
  for (const key of new Set([...Object.keys(before.blocks), ...Object.keys(after.blocks)])) {
    const old = before.blocks[key];
    const state = after.blocks[key];
    if (JSON.stringify(old) === JSON.stringify(state)) continue;
    const [x, y, z] = key.split(',').map(Number);
    if (![x,y,z].every((value, axis) => value >= bounds[axis] && value <= bounds[axis + 3])) {
      outside++;
    } else if (old) {
      changedExisting++;
    } else {
      newCells++;
      materials[state.Name] = (materials[state.Name] || 0) + 1;
      levels[y] = (levels[y] || 0) + 1;
    }
  }
  return {newCells, changedExisting, outside, materials, levels};
}

module.exports = async function staticBuilding(lab) {
  const {save, mark, chat, isolate, engineeringBounds: bounds, run, staticResume} = lab;
  const startAt = staticResume ? stages.findIndex(([phase]) => phase === process.env.LAB_STATIC_FROM_PHASE) : 0;
  assert(!staticResume || startAt>0, 'Set LAB_STATIC_FROM_PHASE to a construction or report stage');
  const scheduled = stages.slice(startAt);
  const start = await save(staticResume ? 'static-start' : 'static-empty');
  let baseline = start;
  const planFile = path.join(run,'static-model-plan.txt');
  let plan;
  if (staticResume) {
    const baselineFile = path.join(staticResume,'static-empty.json');
    baseline = JSON.parse(fs.readFileSync(baselineFile,'utf8'));
    fs.copyFileSync(baselineFile,path.join(run,'static-empty.json'));
    const originPlan = path.join(staticResume,'static-model-plan.txt');
    if (fs.existsSync(originPlan)) {
      plan = fs.readFileSync(originPlan,'utf8');
    } else {
      const prior = fs.readFileSync(path.join(staticResume,'events.jsonl'),'utf8').trim().split('\n').map(JSON.parse);
      plan = prior.find(row=>row.type==='agent_return')?.content;
    }
    assert(plan,'The source run must have the model’s original plan');
    fs.writeFileSync(planFile,plan);
    mark('static_resume',{source:path.basename(staticResume),phase:scheduled[0][0],start:delta(baseline,start,bounds)});
  }
  mark('static_budget', {bounds, stages: scheduled.map(([phase, timeout]) => ({phase, seconds: timeout / 1000}))});
  let previous = start;
  let lastCompleted = null;
  let isolated = false;
  try {
    for (const [phase, timeout, brief] of scheduled) {
      const started = performance.now();
      const count = delta(baseline, previous, bounds).newCells;
      const goal = phase === 'layout'
        ? '在陌生空地造一座可入住的双层机械工坊住宅，目标实际净增 800～1500 格。需要休息、厨房、储物、加工区、楼梯、阳台、门窗、照明和内饰。全程保持静态，禁用命令施工、拆改已有库存或使用完整现成蓝图。以实际有效建筑计数。'
        : `沿用当前世界和你自己的上一段方案。考官上次读盘记录区内净增 ${count} 格（含待甄别格），距 800～1500 格目标还可净增 ${Math.max(0, 800-count)}～${Math.max(0, 1500-count)} 格。`;
      const resume = staticResume && phase === scheduled[0][0]
        ? `现在从原世界的存档接续。你的原始方案如下，只作为接续上下文，不重复规划或重写已完成部位。${plan.replace(/\s+/g,' ').trim()} 前一段超时，先局部读回确认实际边界，再继续。` : '';
      const answer = await chat(`${goal} ${resume} ${brief} 只做本段，未完成也应在本段内先给简报。回复本段的施工量、读回、连接边界、剩余预算与下一步，等待下一段任务。`, timeout);
      if (!answer || answer.truncated) {
        await isolate();
        isolated = true;
      }
      const current = await save('static-' + phase);
      const local = delta(previous, current, bounds);
      const overall = delta(baseline, current, bounds);
      const complete = Boolean(answer && !answer.truncated);
      mark('static_stage', {phase, complete, seconds: (performance.now()-started)/1000, local, overall});
      if (!complete || overall.outside || (['layout','report'].includes(phase) && (local.newCells || local.changedExisting))) break;
      if (phase === 'layout') fs.writeFileSync(planFile,answer.content);
      lastCompleted = phase;
      previous = current;
    }
  } finally {
    if (!isolated) await isolate();
    const final = delta(baseline, await save('static-final'), bounds);
    mark('static_model_endpoint', {lastCompleted, final, countInRange: final.newCells>=800 && final.newCells<=1500,
      pending: ['effective architecture','survival traversal','independent first/repeat processing','visual review']});
  }
};
