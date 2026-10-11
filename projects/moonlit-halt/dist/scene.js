/* Moonlit Halt — dependency-free WebGL 1 scene. All assets are procedural. */
'use strict';
(() => {
const $=id=>document.getElementById(id), canvas=$('scene');
const gl=canvas.getContext('webgl',{alpha:false,antialias:true,powerPreference:'high-performance'});
function fail(message){$('errorText').textContent=message;$('error').classList.remove('hidden');}
if(!gl){fail('浏览器未能启用 WebGL。请尝试更新 Chrome，或使用支持 WebGL 的浏览器。');return;}
const vertex=`attribute vec3 aPosition;attribute vec3 aNormal;attribute vec4 aColor;attribute float aMaterial;
uniform mat4 uVP;varying vec3 vP;varying vec3 vN;varying vec4 vC;varying float vM;
void main(){vP=aPosition;vN=aNormal;vC=aColor;vM=aMaterial;gl_Position=uVP*vec4(aPosition,1.0);}`;
const fragment=`precision mediump float;
varying vec3 vP;varying vec3 vN;varying vec4 vC;varying float vM;
uniform vec3 uEye;uniform vec3 uLights[6];uniform float uTime;uniform sampler2D uAtlas;uniform sampler2D uMoon;
float rect(vec2 p,vec2 c,vec2 size){vec2 d=abs(p-c)-size;return 1.0-smoothstep(-0.1,0.7,max(d.x,d.y));}
void main(){
 vec3 n=normalize(vN),moonDir=normalize(vec3(-0.45,0.72,-0.55));
 vec3 viewDir=normalize(uEye-vP);float moon=max(dot(n,moonDir),0.0);
 vec3 albedo=vC.rgb;float mat=floor(vM+0.5);
 if(mat>0.5&&mat<8.0){
  vec3 an=abs(n);vec2 uv=an.y>0.68?vP.xz:(an.x>an.z?vP.zy:vP.xy);
  float scale=mat==1.0?2.7:mat==2.0?1.1:mat==3.0?0.7:mat==4.0?2.1:mat==5.0?3.5:mat==6.0?0.45:1.6;
  if(mat==3.0)uv*=vec2(3.0,0.42);
  float tile=mat-1.0;vec2 atlas=(vec2(mod(tile,4.0),floor(tile/4.0))+mix(vec2(0.005),vec2(0.995),fract(uv*scale)))/vec2(4.0,2.0);
  vec3 tex=texture2D(uAtlas,atlas).rgb;albedo*=mat==6.0?mix(vec3(1.0),tex,0.4):tex;
 }
 // Broad cool fill preserves dark detail; a directional silver light shapes roofs and stone.
 float occlusion=1.0;
 if(vP.y<0.6){occlusion*=1.0-0.34*rect(vP.xz,vec2(-2.0,-33.0),vec2(6.5,3.9));occlusion*=1.0-0.25*rect(vP.xz,vec2(-14.0,-24.0),vec2(3.3,2.9));}
 if(vP.z>-29.7&&vP.z<-26.4&&vP.x>-8.5&&vP.x<4.6&&vP.y>2.5&&vP.y<3.6)occlusion*=0.7;
 vec3 light=(vec3(0.17,0.22,0.32)+moon*vec3(0.23,0.32,0.48))*occlusion;
 for(int i=0;i<6;i++){
  vec3 d=uLights[i]-vP;float dist=length(d);float radius=i<3?3.8:2.0;
  float falloff=max(1.0-dist/radius,0.0);falloff*=falloff;
  float lam=max(dot(n,normalize(d)),0.0);float pulse=0.98+0.02*sin(uTime*1.7+float(i));
  light+=vec3(1.0,0.63,0.32)*pulse*falloff*lam*(i<3?1.25:0.46);
 }
 if(mat==1.0){float grime=smoothstep(2.7,4.2,vP.y)*0.11+0.08*(0.5+0.5*sin(vP.x*5.7+vP.z*2.4))*smoothstep(2.0,4.4,vP.y);albedo*=1.0-grime;}
 vec3 color=albedo*light;if(mat==11.0)color=vC.rgb*0.9;
 if(mat==5.0){float spec=pow(max(dot(n,normalize(moonDir+viewDir)),0.0),24.0);color+=vec3(0.055,0.085,0.13)*spec;}
 if(vC.a>1.5){
  color=vC.rgb;
  if(mat==8.0){vec2 uv=vec2((vP.x+52.0)/8.2+0.5,0.5-(vP.y-51.0)/8.2);gl_FragColor=vec4(texture2D(uMoon,uv).rgb*vec3(1.02,1.03,1.04),1.0);return;}
  if(mat==9.0){color*=0.87+0.09*sin(vP.y*2.1+vP.x*0.7);color+=vec3(0.025,0.04,0.065)*pow(1.0-max(dot(n,viewDir),0.0),3.0);}
 }
 // No fog veil. Only restrained distant atmospheric colour, with the horizon intact.
 float d=length(vP-uEye);color=mix(color,vec3(0.025,0.045,0.075),clamp((d-80.0)/300.0,0.0,0.22));
 color=color/(color+vec3(0.62));color=pow(color,vec3(0.82));
 gl_FragColor=vec4(color,min(vC.a,1.0));
}`;
function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
function program(v,f){const p=gl.createProgram();gl.attachShader(p,shader(gl.VERTEX_SHADER,v));gl.attachShader(p,shader(gl.FRAGMENT_SHADER,f));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;}
let p;try{p=program(vertex,fragment);}catch(e){fail('渲染器初始化失败：'+e.message);return;}
const attrs=['aPosition','aNormal','aColor','aMaterial'].map(n=>gl.getAttribLocation(p,n));
const uniforms=Object.fromEntries(['uVP','uEye','uLights[0]','uTime','uAtlas','uMoon'].map(n=>[n,gl.getUniformLocation(p,n)]));
const data=[], dynamic=[], lights=[[5.8,3.05,-25],[8.8,3.05,-39],[-10,3.05,-21],[-6.3,2.15,-29.08],[-15.3,1.8,-21.08],[2.2,2.15,-29.08]];
let seed=4319;function rand(){seed=(seed*1664525+1013904223)>>>0;return seed/4294967296;}const color=(r,g,b,a=1,m=0)=>[r,g,b,a,m];
const brick=color(.34,.225,.205,1,1), stone=color(.42,.44,.46,1,2), wood=color(.21,.18,.14,1,3), iron=color(.13,.18,.185,1,5), slate=color(.20,.25,.31,1,4), grass=color(.13,.20,.17,1,6), glow=color(.38,.22,.09,2,9);
function sub(a,b){return a.map((v,i)=>v-b[i]);}function cross(a,b){return[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];}function norm(a){let l=Math.hypot(...a)||1;return a.map(v=>v/l);}
function tri(a,b,c,col,target=data){let n=norm(cross(sub(b,a),sub(c,a)));for(let v of [a,b,c])target.push(...v,...n,...col.slice(0,4),col[4]||0);}
function quad(a,b,c,d,col,target=data){tri(a,b,c,col,target);tri(a,c,d,col,target);}
function box(x,y,z,w,h,d,col,target=data){const a=x-w/2,b=x+w/2,c=y-h/2,e=y+h/2,f=z-d/2,g=z+d/2;quad([a,c,g],[b,c,g],[b,e,g],[a,e,g],col,target);quad([b,c,f],[a,c,f],[a,e,f],[b,e,f],col,target);quad([a,c,f],[a,c,g],[a,e,g],[a,e,f],col,target);quad([b,c,g],[b,c,f],[b,e,f],[b,e,g],col,target);quad([a,e,g],[b,e,g],[b,e,f],[a,e,f],col,target);quad([a,c,f],[b,c,f],[b,c,g],[a,c,g],col,target);}
function cylinder(x,y,z,r,h,col,sides=8,target=data,r2=r){for(let i=0;i<sides;i++){let a=i*2*Math.PI/sides,b=(i+1)*2*Math.PI/sides;let p=[x+r*Math.cos(a),y,z+r*Math.sin(a)],q=[x+r*Math.cos(b),y,z+r*Math.sin(b)],s=[x+r2*Math.cos(b),y+h,z+r2*Math.sin(b)],t=[x+r2*Math.cos(a),y+h,z+r2*Math.sin(a)];quad(p,q,s,t,col,target);tri([x,y+h,z],t,s,col,target);tri([x,y,z],q,p,col,target);}}
function sphere(x,y,z,r,col,target=data,segments=12,rings=7,sx=1,sy=1,sz=1){const v=(a,b)=>[x+r*Math.sin(b)*Math.cos(a)*sx,y+r*Math.cos(b)*sy,z+r*Math.sin(b)*Math.sin(a)*sz];for(let j=0;j<rings;j++)for(let i=0;i<segments;i++){let a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments,c=j*Math.PI/rings,d=(j+1)*Math.PI/rings;let vv=[v(a,c),v(a,d),v(b,d),v(b,c)];for(let k of [0,1,2,0,2,3]){let q=vv[k],nn=norm([(q[0]-x)/(sx*sx),(q[1]-y)/(sy*sy),(q[2]-z)/(sz*sz)]);target.push(...q,...nn,...col.slice(0,4),col[4]||0);}}}
function beam(a,b,r,col){const d=sub(b,a),length=Math.hypot(...d),n=norm(cross(d,Math.abs(d[1])>.9*length?[1,0,0]:[0,1,0])),m=norm(cross(d,n));const corners=v=>[[1,1],[1,-1],[-1,-1],[-1,1]].map(([i,j])=>v.map((c,k)=>c+r*(i*n[k]+j*m[k])));let aa=corners(a),bb=corners(b);for(let i=0;i<4;i++)quad(aa[i],aa[(i+1)%4],bb[(i+1)%4],bb[i],col);}
function brickwall(x,z,w,d,h){box(x,h/2,z,w,h,d,color(.15,.13,.12));for(let row=0;row<h/.095;row++){let y=.0475+row*.095;for(let side of[-1,1])for(let xx=x-w/2+.12+(row%2)*.1275;xx<x+w/2-.1;xx+=.255){let tint=.72+rand()*.38;let soot=1-.28*(y/h);box(xx,y,z+side*d/2,.244,.084,.032,color(brick[0]*tint*soot,brick[1]*tint*soot,brick[2]*tint*soot,1,1));}for(let side of[-1,1])for(let zz=z-d/2+.12+(row%2)*.1275;zz<z+d/2-.1;zz+=.255){let t=.7+rand()*.4;box(x+side*w/2,y,zz,.032,.084,.244,color(.32*t,.21*t,.19*t,1,1));}}for(let side of[-1,1])box(x,.32,z+side*d/2,w+.1,.55,.18,stone);for(let side of[-1,1])box(x+side*w/2,.32,z,.18,.55,d,stone);}
function lamp(x,z){
 cylinder(x,0,z,.24,.13,stone,18);cylinder(x,.13,z,.13,.20,iron,18);cylinder(x,.33,z,.065,2.30,iron,18);cylinder(x,2.55,z,.105,.12,iron,16);cylinder(x,2.68,z,.18,.13,iron,16,data,.26);
 box(x,3.02,z,.30,.43,.30,color(.24,.16,.08,2,9));sphere(x,3.03,z,.067,color(.95,.70,.33,2),data,14,9,1,1.6,1);
 for(let a of[-1,1])for(let b of[-1,1])beam([x+a*.18,2.80,z+b*.18],[x+a*.135,3.27,z+b*.135],.018,iron);
 cylinder(x,3.29,z,.28,.23,iron,4,data,.055);sphere(x,3.55,z,.045,color(.26,.28,.27,1,5),data,12,7);
}
function pool(x,z,r,c){for(let i=0;i<24;i++){let a=i*Math.PI/12,b=(i+1)*Math.PI/12;tri([x,.035,z],[x+r*Math.cos(a),.035,z+r*Math.sin(a)],[x+r*Math.cos(b),.035,z+r*Math.sin(b)],c,dynamic);}}
// Rolling ground: deliberately no distance fog.
function terrainHeight(x,z){return Math.min(0, -.025*Math.hypot(x,z+25)) + .13*Math.sin(x*.18)*Math.cos(z*.13);}
for(let x=-110;x<110;x+=5)for(let z=-135;z<60;z+=5){let c=color(.155+rand()*.02,.205+rand()*.02,.175+rand()*.02,1,6);quad([x,terrainHeight(x,z)-.13,z],[x,terrainHeight(x,z+5)-.13,z+5],[x+5,terrainHeight(x+5,z+5)-.13,z+5],[x+5,terrainHeight(x+5,z)-.13,z],c);}
for(let i=0;i<16;i++){let a=i*Math.PI*2/16,x=Math.cos(a)*115,z=-30+Math.sin(a)*120;sphere(x,-5,z,23+rand()*18,color(.11,.15,.21),data,32,16,1, .45+rand()*.28,1);}
// Gently curving gravel path, 30 m from spawn to station front.
const path=[];for(let i=0;i<=22;i++){let z=9-i*1.75,x=Math.sin(i*.16)*2.1;path.push([x,z]);}
for(let i=0;i<path.length-1;i++){let[x,z]=path[i],[xx,zz]=path[i+1];quad([x-1.2,.015,z],[x+1.2,.015,z],[xx+1.2,.015,zz],[xx-1.2,.015,zz],color(.32,.32,.29,1,7));}
for(let i=0;i<550;i++){let z=8-rand()*37,x=Math.sin((9-z)/1.75*.16)*2.1+(rand()-.5)*2.3;box(x,.027,z,.03+rand()*.07,.018,.04+rand()*.09,color(.31,.31,.285,1,2));}
// Victorian rural station: original footprint, longitudinal slate roof and a narrow entrance gable.
function moulding(x,z,w,d,y){box(x,y,z,w+.18,.13,d+.18,stone);box(x,y+.12,z,w+.31,.07,d+.31,color(.33,.36,.39,1,2));}
function curve(points,r,col){for(let i=1;i<points.length;i++)beam(points[i-1],points[i],r,col);}
function arch(x,y,z,w,rise,thickness,col){const n=20;for(let i=0;i<n;i++){let a=i*Math.PI/n,b=(i+1)*Math.PI/n;let pa=[x+Math.cos(a)*(w/2+thickness),y+Math.sin(a)*(rise+thickness),z],pb=[x+Math.cos(b)*(w/2+thickness),y+Math.sin(b)*(rise+thickness),z],pc=[x+Math.cos(b)*w/2,y+Math.sin(b)*rise,z+.012],pd=[x+Math.cos(a)*w/2,y+Math.sin(a)*rise,z+.012];quad(pa,pb,pc,pd,col);}}
function brickGable(x,z,w,y,rise){tri([x-w/2,y,z],[x+w/2,y,z],[x,y+rise,z],color(.17,.12,.11,1,1));for(let row=0;row<rise/.095;row++){let yy=row*.095+.042,half=w/2*(1-yy/rise);for(let xx=-half+.12+(row%2)*.1275;xx<half-.06;xx+=.255){let t=.85+rand()*.24;box(x+xx,y+yy,z+.015,.244,.084,.032,color(.30*t,.20*t,.18*t,1,1));}}}
function quadUp(a,b,c,d,col){if(cross(sub(b,a),sub(c,a))[1]<0)quad(a,d,c,b,col);else quad(a,b,c,d,col);}
function detailedRoof(x,z,w,d,eave,rise,longitudinal=false){
 const coord=(u,y,v)=>longitudinal?[x+v,y,z+u]:[x+u,y,z+v];let half=(longitudinal?d:w)/2+.32,len=(longitudinal?w:d)/2+.32;
 for(let side of[-1,1]){quadUp(coord(0,eave+rise,-len),coord(0,eave+rise,len),coord(side*half,eave,len),coord(side*half,eave,-len),slate);
  let slope=Math.hypot(half,rise),rows=Math.ceil(slope/.30);for(let row=0;row<rows;row++){let t0=row/rows,t1=Math.min(1,(row+1.08)/rows);for(let v=-len-.18*(row%2);v<len;v+=.36){let v0=Math.max(-len,v),v1=Math.min(len,v+.348),tint=.83+rand()*.27;quadUp(coord(side*half*t0,eave+rise*(1-t0)+.018,v0),coord(side*half*t0,eave+rise*(1-t0)+.018,v1),coord(side*half*t1,eave+rise*(1-t1)+.027,v1),coord(side*half*t1,eave+rise*(1-t1)+.027,v0),color(.20*tint,.24*tint,.30*tint,1,4));}}
  beam(coord(side*half,eave,-len),coord(side*half,eave,len),.065,iron);
 }
 for(let v=-len;v<len;v+=.32){
  let c=coord(0,eave+rise+.04,v+.16);box(c[0],c[1],c[2],longitudinal?.34:.16,.13,longitudinal?.16:.34,color(.26,.28,.30,1,4));}
 for(let end of[-len,len]){let zz=longitudinal?z:z+end,xx=longitudinal?x+end:x;
  for(let side of[-1,1]){beam(coord(side*half,eave,end),coord(0,eave+rise,end),.10,stone);beam(coord(side*(half-.08),eave-.08,end+.025),coord(0,eave+rise-.10,end+.025),.035,color(.22,.24,.26,1,2));}
  if(!longitudinal)brickGable(x,z+end,half*2,eave,rise);
  else tri(coord(-half,eave,end),coord(half,eave,end),coord(0,eave+rise,end),brick);
 }
}
function sash(x,y,z,w=1.18,h=1.72){
 box(x,y,z,w+.34,h+.35,.20,color(.19,.22,.24,1,2));
 // Recessed warm glass sits behind sash and thick stone jambs; no luminous frame.
 box(x,y,z+.113,w,h,.024,glow);
 for(let side of[-1,1]){box(x+side*(w/2+.085),y,z+.19,.16,h+.10,.20,stone);box(x+side*(w/2-.045),y,z+.23,.07,h,.085,color(.12,.19,.18,1,3));}
 box(x,y-h/2-.10,z+.24,w+.50,.16,.38,stone);box(x,y-h/2-.21,z+.16,w+.34,.07,.24,color(.29,.31,.32,1,2));
 box(x,y+h/2+.06,z+.20,w+.20,.09,.19,stone);
 arch(x,y+h/2+.08,z+.225,w+.18,.25,.17,stone);box(x,y+h/2+.40,z+.23,.18,.17,.21,stone);
 box(x,y,z+.235,w,.065,.055,color(.12,.19,.18,1,3));box(x,y,z+.235,.046,h,.045,color(.12,.19,.18,1,3));
 for(let side of[-1,1])box(x+side*w*.32,y,z+.231,.027,h,.04,wood);
 // Quiet curtains reduce the solid yellow rectangle.
 for(let side of[-1,1])for(let j=0;j<3;j++)box(x+side*(w*.43-j*.045),y,z+.16,.047,h-.08,.024,color(.35+j*.01,.30,.22,1,3));
 box(x,y-h*.35,z+.215,w-.10,.045,.02,color(.20,.18,.13));
}
function stack(x,z,y,h){
 box(x,y+h/2,z,.74,h,.82,brick);for(let yy=y+.10;yy<y+h;yy+=.16){box(x,yy,z+.42,.77,.016,.02,color(.10,.09,.09));box(x+.38,yy,z,.016,.014,.83,color(.10,.09,.09));}
 for(let yy of[y+h-.35,y+h-.16])box(x,yy,z,.91,.11,.98,color(.27,.19,.16,1,1));box(x,y+h+.02,z,.99,.14,1.06,stone);
 for(let dx of[-.2,.2]){cylinder(x+dx,y+h+.10,z,.13,.45,color(.34,.24,.20,1,1),16,data,.15);cylinder(x+dx,y+h+.53,z,.18,.06,color(.19,.15,.13,1,1),16);}
}
brickwall(-2,-33,12,7,3.92);moulding(-2,-33,12,7,3.8);detailedRoof(-2,-33,12,7,3.95,2.30,true);
// Entry bay remains within the old approach and projects just 65 cm.
brickwall(-.4,-29.5,3.1,1.3,4.05);detailedRoof(-.4,-29.5,3.1,1.3,4.07,1.95);
for(let x of[-6.3,-3.85,2.2])sash(x,2.05,-29.42);
function sideSash(x,y,z,w,h){let start=data.length;sash(0,y,0,w,h);for(let i=start;i<data.length;i+=11){let px=data[i],pz=data[i+2],nx=data[i+3],nz=data[i+5];data[i]=x+pz;data[i+2]=z-px;data[i+3]=nz;data[i+5]=-nx;}}
for(let z of[-31.25,-34.6])sideSash(4.03,2.05,z,1.15,1.70);

for(let x of[-8.06,4.06])for(let yy=.68;yy<3.8;yy+=.36){box(x,yy,-29.42,.32,.20,.13,color(.33,.35,.37,1,2));box(x,yy,-36.56,.32,.20,.13,stone);}
// A restrained stone string course and dentilled brick eaves.
for(let z of[-29.44,-36.56]){box(-2,.66,z,12.1,.095,.12,stone);box(-2,3.64,z,12.1,.09,.13,color(.27,.23,.22,1,1));for(let xx=-7.9;xx<4;xx+=.34)box(xx,3.75,z+.018,.14,.13,.17,color(.24,.18,.17,1,1));}
// Panelled station door in the narrow gable, with a softly lit fanlight.
box(-.4,1.35,-28.77,1.48,2.70,.18,color(.095,.15,.145,1,3));
for(let xx of[-.86,.06])for(let yy of[.64,1.44]){box(xx,yy,-28.65,.53,.54,.055,color(.14,.19,.175,1,3));box(xx,yy,-28.61,.41,.41,.03,color(.095,.14,.13,1,3));}
box(-.4,2.37,-28.64,1.13,.45,.026,color(.27,.17,.075,2,9));box(-.4,2.37,-28.59,.04,.45,.03,wood);
for(let xx of[-1.24,.44])box(xx,1.36,-28.61,.16,2.78,.27,stone);arch(-.4,2.73,-28.61,1.64,.32,.16,stone);box(-.4,3.12,-28.58,.23,.18,.22,stone);
sphere(.08,1.19,-28.53,.048,color(.36,.32,.19,1,5),data,12,7);box(-.4,.12,-28.38,2.25,.24,.64,stone);
// Stone-faced clock with bezel and fine minute marks, not a large yellow disk.
sphere(-.4,4.77,-28.48,.36,color(.36,.39,.40,1,2),data,28,16,1,1,.16);sphere(-.4,4.77,-28.40,.30,color(.52,.53,.49),data,28,16,1,1,.07);
for(let i=0;i<12;i++){let a=i*Math.PI/6;beam([-.4+Math.sin(a)*.235,4.77+Math.cos(a)*.235,-28.372],[-.4+Math.sin(a)*.27,4.77+Math.cos(a)*.27,-28.372],.010,iron);}
beam([-.4,4.77,-28.36],[-.4,4.98,-28.36],.016,iron);beam([-.4,4.77,-28.35],[-.22,4.69,-28.35],.018,iron);
beam([-.4,6.03,-29.5],[-.4,6.50,-29.5],.026,iron);sphere(-.4,6.52,-29.5,.06,iron,data,12,7);
stack(-5.3,-33.3,5.00,1.48);stack(2.8,-33.3,5.00,1.40);
// Front canopy: iron columns, scroll brackets, thin rafters and a scalloped fascia.
quadUp([-8.4,3.46,-29.38],[4.5,3.46,-29.38],[4.5,3.12,-26.40],[-8.4,3.12,-26.40],slate);
quad([-8.4,3.43,-29.38],[4.5,3.43,-29.38],[4.5,3.09,-26.40],[-8.4,3.09,-26.40],color(.12,.16,.17,1,3));
for(let xx=-8.4;xx<4.5;xx+=.34)beam([xx,3.41,-29.35],[xx,3.08,-26.4],.027,iron);
for(let x of[-7.75,-3.10,1.45,4.1]){
 cylinder(x,0,-26.65,.14,.12,stone,16);cylinder(x,.12,-26.65,.10,.16,iron,16);cylinder(x,.28,-26.65,.052,2.58,iron,16);cylinder(x,2.86,-26.65,.085,.15,iron,16);box(x,3.035,-26.65,.24,.08,.24,iron);
 for(let side of[-1,1]){let points=[];for(let i=0;i<=16;i++){let t=i/16;points.push([x+side*.85*Math.sin(t*Math.PI/2),2.35+.71*t,-26.65]);}curve(points,.022,iron);beam([x,3.0,-26.65],[x+side*.88,3.0,-26.65],.025,iron);}
 let points=[];for(let i=0;i<=16;i++){let t=i/16;points.push([x,2.35+.75*t,-26.65-.80*Math.sin(t*Math.PI/2)]);}curve(points,.023,iron);
}
beam([-8.4,3.10,-26.44],[4.5,3.10,-26.44],.065,iron);
for(let xx=-8.35;xx<4.5;xx+=.26){let pp=[];for(let i=0;i<=8;i++){let a=i*Math.PI/8;pp.push([xx+.12*Math.cos(a),3.06-.10*Math.sin(a),-26.40]);}curve(pp,.014,iron);}
// The same canopy wraps round the track-facing flank, within the existing platform.
quadUp([4.12,3.44,-29.2],[4.12,3.44,-36.8],[7.7,3.05,-36.8],[7.7,3.05,-29.2],slate);
for(let z of[-30,-35.9]){cylinder(7.5,.46,z,.10,.16,iron,16);cylinder(7.5,.62,z,.05,2.25,iron,16);cylinder(7.5,2.87,z,.085,.12,iron,16);let pts=[];for(let i=0;i<18;i++){let t=i/17;pts.push([7.5-.90*Math.sin(t*Math.PI/2),2.36+.64*t,z]);}curve(pts,.023,iron);}
for(let z=-36.8;z<-29.2;z+=.5)beam([4.12,3.42,z],[7.7,3.03,z],.029,iron);
// Downpipes and gutter lips are connected to the eaves.
for(let x of[-7.85,3.85]){cylinder(x,.2,-29.63,.045,3.65,iron,12);beam([x,3.85,-29.63],[x,3.91,-29.30],.04,iron);for(let yy of[1,2.5])box(x,yy,-29.54,.14,.06,.12,iron);}
box(-.4,3.75,-28.72,3.00,.46,.13,iron);
// Nearby staff cottage shares the same masonry, roof and recessed warm sash.
brickwall(-14,-24,5.8,5,3.1);detailedRoof(-14,-24,5.8,5,3.12,1.85);sash(-15.3,1.75,-21.42,1.15,1.45);
box(-12.9,1.10,-21.32,1.15,2.2,.17,color(.115,.18,.17,1,3));box(-12.9,1.76,-21.20,.66,.42,.02,color(.26,.16,.075,2,9));for(let yy of[.5,1.12])box(-12.9,yy,-21.20,.83,.44,.03,wood);stack(-15.8,-25.1,3.8,1.12);
for(let x of[-16.96,-11.04])for(let yy=.65;yy<3;yy+=.38)box(x,yy,-21.43,.28,.20,.13,stone);
// Coal hatch and modest stone edging retain the operational character.
for(let z=-28;z<-22;z+=.55)box(4.65,.14,z,.24,.28,.50,color(.29,.31,.32,1,2));
box(-8.09,1.1,-34.3,.16,1.25,1.5,iron);for(let z=-34.9;z<-33.6;z+=.18)box(-8.2,1.1,z,.035,1.1,.025,color(.24,.27,.27,1,5));
// Platform, ramp and sleepers. Track runs alongside the building.
box(6.7,.23,-34,3.6,.46,24,stone);for(let z=-46;z<-22;z+=.9){box(8.4,.49,z,.18,.055,.83,color(.44,.46,.47,1,2));box(6.7,.48,z,3.2,.02,.025,color(.21,.23,.25));}
quad([4.9,0,-19],[8.5,0,-19],[8.5,.46,-22],[4.9,.46,-22],stone);quad([4.9,0,-19],[4.9,.46,-22],[4.9,0,-22],[4.9,0,-19],stone);
box(11,-.01,-33,4.2,.12,65,color(.25,.26,.27,1,7));for(let z=-65;z<0;z+=.85){box(11,.10,z,3.2,.17,.23,wood);for(let x of[10.2,11.8]){box(x,.19,z,.25,.07,.31,iron);box(x+.15,.24,z,.05,.05,.07,color(.35,.34,.29));}}for(let x of[10.2,11.8]){box(x,.25,-33,.10,.22,65,iron);box(x,.38,-33,.16,.055,65,color(.40,.44,.49,1,5));}
lamp(5.8,-25);lamp(8.8,-39);lamp(-10,-21);
// Benches, a coal bin, luggage trolley and small operational details.
for(let z of[-34,-40]){for(let i=0;i<4;i++)box(6.2,.87,z+(i-1.5)*.16,1.8,.07,.13,wood);for(let i=0;i<3;i++)box(6.2,1.1+i*.16,z-.38,1.8,.10,.055,wood);for(let x of[5.55,6.85]){box(x,.57,z,.07,.60,.48,iron);box(x,1.05,z-.38,.07,.90,.07,iron);}}
box(-8.9,.4,-36.4,1.6,.8,2.1,wood);for(let i=0;i<16;i++)sphere(-9.5+rand()*1.2,.8+rand()*.17,-37.2+rand()*1.6,.15,color(.07,.08,.09),data,6,4);
box(6.2,.6,-29,1.2,.1,.9,wood);for(let x of[5.7,6.7])for(let z of[-29.35,-28.65])sphere(x,.35,z,.17,iron,data,8,5,1,1,.35);beam([5.6,.7,-29.4],[5.6,1.3,-29.4],.045,iron);beam([6.8,.7,-29.4],[6.8,1.3,-29.4],.045,iron);box(6.2,1,-29,.75,.75,.60,color(.26,.20,.13));
for(let i=0;i<6;i++){let x=-18+i*1.1;box(x,.7,-19,.09,1.4,.1,wood);if(i<5){box(x+.55,.6,-19,1.1,.08,.06,wood);box(x+.55,1.05,-19,1.1,.08,.06,wood);}}
// Modest silhouettes of deciduous trees, not a threatening forest.
function tree(x,z,s){
 cylinder(x,terrainHeight(x,z)-.1,z,.13*s,3.5*s,wood,14,data,.045*s);
 for(let j=0;j<7;j++){let a=j*2.39,rr=(.55+rand()*.7)*s,xx=x+Math.cos(a)*rr,zz=z+Math.sin(a)*rr,yy=(3.1+rand()*1.65)*s;beam([x,2.1*s,z],[xx,yy,zz],.045*s,wood);sphere(xx,yy,zz,(.65+rand()*.28)*s,color(.11,.18,.15,1,6),data,18,12,1,.95,1.1);}
}
for(let t of[[-19,-13,1.3],[-22,-35,1.6],[20,-20,1.4],[23,-48,1.8],[-28,-56,1.6],[-7,11,1.2],[14,8,1.1],[-35,-22,1.7],[35,-65,1.5]])tree(...t);
for(let i=0;i<850;i++){let x=(rand()-.5)*75,z=15-rand()*90;if((x>-9&&x<14&&z<-19&&z>-65)||(x>-18&&x<-10&&z>-28&&z<-19)||Math.abs(x-Math.sin((9-z)/1.75*.16)*2.1)<1.6)continue;let h=.12+rand()*.25,y=terrainHeight(x,z)-.08;tri([x-.05,y,z],[x+.04,y,z],[x+.03,y+h,z],color(.16,.24,.19));tri([x,y,z-.06],[x,y,z+.06],[x,y+h*.8,z+.01],color(.15,.23,.20));}
// Moon and faint stars at fixed world positions.
sphere(-52,51,-110,4.1,color(.96,.98,1,2,8),data,48,28);for(let i=0;i<130;i++){let x=(rand()-.5)*270,y=27+rand()*100,z=-120-rand()*50;let s=.025+rand()*.055;quad([x-s,y-s,z],[x+s,y-s,z],[x+s,y+s,z],[x-s,y+s,z],color(.32,.39,.48,2));}
const staticBuffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,staticBuffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.STATIC_DRAW);
const dynamicBuffer=gl.createBuffer(), smokeBuffer=gl.createBuffer();const staticCount=data.length/11,staticBytes=data.length*4;data.length=0;
function bind(buffer){gl.bindBuffer(gl.ARRAY_BUFFER,buffer);for(let i=0;i<4;i++){gl.enableVertexAttribArray(attrs[i]);gl.vertexAttribPointer(attrs[i],i===2?4:i===3?1:3,gl.FLOAT,false,44,[0,12,24,40][i]);}}
// Original textures are packaged locally. Neutral fallbacks keep rendering viable if an asset fails.
function loadTexture(path,fallback){const texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array(fallback));gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);const img=new Image();img.onload=()=>{gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img);gl.generateMipmap(gl.TEXTURE_2D);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);};img.onerror=()=>console.warn('Texture fallback:',path);img.src=path;return texture;}
const atlasTex=loadTexture('assets/materials.jpg',[210,210,210,255]),moonTex=loadTexture('assets/moon.jpg',[235,241,249,255]);
const signProgram=program(`attribute vec3 aPosition;attribute vec2 aUV;uniform mat4 uVP;varying vec2 vUV;void main(){vUV=aUV;gl_Position=uVP*vec4(aPosition,1.0);}`,`precision mediump float;varying vec2 vUV;uniform sampler2D uTexture;void main(){gl_FragColor=vec4(texture2D(uTexture,vUV).rgb*0.48,1.0);}`);
const signTex=loadTexture('assets/station-sign.png',[22,38,42,255]);
const signBuffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,signBuffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1.86,3.54,-28.64,0,1,1.06,3.54,-28.64,1,1,1.06,3.96,-28.64,1,0,-1.86,3.54,-28.64,0,1,1.06,3.96,-28.64,1,0,-1.86,3.96,-28.64,0,0]),gl.STATIC_DRAW);
// Sky gradient behind all geometry; stars and moon remain actual world geometry.
const skyProgram=program(`attribute vec2 a;varying vec2 uv;void main(){uv=a;gl_Position=vec4(a,0.9999,1.0);}`,`precision mediump float;varying vec2 uv;uniform float pitch;void main(){float t=clamp((uv.y+pitch+0.7)*0.5,0.0,1.0);gl_FragColor=vec4(mix(vec3(0.033,0.056,0.095),vec3(0.008,0.019,0.044),t),1.0);}`);
const skyBuffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,skyBuffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);
function multiply(a,b){let out=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)out[c*4+r]+=a[k*4+r]*b[c*4+k];return out;}
function perspective(fov,aspect,near,far){let f=1/Math.tan(fov/2);return new Float32Array([f/aspect,0,0,0,0,f,0,0,0,0,(far+near)/(near-far),-1,0,0,2*far*near/(near-far),0]);}
function view(eye,forward){let z=norm(forward.map(v=>-v)),x=norm(cross([0,1,0],z)),y=cross(z,x);return new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-x.reduce((s,v,i)=>s+v*eye[i],0),-y.reduce((s,v,i)=>s+v*eye[i],0),-z.reduce((s,v,i)=>s+v*eye[i],0),1]);}
let eye=[0,1.7,4],yaw=0,pitch=.035,started=false,low=false,paused=false,frame=0,fps=0,frameTime=0,frames=0,move=[0,0],lookPointer=null,joyPointer=null,lastLook=[0,0],last=0;const keys=new Set();const collisions=[[-2.05,1.25,-30.4,-28.58],[-8.4,4.4,-36.9,-29.15],[-17.2,-10.8,-26.8,-21.15],[-9.8,-8,-37.6,-35.2]];
function blocked(x,z){return collisions.some(([a,b,c,d])=>x>a-.22&&x<b+.22&&z>c-.22&&z<d+.22);}
function groundEye(x,z){if(x>=4.85&&x<=8.55&&z<=-19&&z>=-46)return 1.7+(z>-22?(-19-z)/3*.46:.46);let pathX=Math.sin((9-z)/1.75*.16)*2.1;return Math.abs(x-pathX)<1.3&&z>-29?1.7:1.7+terrainHeight(x,z)-.13;}
function reset(){eye=[0,1.7,4];yaw=0;pitch=.035;keys.clear();move=[0,0];$('stick').style.transform='';}
function resize(){let scale=Math.min(window.devicePixelRatio||1,low?1:1.5);let w=Math.round(innerWidth*scale),h=Math.round(innerHeight*scale);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;gl.viewport(0,0,w,h);}}
function clearInputs(){keys.clear();move=[0,0];joyPointer=null;lookPointer=null;$('stick').style.transform='';}
$('enter').onclick=()=>{started=true;$('intro').classList.add('hidden');};$('home').onclick=reset;
$('quality').onclick=()=>{low=!low;$('quality').textContent='画质 '+(low?'省电':'标准');resize();};$('help').onclick=()=>{clearInputs();paused=true;$('helpPanel').classList.remove('hidden');};$('closeHelp').onclick=()=>{paused=false;$('helpPanel').classList.add('hidden');};
window.addEventListener('keydown',e=>{if(['KeyW','KeyA','KeyS','KeyD','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.code)){keys.add(e.code);e.preventDefault();}});window.addEventListener('keyup',e=>keys.delete(e.code));window.addEventListener('blur',clearInputs);document.addEventListener('visibilitychange',()=>{clearInputs();if(audioCtx)document.hidden?audioCtx.suspend():soundOn&&audioCtx.resume();});
const joy=$('joystick');function joystick(e){let r=joy.getBoundingClientRect(),dx=e.clientX-r.left-r.width/2,dy=e.clientY-r.top-r.height/2,l=Math.hypot(dx,dy),max=30;if(l>max){dx*=max/l;dy*=max/l;}move=[dx/max,dy/max];$('stick').style.transform=`translate(${dx}px,${dy}px)`;}
joy.addEventListener('pointerdown',e=>{if(joyPointer!==null)return;joyPointer=e.pointerId;joy.setPointerCapture(e.pointerId);joystick(e);e.preventDefault();});joy.addEventListener('pointermove',e=>{if(e.pointerId===joyPointer)joystick(e);});function joyEnd(e){if(e.pointerId===joyPointer){joyPointer=null;move=[0,0];$('stick').style.transform='';}}joy.addEventListener('pointerup',joyEnd);joy.addEventListener('pointercancel',joyEnd);
canvas.addEventListener('pointerdown',e=>{if(!started||paused||lookPointer!==null)return;lookPointer=e.pointerId;lastLook=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);});canvas.addEventListener('pointermove',e=>{if(e.pointerId!==lookPointer)return;yaw+=(e.clientX-lastLook[0])*.004;pitch=Math.max(-1.1,Math.min(1.15,pitch-(e.clientY-lastLook[1])*.004));lastLook=[e.clientX,e.clientY];});function lookEnd(e){if(e.pointerId===lookPointer)lookPointer=null;}canvas.addEventListener('pointerup',lookEnd);canvas.addEventListener('pointercancel',lookEnd);
let audioCtx=null,soundOn=false,audioMaster=null,industrial=null;function setupAudio(){audioCtx=new(window.AudioContext||window.webkitAudioContext)();audioMaster=audioCtx.createGain();audioMaster.gain.value=.0;audioMaster.connect(audioCtx.destination);let noise=audioCtx.createBuffer(1,audioCtx.sampleRate*4,audioCtx.sampleRate),v=noise.getChannelData(0),prev=0;for(let i=0;i<v.length;i++){prev=(prev+(rand()-.5)*.04)/1.015;v[i]=prev;}let source=audioCtx.createBufferSource();source.buffer=noise;source.loop=true;let filter=audioCtx.createBiquadFilter();filter.type='lowpass';filter.frequency.value=650;let wind=audioCtx.createGain();wind.gain.value=.26;source.connect(filter).connect(wind).connect(audioMaster);source.start();industrial=audioCtx.createGain();industrial.gain.value=.018;industrial.connect(audioMaster);for(let f of[52,104,157]){let o=audioCtx.createOscillator();o.frequency.value=f;let g=audioCtx.createGain();g.gain.value=f===52?.5:.1;o.connect(g).connect(industrial);o.start();}let insect=audioCtx.createOscillator(),ig=audioCtx.createGain();insect.frequency.value=3100;ig.gain.value=.003;insect.connect(ig).connect(audioMaster);let lfo=audioCtx.createOscillator(),lg=audioCtx.createGain();lfo.frequency.value=5.7;lg.gain.value=.002;lfo.connect(lg).connect(ig.gain);insect.start();lfo.start();}
$('sound').onclick=async()=>{try{if(!audioCtx)setupAudio();await audioCtx.resume();soundOn=!soundOn;audioMaster.gain.setTargetAtTime(soundOn?.45:0,audioCtx.currentTime,.4);$('sound').textContent='声音 '+(soundOn?'开':'关');}catch(e){$('sound').textContent='声音不可用';}};
canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();fail('图形上下文已中断。请重新加载；也可以关闭其他占用显存的页面后重试。');});
const poolData=new Float32Array(dynamic);dynamic.length=0;gl.bindBuffer(gl.ARRAY_BUFFER,dynamicBuffer);gl.bufferData(gl.ARRAY_BUFFER,poolData,gl.STATIC_DRAW);
function draw(now){if(gl.isContextLost())return;resize();let t=now*.001,elapsed=last?Math.max(0,t-last):.016,dt=Math.min(.05,elapsed);last=t;if(started&&!paused&&!document.hidden){let side=move[0]+(keys.has('KeyD')||keys.has('ArrowRight')?1:0)-(keys.has('KeyA')||keys.has('ArrowLeft')?1:0),front=-move[1]+(keys.has('KeyW')||keys.has('ArrowUp')?1:0)-(keys.has('KeyS')||keys.has('ArrowDown')?1:0),len=Math.max(1,Math.hypot(side,front));side/=len;front/=len;let dx=(Math.cos(yaw)*side+Math.sin(yaw)*front)*dt*2.7,dz=(Math.sin(yaw)*side-Math.cos(yaw)*front)*dt*2.7;let x=Math.max(-40,Math.min(40,eye[0]+dx)),z=Math.max(-68,Math.min(22,eye[2]+dz));if(!blocked(x,eye[2]))eye[0]=x;if(!blocked(eye[0],z))eye[2]=z;eye[1]+=(groundEye(eye[0],eye[2])-eye[1])*Math.min(1,dt*10);}
let forward=[Math.sin(yaw)*Math.cos(pitch),Math.sin(pitch),-Math.cos(yaw)*Math.cos(pitch)],vp=multiply(perspective(Math.PI/3,canvas.width/canvas.height,.08,350),view(eye,forward));
gl.clearColor(.03,.05,.09,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.disable(gl.DEPTH_TEST);gl.disable(gl.BLEND);gl.useProgram(skyProgram);gl.bindBuffer(gl.ARRAY_BUFFER,skyBuffer);let sa=gl.getAttribLocation(skyProgram,'a');gl.enableVertexAttribArray(sa);gl.vertexAttribPointer(sa,2,gl.FLOAT,false,8,0);gl.uniform1f(gl.getUniformLocation(skyProgram,'pitch'),pitch);gl.drawArrays(gl.TRIANGLES,0,6);gl.disableVertexAttribArray(sa);
gl.enable(gl.DEPTH_TEST);gl.useProgram(p);gl.uniformMatrix4fv(uniforms.uVP,false,vp);gl.uniform3fv(uniforms.uEye,eye);gl.uniform3fv(uniforms['uLights[0]'],lights.flat());gl.uniform1f(uniforms.uTime,t);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,atlasTex);gl.uniform1i(uniforms.uAtlas,0);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,moonTex);gl.uniform1i(uniforms.uMoon,1);bind(staticBuffer);gl.drawArrays(gl.TRIANGLES,0,staticCount);
// Pools have alpha, but don't block other geometry.
gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false);bind(dynamicBuffer);gl.drawArrays(gl.TRIANGLES,0,poolData.length/11);
const smoke=[];let puffs=[];for(let source of[[-5.3,7.06,-33.3],[-15.8,5.48,-25.1]])for(let i=0;i<(low?6:10);i++){let age=(t*.22+i/10)%1;let x=source[0]+age*3.9,y=source[1]+age*6,z=source[2]+Math.sin(age*4+t*.05)*.45,r=.22+age*.95;puffs.push({x,y,z,r,alpha:.12*(1-age),dist:Math.hypot(x-eye[0],y-eye[1],z-eye[2])});}puffs.sort((a,b)=>b.dist-a.dist);let right=[Math.cos(yaw),0,Math.sin(yaw)],up=norm(cross(right,forward));for(let s of puffs){let center=[s.x,s.y,s.z],nn=forward.map(v=>-v),vertex=(v,a)=>smoke.push(...v,...nn,.28,.30,.34,a,11);for(let i=0;i<14;i++){let aa=i*Math.PI*2/14,bb=(i+1)*Math.PI*2/14;let edge=a=>center.map((v,k)=>v+s.r*(right[k]*Math.cos(a)+up[k]*Math.sin(a)*1.35));vertex(center,s.alpha*.8);vertex(edge(aa),0);vertex(edge(bb),0);}}gl.bindBuffer(gl.ARRAY_BUFFER,smokeBuffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(smoke),gl.DYNAMIC_DRAW);bind(smokeBuffer);gl.drawArrays(gl.TRIANGLES,0,smoke.length/11);gl.depthMask(true);gl.disable(gl.BLEND);
for(let a of attrs)gl.disableVertexAttribArray(a);gl.useProgram(signProgram);gl.uniformMatrix4fv(gl.getUniformLocation(signProgram,'uVP'),false,vp);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,signTex);gl.uniform1i(gl.getUniformLocation(signProgram,'uTexture'),0);gl.bindBuffer(gl.ARRAY_BUFFER,signBuffer);let sp=gl.getAttribLocation(signProgram,'aPosition'),su=gl.getAttribLocation(signProgram,'aUV');gl.enableVertexAttribArray(sp);gl.enableVertexAttribArray(su);gl.vertexAttribPointer(sp,3,gl.FLOAT,false,20,0);gl.vertexAttribPointer(su,2,gl.FLOAT,false,20,12);gl.drawArrays(gl.TRIANGLES,0,6);gl.disableVertexAttribArray(sp);gl.disableVertexAttribArray(su);
if(industrial&&soundOn){let d=Math.hypot(eye[0]+2,eye[2]+33);industrial.gain.setTargetAtTime(.012+.035/(1+d*.07),audioCtx.currentTime,.3);}
frames++;frameTime+=elapsed;if(frameTime>=1){fps=Math.round(frames/frameTime);frames=0;frameTime=0;$('perf').textContent=fps+' FPS · '+(low?'省电':'标准');let d=Math.hypot(eye[0]+.4,eye[2]+29);$('landmark').textContent=d>12?'沿着灯光，慢慢走。':eye[0]>4?'月台安静，铁轨仍通向远方。':'灯还亮着，夜还很长。';}
frame=requestAnimationFrame(draw);
}
window.addEventListener('resize',resize);resize();requestAnimationFrame(draw);
window.MoonlitHalt={version:'2.0',staticVertices:staticCount,reset,getState:()=>({eye:[...eye],yaw,pitch,fps,low,started}),geometrySummary:()=>({triangles:staticCount/3,bytes:staticBytes,pointLights:lights.length,localTextures:3,externalAssets:0})};
})();
