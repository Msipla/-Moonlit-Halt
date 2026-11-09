#!/usr/bin/env node
/* Requires Playwright and an installed Chromium. Optional BROWSER_EXECUTABLE.
   Writes evidence to --out (default /tmp/prompt-dictionary-evidence), never to stable HTML. */
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),zlib=require('node:zlib'),crypto=require('node:crypto');
const {pathToFileURL}=require('node:url');const {chromium}=require('playwright');
const root=__dirname,out=path.resolve(process.argv[2]||'/tmp/prompt-dictionary-evidence');fs.mkdirSync(out,{recursive:true});
const stable=fs.readFileSync(path.join(root,'prompt-generator.html'));
const blob=b=>crypto.createHash('sha1').update(`blob ${b.length}\0`).update(b).digest('hex');
assert.equal(blob(stable),'83310c41154cde087d5905f4866c1d9a0706326c');
const dict=JSON.parse(zlib.gunzipSync(fs.readFileSync(path.join(root,'prompt-dictionary.json.gz'))));
assert.equal(dict.records.length,dict.stats.unique_tags);assert.equal(dict.records.length,new Set(dict.records.map(r=>r[0].toLowerCase().replaceAll('_',' '))).size);
assert.equal(dict.records.filter(r=>/[\u3400-\u9fff]/.test(r[1])).length,dict.stats.chinese);
const byTag=new Map(dict.records.map(r=>[r[0],r]));for(const t of ['silver_hair','blue_eyes','running','night','nude'])assert.ok(byTag.has(t));
assert.ok(dict.records.every((r,i,a)=>!i||a[i-1][3]>=r[3]));
assert.equal(dict.default_rating,'unknown');
assert.ok(dict.stats.unique_tags>330000);assert.ok(dict.stats.display_groups['敏感来源标签']>0);
const summary={dataStats:dict.stats,stableBlobSha:blob(stable),browser:null,runs:[],checks:[]};
const file=pathToFileURL(path.join(root,'prompt-generator-dictionary-test.html')).href;
async function query(page,q){
 const before=await page.evaluate(()=>window.dictionaryMetrics.searches.length);const start=performance.now();
 await page.locator('#tagSearch').fill(q);
 await page.waitForFunction(n=>window.dictionaryMetrics.searches.length>n,before);
 return {query:q,wallMs:performance.now()-start,workerMs:await page.evaluate(()=>window.dictionaryMetrics.searches.at(-1))};
}
async function rss(browser){
 const c=await browser.newBrowserCDPSession();const {targetInfos}=await c.send('Target.getTargets');
 const heaps=[];
 for(const t of targetInfos.filter(t=>['page','worker'].includes(t.type))){
  const {sessionId}=await c.send('Target.attachToTarget',{targetId:t.targetId,flatten:false});
  const result=await new Promise(async(resolve,reject)=>{
   const timer=setTimeout(()=>{c.off('Target.receivedMessageFromTarget',listen);reject(Error('Heap measurement timeout'))},10000);
   function listen(m){if(m.sessionId!==sessionId)return;const r=JSON.parse(m.message);if(r.id!==71)return;clearTimeout(timer);c.off('Target.receivedMessageFromTarget',listen);r.error?reject(Error(r.error.message)):resolve(r.result)}
   c.on('Target.receivedMessageFromTarget',listen);
   await c.send('Target.sendMessageToTarget',{sessionId,message:JSON.stringify({id:71,method:'Runtime.getHeapUsage'})});
  });
  heaps.push({type:t.type,usedMiB:result.usedSize/1048576,totalMiB:result.totalSize/1048576,backingStorageMiB:(result.backingStorageSize||0)/1048576});
  await c.send('Target.detachFromTarget',{sessionId});
 }
 await c.detach();return {heaps,usedMiB:heaps.reduce((s,h)=>s+h.usedMiB,0),note:'V8 isolate heaps from CDP, before forced GC; excludes total OS/browser RSS.'};
}
(async()=>{
 let browser;const launch=()=>chromium.launch({headless:true,executablePath:process.env.BROWSER_EXECUTABLE||undefined,args:['--no-sandbox','--disable-dev-shm-usage',...(process.env.BROWSER_SINGLE_PROCESS?['--no-zygote','--single-process','--disable-gpu']:[])]});
 try{
 for(const mobile of [false,true]){
  browser=await launch();summary.browser=browser.version();
  const context=await browser.newContext(mobile?{viewport:{width:393,height:873},isMobile:true,hasTouch:true,deviceScaleFactor:1}:{viewport:{width:1440,height:1000}});
  const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const cdp=await context.newCDPSession(page);if(mobile)await cdp.send('Emulation.setCPUThrottlingRate',{rate:4});
  await page.goto(file);await page.locator('#tagTab').click();await page.waitForFunction(()=>document.querySelectorAll('#tagChoices>div').length>0);
  await page.evaluate(()=>{window.tickGaps=[];let last=performance.now();window.tickTimer=setInterval(()=>{const t=performance.now();tickGaps.push(t-last);last=t},20)});
  const memoryBefore=await rss(browser),loadStart=performance.now();
  await page.locator('#tagCsv').setInputFiles(path.join(root,'prompt-dictionary.json.gz'));
  await page.waitForFunction(()=>window.dictionaryMetrics.loads.length===1,null,{timeout:120000});
  await page.waitForFunction(()=>document.getElementById('tagCount').textContent.includes('343,')||document.getElementById('tagCount').textContent.includes('344,'));
  const loadWallMs=performance.now()-loadStart;
  const timing=await page.evaluate(()=>{clearInterval(tickTimer);return {loadWorkerMs:dictionaryMetrics.loads[0].loadMs,maxMainThreadTimerGapMs:Math.max(...tickGaps)}});
  const memoryAfter=await rss(browser);const searches=[];
  for(const q of ['银发','silver_hair','silver hair','夜景','running','zzzz_missing_tag_123','a'])searches.push(await query(page,q));
  await query(page,'silver_hair');const chip=page.locator('#tagChoices .tag-chip').filter({has:page.locator('small').filter({hasText:/^silver_hair$/})}).first();
  assert.equal(await page.locator('#tagChoices>div').first().locator('small').first().innerText(),'silver_hair');
  await chip.click();assert.match(await page.locator('#tagPositive').inputValue(),/silver_hair/);
  await page.locator('#tagChoices>div').filter({has:page.locator('small').filter({hasText:/^silver_hair$/})}).locator('button').last().click();
  await page.locator('#tagFavoritesOnly').click();await page.waitForFunction(()=>document.querySelector('#tagChoices')?.textContent.includes('silver_hair'));
  assert.match(await page.locator('#tagChoices').innerText(),/silver_hair/);await page.locator('#tagFavoritesOnly').click();
  await page.locator('#tagModel').selectOption('anima');assert.match(await page.locator('#tagPositive').inputValue(),/score_7/);
  await page.locator('#tagNegative').fill('low quality, blurry');assert.equal(await page.locator('#tagNegative').inputValue(),'low quality, blurry');
  await query(page,'');await page.locator('#tagCats button').filter({hasText:/^发型与发色$/}).click();
  await page.waitForFunction(n=>Number(document.querySelector('#tagCount').textContent.match(/^匹配 ([\d,]+)/)?.[1]?.replaceAll(',',''))>=n&&Number(document.querySelector('#tagCount').textContent.match(/^匹配 ([\d,]+)/)?.[1]?.replaceAll(',',''))<n+600,dict.stats.display_groups['发型与发色']);
  await page.locator('#tagCats button').filter({hasText:/^全部$/}).click();await page.locator('#tagSourceCategory').selectOption('1');
  await page.waitForFunction(()=>document.querySelector('#tagCount').textContent.startsWith('匹配 156,886'));
  await page.locator('#tagSourceCategory').selectOption('');await page.locator('#tagFlag').selectOption('restricted_source');
  await page.waitForFunction(()=>document.querySelector('#tagCount').textContent.startsWith('匹配 175'));
  await page.locator('#tagFlag').selectOption('risk');await page.waitForFunction(()=>document.querySelectorAll('#tagChoices button:disabled').length>0);
  assert.ok(await page.locator('#tagChoices button:disabled').count()>0);
  await page.locator('#tagFlag').selectOption('');await query(page,'silver_hair');
  assert.ok(await page.locator('#tagChoices>div').count()<=120);
  const layout=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));assert.ok(layout.scroll<=layout.width+1);
  const [download]=await Promise.all([page.waitForEvent('download'),page.locator('#tagExport').click()]);await download.saveAs(path.join(out,mobile?'mobile-export.txt':'desktop-export.txt'));assert.match(fs.readFileSync(path.join(out,mobile?'mobile-export.txt':'desktop-export.txt'),'utf8'),/silver_hair/);
  await page.screenshot({path:path.join(out,mobile?'mobile.png':'desktop.png'),fullPage:true});
  await page.reload();await page.locator('#tagTab').click();assert.match(await page.locator('#tagPositive').inputValue(),/silver_hair/);assert.equal(await page.locator('#tagNegative').inputValue(),'low quality, blurry');
  await page.locator('#tagFavoritesOnly').click();await page.waitForFunction(()=>document.querySelector('#tagChoices')?.textContent.includes('silver'));
  await page.locator('#tagFavoritesOnly').click();
  // Header, quoted alias, Chinese column, duplicate, and multiline CSV parsing.
  await page.locator('#tagCsv').setInputFiles({name:'test.csv',mimeType:'text/csv',buffer:Buffer.from('tag,type,count,aliases,zh\nunit_test_tag,0,20,"first,second",测试词\nunit_test_tag,0,10,,重复\nmultiline_tag,0,1,"line1\nline2",多行词\n')});
  await page.waitForFunction(()=>dictionaryMetrics.loads.length===1);await query(page,'测试词');assert.match(await page.locator('#tagChoices').innerText(),/unit_test_tag/);
  await page.locator('#tagCsv').setInputFiles({name:'bad.json',mimeType:'application/json',buffer:Buffer.from('{invalid')});
  await page.waitForFunction(()=>document.getElementById('tagImportStatus').textContent.includes('失败'));
  assert.equal(await page.locator('#tagCsv').isDisabled(),false);await query(page,'多行词');assert.match(await page.locator('#tagChoices').innerText(),/multiline_tag/);
  await page.locator('#tagResetLib').click();await page.waitForFunction(()=>document.getElementById('tagImportStatus').textContent.includes('恢复内置'));
  await query(page,'unit_test_tag');assert.equal(await page.locator('#tagChoices>div').count(),0);
  await page.locator('.tab[data-mode="moss"]').click();assert.ok((await page.locator('#output').inputValue()).length>100);
  assert.deepEqual(errors,[]);
  summary.runs.push({mode:mobile?'mobile viewport + CPU 4x (not Android)':'desktop Linux Chromium',loadWallMs,...timing,memoryBefore,memoryAfter,searches,layout,errors});
  summary.checks.push(...['gzip full load','Chinese and English search','underscore and space search','frequency and relevance ordering','selection and positive output','favorites survive reload','negative output survives reload','model presets','TXT export','display category','raw source category','restricted source metadata','risk flags read only','DOM cap','no horizontal overflow','CSV quoting/header/dedup/multiline','bad JSON recovery','library reset','MOSS regression'].map(check=>({mode:mobile?'mobile':'desktop',check,passed:true})));
  await context.close();await browser.close();
 }
 assert.equal(blob(fs.readFileSync(path.join(root,'prompt-generator.html'))),blob(stable));
 fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(summary,null,2));console.log(JSON.stringify({browser:summary.browser,passed:summary.checks.length,evidence:out,runs:summary.runs},null,2));
 }finally{if(browser?.isConnected())await browser.close()}
})().catch(e=>{fs.writeFileSync(path.join(out,'failure.txt'),e.stack||String(e));console.error(e);process.exit(1)});
