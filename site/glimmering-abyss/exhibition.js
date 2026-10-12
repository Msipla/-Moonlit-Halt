'use strict';
const byId = id => document.getElementById(id);
const viewNames = {platform:'观景台',tunnel:'通道',edge:'深渊边缘',overlook:'俯视'};
let project, currentStage, currentView='platform', currentMode, currentImage;
const text = (tag, value, className) => {
  const node=document.createElement(tag);node.textContent=value;if(className)node.className=className;return node;
};
function imageUrl(stage, mode, view) {
  const variant=stage.modes.find(x=>x.id===mode);
  return stage.base+(variant.file||mode+'-'+view+'.png');
}
function openImage(url,title) {
  byId('lightbox-title').textContent=title;
  byId('lightbox-image').src=url;byId('lightbox-image').alt=title+' · 未修改的游戏原图';
  byId('lightbox-original').href=url;byId('lightbox').showModal();
}
function badge(value,state='') {return text('span',value,'badge '+state);}
function stageLink(stage) {return '?stage='+stage.id+'#gallery';}
function chooseStage(id, updateHistory=false) {
  currentStage=project.stages.find(x=>x.id===id)||project.stages.find(x=>x.id===project.latestStage);
  currentView='platform';currentMode=currentStage.defaultMode;
  if(updateHistory){const url=new URL(location.href);url.searchParams.set('stage',currentStage.id);url.hash='gallery';history.pushState({},'',url);}
  renderGallery();
}
function renderOverview() {
  const latest=project.stages.find(s=>s.id===project.latestStage);
  byId('hero-image').src=latest.base+latest.cover;
  byId('hero-image').alt=latest.title+' · 实际客户端截图';
  byId('header-stage').textContent=latest.developmentLabel;
  byId('hero-photo-label').textContent='真实客户端截图 · v'+latest.version;
  byId('technical-status').textContent=latest.technicalState==='passed'?'已实际运行':'尚待验证';
  byId('aesthetic-status').textContent=latest.aestheticState==='failed'?'美学不通过':latest.aestheticState==='accepted'?'用户已通过':'等待用户判断';
  byId('development-status').textContent=latest.developmentLabel;
  const cards=byId('stage-cards');cards.replaceChildren();
  const tabs=byId('stage-tabs');tabs.replaceChildren();
  for(const s of project.stages){
    const a=document.createElement('a');a.className='stage-card';a.href=stageLink(s);a.dataset.stage=s.id;
    const img=document.createElement('img');img.src=s.base+s.cover;img.alt=s.title+' · 实际游戏画面';img.loading='lazy';img.width=1280;img.height=720;
    const body=document.createElement('div');body.className='stage-card-content';body.append(text('span','PHASE '+s.number,'stage-number'),text('h3',s.title.split(' · ')[1]),text('p',s.description));
    const badges=document.createElement('div');badges.className='badges';badges.append(badge(s.technical),badge(s.aesthetic,s.aestheticState==='failed'?'failed':'art'));body.append(badges,text('div','回看本阶段画面 →','card-action'));a.append(img,body);
    a.addEventListener('click',event=>{event.preventDefault();chooseStage(s.id,true);byId('gallery').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'});});cards.append(a);
    const button=text('button',s.short);button.dataset.stageTab=s.id;button.addEventListener('click',()=>chooseStage(s.id,true));tabs.append(button);
  }
  const timeline=byId('timeline-list');timeline.replaceChildren();
  for(const item of project.timeline){const li=document.createElement('li');li.append(text('span',item.date,'time'),text('h3',item.title),text('p',item.text),text('span',item.state,'timeline-state'));timeline.append(li);}
  byId('latest-summary').href=latest.evidenceBase+'summary.json';
}
function renderGallery() {
  const s=currentStage;
  byId('gallery-heading').textContent=s.title;byId('gallery-description').textContent=s.description;byId('stage-verdict').textContent=s.verdict;
  for(const b of document.querySelectorAll('[data-stage-tab]'))b.setAttribute('aria-pressed',b.dataset.stageTab===s.id);
  const views=byId('view-tabs');views.replaceChildren();
  for(const v of s.views){const b=text('button',viewNames[v]);b.dataset.view=v;b.setAttribute('aria-pressed',v===currentView);b.addEventListener('click',()=>{currentView=v;const mode=s.modes.find(x=>x.id===currentMode);if(mode.onlyView&&mode.onlyView!==v)currentMode=s.defaultMode;renderGallery();});views.append(b);}
  const modes=byId('mode-tabs');modes.replaceChildren();
  for(const mode of s.modes){const b=text('button',mode.label);b.dataset.mode=mode.id;b.setAttribute('aria-pressed',mode.id===currentMode);b.addEventListener('click',()=>{currentMode=mode.id;if(mode.onlyView)currentView=mode.onlyView;renderGallery();});modes.append(b);}
  const mode=s.modes.find(x=>x.id===currentMode);
  currentImage={url:imageUrl(s,currentMode,currentView),title:viewNames[currentView]+' · '+mode.label};
  const img=byId('gallery-image');img.src=currentImage.url;img.alt=s.short+' '+currentImage.title+' · 实际客户端原始截图';
  byId('image-title').textContent=currentImage.title;byId('original-link').href=currentImage.url;
  byId('image-note').textContent=mode.onlyView?'此控制方案只有观景台原图，已切换到对应机位。':'实际 Minecraft 客户端画面，未经提亮或调色。';
  const extras=byId('extra-images');extras.replaceChildren();
  for(const item of s.extras){const article=document.createElement('article');article.className='extra-card';const b=document.createElement('button');b.setAttribute('aria-label','放大：'+item.title);const i=document.createElement('img');i.src=s.base+item.file;i.alt=item.title+' · 原始游戏帧';i.loading='lazy';b.append(i);b.addEventListener('click',()=>openImage(s.base+item.file,item.title));article.append(b,text('p',item.title));extras.append(article);}
  byId('extra-images').parentElement.hidden=s.extras.length===0;
}
function compareUrl(mode,view) {
  const latest=project.stages.find(s=>s.id===project.latestStage);
  if(['blue','copper'].includes(mode))return latest.base+mode+'-'+view+'.png';
  // Use the new, drained-queue native controls where they exist. Historical A tunnel is preserved separately.
  if(mode==='a'&&['platform','tunnel'].includes(view))return latest.base+'a-'+view+'.png';
  const second=project.stages.find(s=>s.id==='phase2');return second.base+mode+'-'+(mode==='veil'?'platform':view)+'.png';
}
function updateCompare() {
  if(!project)return;
  const [left,right]=byId('compare-pair').value.split(',');const control=byId('compare-view');
  const veil=left==='veil';control.disabled=veil;if(veil)control.value='platform';
  const view=control.value, labels={a:'原版基线 A',b:'第二阶段 B',c:'第二阶段 C',veil:'Veil · 雾幕反例',blue:'最新空间光 · 冷蓝',copper:'最新空间光 · 铜金'};
  byId('compare-top').src=compareUrl(left,view);byId('compare-top').alt=labels[left]+' · '+viewNames[view]+'原图';
  byId('compare-bottom').src=compareUrl(right,view);byId('compare-bottom').alt=labels[right]+' · '+viewNames[view]+'原图';
  byId('compare-left-label').textContent=labels[left];byId('compare-right-label').textContent=labels[right];
  byId('compare-note').textContent=veil?'Veil 只有观景台原图，已固定到相同机位；它是保留的雾幕反例。':left==='a'&&['platform','tunnel'].includes(view)?'原版 A 使用第三阶段重新采集的同机位控制图。历史原图仍在旧报告中保留。':'同一洞厅、相同机位、视距与显示设置。历史帧和新帧均保持原样；不同运行环境的性能不作直接比较。';
}
byId('compare-range').addEventListener('input',event=>{const n=event.target.value;byId('compare-top').style.clipPath='inset(0 '+(100-n)+'% 0 0)';byId('compare-divider').style.left=n+'%';byId('compare-value').value=n+'%';});
byId('compare-pair').addEventListener('change',updateCompare);byId('compare-view').addEventListener('change',updateCompare);
byId('open-image').addEventListener('click',()=>currentImage&&openImage(currentImage.url,currentImage.title));
byId('zoom-image').addEventListener('click',()=>currentImage&&openImage(currentImage.url,currentImage.title));
byId('close-image').addEventListener('click',()=>byId('lightbox').close());
byId('lightbox').addEventListener('click',event=>{const d=byId('lightbox'),r=d.getBoundingClientRect();if(event.target===d&&(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom))d.close();});
addEventListener('popstate',()=>project&&chooseStage(new URL(location.href).searchParams.get('stage')));
async function init(){
  const response=await fetch('exhibition.json');if(!response.ok)throw Error('阶段索引暂时不可用');project=await response.json();if(project.schema!==1)throw Error('阶段索引格式不匹配');
  await Promise.all(project.stages.map(async stage=>{if(stage.pointer){const r=await fetch(stage.pointer);if(!r.ok)throw Error('最新实跑记录暂时不可用');const latest=await r.json();stage.evidenceBase='evidence/'+stage.id+'/runs/'+latest.run+'/';stage.base=stage.evidenceBase+'screenshots/';}else stage.evidenceBase='evidence/'+stage.id+'/';}));
  renderOverview();chooseStage(new URL(location.href).searchParams.get('stage'));updateCompare();document.documentElement.dataset.galleryReady='true';
}
init().catch(error=>{const box=byId('load-error');box.hidden=false;box.textContent=error.message+'。你仍可打开下方永久阶段报告。';});
