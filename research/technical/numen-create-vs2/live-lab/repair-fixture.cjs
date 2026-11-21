// Fixed, explicitly non-model setup for a blind repair test.
// This arrangement is never included in the model's system instructions.
const cells = [
  [[0,4,-5],'create:creative_motor[facing=south]',{rpm:64}],
  [[0,4,-4],'create:shaft[axis=z]'],
  [[0,4,-3],'create:gearbox[axis=x]'],
  [[0,4,-2],'create:encased_fan[facing=south]'],
  [[0,5,-3],'create:gearbox[axis=x]'],
  [[0,5,-2],'create:shaft[axis=z]'],
  [[0,5,-1],'create:shaft[axis=z]'],
  [[0,5,0],'create:gearbox[axis=x]'],
  [[0,6,0],'create:cogwheel[axis=y]'],
  [[1,6,0],'create:millstone'],
  [[2,6,0],'minecraft:hopper[facing=west]'],
  [[2,7,0],'minecraft:barrel'],
  [[1,5,0],'create:smart_chute[powered=false]',{filter:'create:wheat_flour'}],
  [[1,4,0],'create:chute'],
  [[1,3,0],'minecraft:hopper[facing=west]'],
  [[0,3,0],'create:depot'],
  [[0,2,0],'create:smart_chute[powered=false]',{filter:'create:dough'}],
  [[0,1,0],'minecraft:barrel'],
  [[-1,4,-1],'minecraft:glass'],
  [[1,4,-1],'minecraft:glass'],
  [[0,3,-1],'minecraft:glass'],
  [[0,4,-1],'minecraft:water'],
].map(([at,state,options])=>({at,state,...options}));
module.exports={cells,sample:{at:[2,7,0],out:[0,1,0]}};
