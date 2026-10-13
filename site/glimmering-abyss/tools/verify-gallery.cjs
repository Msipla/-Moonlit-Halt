'use strict';
const fs=require('node:fs');const path=require('node:path');
const {chromium}=require(path.join(process.env.GALLERY_PLAYWRIGHT_DIR,'node_modules/playwright'));
const base=process.env.GALLERY_URL||'https://msipla.github.io/-Moonlit-Halt/';
const out=process.env.GALLERY_QA_OUT;fs.mkdirSync(out,{recursive:true});
const assert=(value,message)=>{if(!value)throw Error(message);};
async function ready(page){await page.waitForFunction(()=>document.documentElement.dataset.galleryReady==='true');await page.waitForFunction(()=>['hero-image','gallery-image'].every(id=>{const i=document.getElementById(id);return i.complete&&i.naturalWidth===1280;}));}
async function overflow(page){return page.evaluate(()=>({viewport:innerWidth,width:document.documentElement.scrollWidth,body:document.body.scrollWidth}));}
(async()=>{
  let deployment;
  for(let attempt=0;attempt<18;attempt++){
    const r=await fetch(base+'build-info.json?qa='+Date.now());if(r.ok)deployment=await r.json();
    if(deployment?.commit===process.env.GALLERY_SOURCE_SHA)break;
    await new Promise(resolve=>setTimeout(resolve,10000));
  }
  assert(deployment?.commit===process.env.GALLERY_SOURCE_SHA,'Expected exhibition source is not deployed');
  const browser=await chromium.launch({headless:true});
  const results=[];const errors=[];
  try{
    for(const width of [360,390,430,768,1440]){
      const mobile=width<768;
      const context=await browser.newContext({viewport:{width,height:mobile?844:1000},deviceScaleFactor:1,isMobile:mobile,hasTouch:mobile,locale:'zh-CN',reducedMotion:'reduce'});
      const page=await context.newPage();page.on('pageerror',error=>errors.push({width,error:error.message}));
      await page.goto(base,{waitUntil:'domcontentloaded'});await ready(page);
      const photo=await page.locator('#gallery-image').boundingBox();assert(Math.abs(photo.width/photo.height-16/9)<.03,'Gallery photo letterboxed at '+width);
      const initial=await overflow(page);assert(initial.width<=width&&initial.body<=width,'Horizontal overflow at '+width);
      assert((await page.locator('#aesthetic-status').innerText()).includes('判断'),'Aesthetic state falsely accepted');
      await page.screenshot({path:path.join(out,'home-'+width+'.png'),fullPage:width===390});
      for(const id of ['phase1','phase2','phase3','phase4','phase5','phase6','phase7','phase75']){
        await page.locator('[data-stage-tab="'+id+'"]').click();await ready(page);
        assert(await page.locator('[data-stage-tab="'+id+'"]').getAttribute('aria-pressed')==='true','Stage did not switch');
        assert(page.url().includes('stage='+id),'Stage permalink missing');
      }
      for(const view of ['overview','cliff','floor','cave','bridge','library','street','bedroom','water','descent']){await page.locator('[data-view="'+view+'"]').click();await ready(page);}
      if(width===390){await page.locator('[data-view="overview"]').click();await ready(page);await page.locator('.site-nav a[href="#gallery"]').click();await page.screenshot({path:path.join(out,'phase75-390.png')});}
      await page.locator('#compare-pair').selectOption('before75,after75');assert(await page.locator('#compare-view').isDisabled(),'Natural cave comparison must keep the same camera');assert(await page.locator('#compare-view').inputValue()==='overview','Natural cave comparison camera missing');await page.waitForFunction(()=>['compare-top','compare-bottom'].every(id=>{const i=document.getElementById(id);return i.complete&&i.naturalWidth===1280;}));
      if(width===390||width===1440){await page.locator('.site-nav a[href="#compare"]').click();await page.screenshot({path:path.join(out,'phase75-compare-'+width+'.png')});}
      await page.locator('[data-stage-tab="phase7"]').click();await ready(page);
      for(const view of ['entrance','car','descent','street','cliff','overview','return']){await page.locator('[data-view="'+view+'"]').click();await ready(page);}
      if(width===390){await page.locator('.site-nav a[href="#gallery"]').click();await page.screenshot({path:path.join(out,'phase7-390.png')});}
      await page.locator('[data-stage-tab="phase6"]').click();await ready(page);
      for(const view of ['street','plaza','high','cliff','bridge','scale','overview','facade','interior']){await page.locator('[data-view="'+view+'"]').click();await ready(page);}
      await page.locator('[data-mode="blue95"]').click();await ready(page);assert(await page.locator('[data-view="street"]').getAttribute('aria-pressed')==='true','Phase 6 subset mode has wrong camera');
      await page.locator('[data-view="high"]').click();await ready(page);await page.locator('[data-mode="blue95-fog"]').click();await ready(page);
      await page.locator('[data-view="overview"]').click();await ready(page);assert(await page.locator('[data-mode="blue"]').getAttribute('aria-pressed')==='true','Unsupported fog camera must return to baseline');
      for(const pair of ['base6,low6','low6,fog6']){await page.locator('#compare-pair').selectOption(pair);for(const view of ['street','high','cliff']){await page.locator('#compare-view').selectOption(view);await page.waitForFunction(()=>['compare-top','compare-bottom'].every(id=>{const i=document.getElementById(id);return i.complete&&i.naturalWidth===1280;}));}}
      if(width===390){await page.locator('[data-view="street"]').click();await ready(page);await page.locator('.site-nav a[href="#gallery"]').click();await page.screenshot({path:path.join(out,'phase6-390.png')});await page.locator('.site-nav a[href="#compare"]').click();await page.screenshot({path:path.join(out,'phase6-compare-390.png')});}
      await page.locator('[data-stage-tab="phase5"]').click();await ready(page);
      for(const view of ['core','down','facade','span','departure','arrival','warmth','interior','platform']){await page.locator('[data-view="'+view+'"]').click();await ready(page);}
      await page.locator('[data-mode="a"]').click();await ready(page);assert(await page.locator('[data-view="core"]').getAttribute('aria-pressed')==='true','Phase 5 core-only control mismatched');
      await page.locator('#compare-pair').selectOption('before5,after5');assert(await page.locator('#compare-view').isDisabled(),'Refinement must use facade camera');assert(await page.locator('#compare-view').inputValue()==='facade','Wrong refinement camera label');
      await page.waitForFunction(()=>['compare-top','compare-bottom'].every(id=>{const i=document.getElementById(id);return i.complete&&i.naturalWidth===1280;}));
      if(width===390){await page.locator('.site-nav a[href="#gallery"]').click();await page.screenshot({path:path.join(out,'phase5-390.png')});}
      await page.locator('[data-stage-tab="phase4"]').click();await ready(page);
      for(const view of ['facade','warmth','scale','bridge','interior','platform','overlook']){await page.locator('[data-view="'+view+'"]').click();await ready(page);}
      await page.locator('[data-mode="a"]').click();await ready(page);assert(await page.locator('[data-view="facade"]').getAttribute('aria-pressed')==='true','Phase 4 single-view control mismatched');
      await page.locator('#compare-pair').selectOption('before4,after4');assert(await page.locator('#compare-view').isDisabled(),'Before/after camera must stay platform');
      await page.waitForFunction(()=>['compare-top','compare-bottom'].every(id=>{const i=document.getElementById(id);return i.complete&&i.naturalWidth===1280;}));
      if(width===390){await page.locator('.site-nav a[href="#gallery"]').click();await page.screenshot({path:path.join(out,'phase4-390.png')});}
      await page.locator('[data-stage-tab="phase3"]').click();await ready(page);
      await page.locator('[data-mode="copper"]').click();await page.waitForFunction(()=>document.getElementById('gallery-image').src.includes('copper-platform'));
      await page.locator('[data-view="overlook"]').click();await page.waitForFunction(()=>document.getElementById('gallery-image').complete&&document.getElementById('gallery-image').src.includes('copper-overlook'));
      await page.locator('#zoom-image').click();assert(await page.locator('#lightbox').evaluate(d=>d.open),'Lightbox did not open');
      await page.locator('#close-image').click();assert(!(await page.locator('#lightbox').evaluate(d=>d.open)),'Lightbox did not close');
      await page.locator('[data-mode="lamps"]').click();await ready(page);assert(await page.locator('[data-view="platform"]').getAttribute('aria-pressed')==='true','Single-view control mismatched');
      await page.locator('#compare-pair').selectOption('veil,blue');assert(await page.locator('#compare-view').isDisabled(),'Veil camera constraint missing');
      await page.locator('#compare-pair').selectOption('blue,copper');await page.locator('#compare-view').selectOption('overlook');
      await page.locator('#compare-range').evaluate(input=>{input.value=75;input.dispatchEvent(new Event('input',{bubbles:true}));});
      assert((await page.locator('#compare-top').getAttribute('style')).includes('25%'),'Comparison slider did not change');
      await page.waitForFunction(()=>['compare-top','compare-bottom'].every(id=>{const i=document.getElementById(id);return i.complete&&i.naturalWidth===1280;}));
      const pairSize=await page.locator('#compare-bottom').boundingBox();assert(Math.abs(pairSize.width/pairSize.height-16/9)<.03,'Comparison photo letterboxed at '+width);
      if(width===390){await page.locator('.site-nav a[href="#gallery"]').click();await page.screenshot({path:path.join(out,'gallery-390.png')});await page.locator('.site-nav a[href="#compare"]').click();await page.screenshot({path:path.join(out,'compare-390.png')});}
      const final=await overflow(page);assert(final.width<=width&&final.body<=width,'Interaction overflow at '+width);
      const touchTargets=await page.locator('#view-tabs button,#mode-tabs button,#zoom-image').evaluateAll(nodes=>nodes.map(n=>({label:n.textContent,height:n.getBoundingClientRect().height})));
      assert(touchTargets.every(t=>t.height>=44),'Small touch target at '+width);
      results.push({width,height:mobile?844:1000,emulated_mobile:mobile,horizontal_overflow:false,stage_navigation:true,palette_and_view:true,lightbox:true,comparison_slider:true,touch_targets:touchTargets});
      await context.close();
    }
    assert(errors.length===0,'Browser JavaScript errors');
    const legacy=['phase75.html','evidence/phase75/latest.json','phase7.html','evidence/phase7/latest.json','phase1.html','phase2.html','phase3.html','phase4.html','phase5.html','phase6.html','evidence/phase6/latest.json','evidence/phase5/latest.json','evidence/phase4/latest.json','evidence/phase1/summary.json','evidence/phase2/summary.json','evidence/phase1/screenshots/glimmer-platform.png','evidence/phase2/screenshots/veil-platform.png','evidence/phase3/latest.json'];
    const links=[];for(const url of legacy){const response=await fetch(base+url);links.push({url,status:response.status});assert(response.ok,'Broken permanent link '+url);}
    const detail=await browser.newPage({viewport:{width:390,height:844}});await detail.goto(base+'phase4.html');await detail.waitForFunction(()=>document.documentElement.dataset.phase4Ready==='true');await detail.waitForFunction(()=>Array.from(document.querySelectorAll('#pictures img,#before,#after')).every(i=>i.complete&&i.naturalWidth===1280));assert((await overflow(detail)).width<=390,'Phase 4 detail overflow');await detail.screenshot({path:path.join(out,'phase4-detail-390.png'),fullPage:true});await detail.close();
    const fifth=await browser.newPage({viewport:{width:390,height:844}});await fifth.goto(base+'phase5.html');await fifth.waitForFunction(()=>document.documentElement.dataset.phase5Ready==='true');await fifth.waitForFunction(()=>Array.from(document.querySelectorAll('#pictures img,#before,#after')).every(i=>i.complete&&i.naturalWidth===1280));assert((await overflow(fifth)).width<=390,'Phase 5 detail overflow');await fifth.screenshot({path:path.join(out,'phase5-detail-390.png'),fullPage:true});await fifth.close();
    const sixth=await browser.newPage({viewport:{width:390,height:844}});await sixth.goto(base+'phase6.html');await sixth.waitForFunction(()=>document.documentElement.dataset.phase6Ready==='true');await sixth.locator('#pictures img').evaluateAll(nodes=>nodes.forEach(i=>i.loading='eager'));await sixth.waitForFunction(()=>Array.from(document.querySelectorAll('#pictures img,#before,#after')).every(i=>i.complete&&i.naturalWidth===1280));assert((await overflow(sixth)).width<=390,'Phase 6 detail overflow');await sixth.locator('#compare-pair').selectOption('blue95,blue95-fog');await sixth.locator('#compare-view').selectOption('cliff');await sixth.waitForFunction(()=>document.getElementById('after').complete&&document.getElementById('after').src.includes('blue95-fog-cliff'));await sixth.screenshot({path:path.join(out,'phase6-detail-390.png'),fullPage:true});await sixth.close();
    const seventh=await browser.newPage({viewport:{width:390,height:844}});await seventh.goto(base+'phase7.html');await seventh.waitForFunction(()=>document.documentElement.dataset.phase7Ready==='true');await seventh.locator('#pictures img').evaluateAll(nodes=>nodes.forEach(i=>i.loading='eager'));await seventh.waitForFunction(()=>Array.from(document.querySelectorAll('#pictures img')).every(i=>i.complete&&i.naturalWidth===1280));assert((await overflow(seventh)).width<=390,'Phase 7 detail overflow');await seventh.screenshot({path:path.join(out,'phase7-detail-390.png'),fullPage:true});await seventh.close();
    const eighth=await browser.newPage({viewport:{width:390,height:844}});await eighth.goto(base+'phase75.html');await eighth.waitForFunction(()=>document.documentElement.dataset.phase75Ready==='true');await eighth.locator('#pictures img').evaluateAll(nodes=>nodes.forEach(i=>i.loading='eager'));await eighth.waitForFunction(()=>Array.from(document.querySelectorAll('#pictures img')).every(i=>i.complete&&i.naturalWidth===1280));assert((await overflow(eighth)).width<=390,'Phase 7.5 detail overflow');await eighth.screenshot({path:path.join(out,'phase75-detail-390.png'),fullPage:true});await eighth.close();
    const report={status:'PASS',preview:!!deployment.preview,deployed_source:deployment.commit,base,viewports:results,legacy_links:links,javascript_errors:errors,limit:'Chromium mobile viewport/touch emulation on hosted Linux; not real iQOO, Safari or Windows testing'};
    fs.writeFileSync(path.join(out,'summary.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
  }catch(error){fs.writeFileSync(path.join(out,'failure.json'),JSON.stringify({status:'FAIL',error:error.message,deployment,results,errors},null,2)+'\n');throw error;}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
