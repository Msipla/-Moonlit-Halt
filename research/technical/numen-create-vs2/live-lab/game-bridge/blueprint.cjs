const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

function parseState(state) {
  const match = /^([a-z0-9_]+:[a-z0-9_./-]+)(?:\[([a-z0-9_]+=[a-z0-9_-]+(?:,[a-z0-9_]+=[a-z0-9_-]+)*)\])?$/.exec(state);
  assert(match, `Invalid block state ${state}`);
  const pairs = match[2]?.split(',').map(pair => pair.split('=')) || [];
  const properties = Object.fromEntries(pairs);
  assert.equal(Object.keys(properties).length,pairs.length,`Duplicate state property in ${state}`);
  return {name:match[1],properties};
}

function matches(actual, state, exact = false) {
  const {name,properties} = parseState(state);
  const fields = actual?.Properties || {};
  return name === (actual?.Name || 'minecraft:air')
    && (!exact || Object.keys(fields).length === Object.keys(properties).length)
    && Object.entries(properties).every(([k,v]) => fields[k] === v);
}

function compile(source,bounds) {
  const result = spawnSync('prlimit',['--as=268435456','--cpu=2','--','texlua',
    path.join(__dirname,'blueprint.lua'),source,...bounds.map(String)],{
    env:{PATH:process.env.PATH,LANG:'C'},encoding:'utf8',timeout:3500,maxBuffer:2*1024*1024,
  });
  assert(!result.error && result.status===0,result.error?.message || result.stdout || result.stderr || 'Blueprint evaluation failed');
  return result.stdout.trim().split('\n').map(row => {
    const [key,state,expected] = row.split('\t');
    const [x,y,z] = key.split(',').map(Number);
    parseState(state);
    parseState(expected);
    return {key,pos:{x,y,z},state,expected};
  });
}

module.exports = async function blueprint({save,mark,drive,output,run,engineeringBounds}, source) {
  const savedSource = path.join(run,'blueprint-design.lua');
  fs.copyFileSync(source,savedSource);
  const started = performance.now();
  const plan = compile(savedSource, engineeringBounds);
  fs.writeFileSync(path.join(run,'blueprint-cells.json'),JSON.stringify({bounds:engineeringBounds,cells:plan},null,2)+'\n');
  mark('blueprint_generated',{cells:plan.length,ms:Math.round(performance.now()-started)});
  const initial = await save('blueprint-start');
  let snapshot = initial;
  let written = 0;
  let skipped = 0;
  let conflicts = [];
  let stage = 'complete';
  const batchBudget = Number(process.env.LAB_STATIC_BLUEPRINT_BATCHES || 4096);
  assert(Number.isInteger(batchBudget) && batchBudget>0,'Expected a positive blueprint batch budget');
  let batches = 0;
  for (let first=0; first<plan.length; first+=64) {
    const batch = plan.slice(first,first+64);
    const edits = [];
    conflicts = [];
    for (const cell of batch) {
      const actual = snapshot.blocks[cell.key];
      if (matches(actual,cell.state)) skipped++;
      else if (!matches(actual,cell.expected,true)) conflicts.push({key:cell.key,expected:cell.expected,actual});
      else edits.push(cell);
    }
    if (conflicts.length) {
      stage = 'conflict';
      break;
    }
    if (!edits.length) continue;
    const cells=edits.map(({pos:{x,y,z},state,expected})=>
      `{pos={x=${x},y=${y},z=${z}},state=${JSON.stringify(state)},expected=${JSON.stringify(expected)}}`).join(',');
    const marker=`blueprint_receipt_${first}`;
    await drive(`local r=my.create_survey.construct({${cells}}); for _,reject in ipairs(r.rejects or {}) do print("blueprint reject",reject.pos,reject.reason) end; print("${marker}",r.stage,r.written,r.confirmed,r.error or "-", "blueprint_receipt_end")`,'\tblueprint_receipt_end');
    const receipt = output().match(new RegExp(marker+'\\t(\\w+)\\t(\\d+)\\t(\\d+)\\t([^\\r\\n]*)\\tblueprint_receipt_end'));
    assert(receipt,'Missing numeric construction receipt');
    const [,batchStage,applied,confirmed,error] = receipt;
    mark('blueprint_batch',{first:first+1,target:batch.length,pending:edits.length,
      stage:batchStage,written:Number(applied),confirmed:Number(confirmed),error});
    snapshot = await save(`blueprint-batch-${first+1}`);
    written += Number(applied);
    batches++;
    if (batchStage!=='complete') {
      stage = batchStage;
      break;
    }
    if (batches===batchBudget) {
      stage = 'checkpoint';
      break;
    }
  }
  const final = await save('blueprint-final');
  const pending = plan.filter(cell=>!matches(final.blocks[cell.key],cell.state)).map(cell=>cell.key);
  if (stage==='checkpoint' && !pending.length) stage = 'complete';
  mark('blueprint_result',{stage,target:plan.length,confirmed:plan.length-pending.length,written,skipped,batches,pending,conflicts});
};
