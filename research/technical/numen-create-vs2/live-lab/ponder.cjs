const {execFileSync} = require('node:child_process');

// Short observations from manually reviewed Create Fabric scene scripts, not a scene layout.
const scenes = {
  millstone: ['millstone', 'ProcessingScenes.millstone',
    'A side small cog can drive the millstone. Input goes in the top; output can be collected below. The script sets speed and inventories for animation.'],
  fan: ['fan/processing', 'FanScenes.processing',
    'The air stream passes through a treatment block before reaching the item. The scene shows water for washing, fire for smoking, lava for blasting, and soul fire for haunting. Animation changes the shown items; verify the real machine and recipe.'],
  chute: ['chute/smart', 'ChuteScenes.smart',
    'Chutes transfer down to a reachable destination below. A smart chute can select an item. A powered chute can stop transfer; check actual filters and inventory.'],
};
const kinds = new Set(['milling','splashing','crushing','haunting','smoking','mixing','pressing']);

function sources(jar) {
  const listing = new Set(execFileSync('unzip',['-Z1',jar],{encoding:'utf8'}).trim().split('\n'));
  function recipe(path) {
    if (typeof path!=='string' || !/^[a-z_]+\/[a-z0-9_]+$/.test(path) || !kinds.has(path.split('/')[0])) return;
    const entry='data/create/recipes/'+path+'.json';
    if (!listing.has(entry)) return;
    return {source:entry,...JSON.parse(execFileSync('unzip',['-p',jar,entry],{encoding:'utf8'}))};
  }
  return {
    recipe,
    query(topic) {
      if (typeof topic!=='string') return {error:'provide a scene name or recipe path'};
      if (Object.hasOwn(scenes,topic)) {
        const [asset,script,notes]=scenes[topic];
        const nbt='assets/create/ponder/'+asset+'.nbt';
        return {topic,script,assets:listing.has(nbt)?[nbt]:[],notes,evidence:'Ponder teaching scene'};
      }
      return recipe(topic) || {error:'unknown topic',scenes:Object.keys(scenes),recipeFormat:'type/name'};
    },
  };
}

module.exports = {sources};
