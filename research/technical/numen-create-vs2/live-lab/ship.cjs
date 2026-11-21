const assert=require('node:assert/strict');
const {setTimeout:wait}=require('node:timers/promises');
const hull=['minecraft:oak_planks','minecraft:oak_fence','vs_eureka:oak_ship_helm','vs_eureka:anchor',
  'vs_eureka:balloon','vs_eureka:white_balloon','vs_eureka:floater'];
const target={
  goal:'从空场自主建造一台可控制、能停下的 Eureka 小型飞行载具。用真正的 VS 船体装配和控制，读取船 ID、位置、转向变换、位移和停止数据后回报。',
  ship:true,
};
const instructions=[
  'Use one JSON action per reply. Build uses {"action":"build","blocks":[{"at":[x,y,z],"state":"namespace:block[prop=value]"}]}. x,z are -7..7 and local y is 0..7. In this empty flat server you design a small connected Eureka airship. Start above the ground with enough balloons for lift, a correctly facing oak_ship_helm, and a connected anchor for stopping.',
  'Every block must be from the airship palette: '+hull.join(', ')+'. Use at most 64 hull blocks and only one helm. Each block needs one state. The floor is y=0; use clearance so the airship can lift.',
  'After building call {"action":"assemble","at":[helm x,y,z]}. It uses Eureka native assembly.',
  'Then call {"action":"drive","heading":"north|east|south|west","forward":1,"up":1} with forward/up between -1 and 1. The bounded pulse is 2.5 seconds. An airship can use balloons for upward thrust and move by the helm.',
  'Call {"action":"stop"} to engage the fitted anchor and measure actual settling. Use {"action":"observe"} for a fresh position/velocity/rotation sample.',
  'When inspection shows controlled forward travel and stopped position, submit {"action":"finish","summary":"measured results"}.',
].join('\n');
const delta=(a,b)=>Math.hypot(a.p[0]-b.p[0],a.p[2]-b.p[2]);
const sample=async command=>JSON.parse((await command('engineering_ship sample')).trim());

async function act(a,lab) {
  const c=lab.command;
  switch(a.action){
    case 'assemble': {
      const at=lab.position(a.at);
      const state=await lab.save('static-hull');
      const parts=Object.values(state.blocks).filter(b=>hull.includes(b.Name));
      assert(parts.length<=64,'hull exceeds 64 blocks');
      assert(parts.filter(b=>b.Name==='vs_eureka:oak_ship_helm').length===1,'exactly one helm required');
      const answer=JSON.parse(await c('engineering_ship assemble '+at.join(' ')));
      if (answer.error) return answer;
      await wait(480);
      return {assembly:answer,ship:await sample(c)};
    }
    case 'observe': return sample(c);
    case 'drive': {
      assert(['north','east','south','west'].includes(a.heading),'invalid heading');
      assert([a.forward,a.up].every(x=>typeof x==='number'&&Number.isFinite(x)&&Math.abs(x)<=1),'control outside [-1,1]');
      const before=await sample(c);
      if(before.error) return before;
      const start=await c(`engineering_ship steer ${a.heading} ${a.forward} ${a.up}`);
      const control=JSON.parse(start);
      if(control.error) return control;
      await wait(2500);
      const after=await sample(c);
      if (after.error) return after;
      // Release commanded impulses before the next model request. Stop validation is a separate measurement.
      await c(`engineering_ship steer ${a.heading} 0 0`);
      const horizontal=delta(before,after);
      const turned=after.p[1]-before.p[1];
      const idx=['north','south'].includes(a.heading)?2:0;
      const direction=['north','west'].includes(a.heading)?-1:1;
      const along=(after.p[idx]-before.p[idx])*direction*Math.sign(a.forward);
      return {before,after,horizontal,vertical:turned,along,passed:horizontal>0.75&&along>0.75&&Math.abs(a.forward)>0};
    }
    case 'stop': {
      const anchor=JSON.parse(await c('engineering_ship stop'));
      if(anchor.error) return anchor;
      await wait(850);
      const before=await sample(c);
      await wait(1000);
      const after=await sample(c);
      if(before.error||after.error) return {anchor,before,after,passed:false};
      return {before,after,passed:after.static&&delta(before,after)<0.10&&Math.abs(before.p[1]-after.p[1])<0.10};
    }
    default: throw new assert.AssertionError({message:'unknown ship action'});
  }
}

async function independent(lab,drive) {
  const launched=await act(drive,lab);
  const stopped=await act({action:'stop'},lab);
  return {launched,stopped,passed:Boolean(launched.passed&&stopped.passed&&launched.after.id===stopped.after.id)};
}
module.exports={target,hull,instructions,act,independent};
