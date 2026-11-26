# Stationary Create engineering

Use `local s=engineering.site`. Read `s.info()` for a new site or `s.overview()` for repair. Build in small direct batches, beginning with a functional segment. The companion can stay beside the owner. Use engine reads and writes for these tasks. Send only the positions and brief outputs you need.

## Fan washing

Choose a horizontal facing and a fan cell inside the site, at least two blocks above walking height. Along that direction use motor → fan → water source → air above depot → glass. Below the source put glass and below the next cell put the depot. Glass borders both open cells on both sides. The motor faces the fan and the fan faces the source. The lane has one source and one flowing cell. Keep both above-floor cells and all walls inside the site.

Here is the relative unit. Choose your own `F` and `facing` before running it. Use its lane without the motor if you already have a powered output facing the back of the fan.

```lua
local d=({east={1,0},west={-1,0},south={0,1},north={0,-1}})[facing]
local function p(k,y,t)
  return {x=F.x+k*d[1]+t*d[2],y=F.y+y,z=F.z+k*d[2]-t*d[1]}
end
local function c(k,y,t,state,rpm)
  return {pos=p(k,y,t),state=state,rpm=rpm}
end
local glass="minecraft:glass"
local r=s.apply({
  c(-1,0,0,"create:creative_motor[facing="..facing.."]",64),
  c(0,0,0,"create:encased_fan[facing="..facing.."]"),
  c(1,-1,0,glass),c(2,-1,0,"create:depot"),c(3,0,0,glass),
  c(1,0,-1,glass),c(1,0,1,glass),c(2,0,-1,glass),c(2,0,1,glass)
})
print(r.cells,r.power)
print(s.water(p(1,0,0),p(2,0,0)).state)
```

Read `s.air(fan,depot)` after the source settles, then check actual flour-to-dough conversion. Carry a water bucket for the direct pour. With an empty bucket, `s.drain_water(source)` removes the source. Wait for flow to recede before changing walls.

## Transfers and repairs

- Leave a full block gap below a source barrel or millstone for extraction. A barrel directly on a millstone does not feed. Use `s.flow({links={{from=input,to=machine,item=raw}},cells=planned})`. Require `candidate` or `existing` before building the next stage. Use `s.connect` for chosen drive endpoints and pass its returned cells on directly, without retyping their orientation. Keep gravity cells out of the drive route with `avoid`.
- When the outlet must stay open, mesh the millstone via a same-height Y-axis side cog with vertical drive. Read both RPM and actual material transfer. If the recipe is unknown, print `s.recipe("create:milling",raw)`, or use `create:splashing` for washing.
- For an existing line, inspect the first outlet that should pass the missing intermediate. If its smart-chute filter differs, use `s.apply({{pos=P,state=seen.state,expected=seen.state,filter=desired_id}})`. Check the returned `configured` filter and resume production. A settings-only edit keeps `cells=0`. Verify actual output and report any remaining fault.
