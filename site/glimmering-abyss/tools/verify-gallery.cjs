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
      for(const id of ['phase1','phase2','phase3','phase4']){
        await page.locator('[data-stage-tab="'+id+'"]').click();await ready(page);
        assert(await page.locator('[data-stage-tab="'+id+'"]').getAttribute('aria-pressed')==='true','Stage did not switch');
        assert(page.url().includes('stage='+id),'Stage permalink missing');
      }
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
    const legacy=['phase1.html','phase2.html','phase3.html','phase4.html','evidence/phase4/latest.json','evidence/phase1/summary.json','evidence/phase2/summary.json','evidence/phase1/screenshots/glimmer-platform.png','evidence/phase2/screenshots/veil-platform.png','evidence/phase3/latest.json'];
    const links=[];for(const url of legacy){const response=await fetch(base+url);links.push({url,status:response.status});assert(response.ok,'Broken permanent link '+url);}
    const detail=await browser.newPage({viewport:{width:390,height:844}});await detail.goto(base+'phase4.html');await detail.waitForFunction(()=>document.documentElement.dataset.phase4Ready==='true');await detail.waitForFunction(()=>Array.from(document.querySelectorAll('#pictures img,#before,#after')).every(i=>i.complete&&i.naturalWidth===1280));assert((await overflow(detail)).width<=390,'Phase 4 detail overflow');await detail.screenshot({path:path.join(out,'phase4-detail-390.png'),fullPage:true});await detail.close();
    const report={status:'PASS',deployed_source:deployment.commit,base,viewports:results,legacy_links:links,javascript_errors:errors,limit:'Chromium mobile viewport/touch emulation on hosted Linux; not real iQOO, Safari or Windows testing'};
    fs.writeFileSync(path.join(out,'summary.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
  }catch(error){fs.writeFileSync(path.join(out,'failure.json'),JSON.stringify({status:'FAIL',error:error.message,deployment,results,errors},null,2)+'\n');throw error;}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
