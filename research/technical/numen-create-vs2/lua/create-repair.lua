local i=createlab.machine.inspect
local a=i({x=10,y=-57,z=2}).kinetics
local b=i({x=8,y=-57,z=2}).kinetics
local s=numen.scan.block({x=9,y=-57,z=2})
if a.rpm~=0 and b.rpm==0 and s.name=="create:shaft" and s.properties.axis~="x" then
    numen.build.place({{name="minecraft:air",pos=s.pos}})
    assert(numen.scan.block(s.pos).is_air)
    return numen.build.place({{name="create:shaft[axis=x]",pos=s.pos}})
end
error("unexpected diagnosis")
