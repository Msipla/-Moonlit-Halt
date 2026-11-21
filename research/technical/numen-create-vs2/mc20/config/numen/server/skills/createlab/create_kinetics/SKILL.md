---
name: create_kinetics
description: Build, inspect, and repair Create 6.0.8.1 water-powered mills and crushing wheels on Fabric 1.20.1 using real kinetics and recipe outputs.
---

For a watermill workshop or a millstone powered by a water wheel, load `references/watermill.md` with the skill tool before planning. It gives the drive and access rules for a working mill. The crusher workflow below applies to crushing wheels.

## Building or retrofitting machinery

For a local retrofit, read the machine and its floor in one short report. Adapt the part IDs and load type, or supply an observed floor tile. If no tile is supplied, the first solid non-machine block within three cells below the load is a possible floor. Its cluster gives an outline, which may include adjoining space.

Run this Lua once to survey. After you choose a layout, set `cells` to your short list and run it again for the diff only. Use an air entry to check a required clear cell. Review any reported dig before acting.

```lua
local ids = {"create:water_wheel", "create:shaft", "create:gearbox", "create:millstone"}
local loadId, floor, cells = "create:millstone", nil, {}
if #cells > 0 then
  local d = numen.build.diff(cells)
  print("candidate", d.left, "dig", d.dig, "far", d.far,
        "unsupported", d.unheld, "skipped", d.skipped, "liquid", d.liquid)
else
  local parts, load, count = {}, nil, 0
  for _, id in ipairs(ids) do parts[id] = true end
  for _, group in ipairs(numen.scan.blocks(ids, {radius=16})) do
    for _, part in ipairs(group.blocks) do
      local p = part.pos
      if part.name == loadId and not load then load = p end
      if count < 16 then
        local state = numen.scan.block(p).properties or {}
        print(part.name, p.x, p.y, p.z, state.axis or state.facing or "-")
      end
      count = count + 1
    end
  end
  print("parts found", count)
  if not floor and load then
    for depth = 1, 3 do
      local p = {x=load.x, y=load.y-depth, z=load.z}
      local b = numen.scan.block(p)
      if b.is_solid and not parts[b.name] then floor = p; break end
    end
  end
  if floor then
    for _, group in ipairs(numen.scan.blocks(numen.scan.block(floor).name, {radius=16})) do
      local xmin,xmax,zmin,zmax = floor.x,floor.x,floor.z,floor.z
      local anchored = false
      for _, part in ipairs(group.blocks) do
        local p = part.pos
        if p.y == floor.y then
          anchored = anchored or (p.x == floor.x and p.z == floor.z)
          xmin,xmax = math.min(xmin,p.x),math.max(xmax,p.x)
          zmin,zmax = math.min(zmin,p.z),math.max(zmax,p.z)
        end
      end
      if anchored then
        print("floor y / x range / z range", floor.y,xmin,xmax,zmin,zmax)
        break
      end
    end
  end
end
```

- **Stop surveying when you can build.** With the ports and intended clearance checked, execute the local change. Leave the untouched shell and supply out of the scan. Only read a specific missing detail that could change the plan.
- **Finish from the measured result.** Restore incidental construction changes and remove temporary feed hardware. Print the load’s storage directly with `print(numen.scan.container(load).storage)` before and after feeding. The entries are text lines. Compare the load’s own output to establish the new product. Once the required function and restoration are verified, send the measurements in the next reply. Loose old drops and closing the todo list can wait.
- **Act on the receipt.** Read back uncertain placements and any incidental digs or scaffolding. If the same action fails, change the approach or arguments using the returned reason. Move on from a blocked secondary task and resume the main job.

1. **Build only the machine.** Use the existing ground. Choose one compact site with room above and below the intake. Skip leveling, floors, broad scans and spreading water across an area. Use small batches. For an X-axis shaft at `pos={x=…,y=…,z=…}`, use `numen.build.place({{name="create:shaft[axis=x]",pos=pos}})`. Put the axis in the state name. An independent `properties` input is ignored. Verify the placed axis with `numen.scan.block(pos).properties.axis`.
2. **Prove the power source.** Start with one small water wheel. Its block state uses horizontal `facing`, along the axle. For an east/west-facing wheel, put the intended source one block up and one block north or south of its center (same X). For a north/south-facing wheel, use east or west instead (same Z). The falling cell directly below that source must be open beside the rim. Build the solid side walls and a small catch basin before pouring, and keep the axle faces and your approach dry. Read dry RPM with `createlab.machine.inspect(wheel)` (the `createlab` global). In creative use `numen.creative.give("minecraft:water_bucket",{count=1})`, then aim `numen.use.block(anchor,{item="minecraft:water_bucket"})` at a visible solid chute wall face bordering the intended source cell. Stand on the source side of that face so the bucket pours into the adjacent air. Read that air cell back after clicking and require `minecraft:water` with `properties.level` equal to `"0"`. The click receipt may mention only the anchor. When the water has settled, inspect the flowing rim and read `createlab.machine.inspect(wheel).kinetics`. Expect signed ±8 RPM. If enabled in this creative lab, `createlab_water.source.place(source, wheel)` places one exact source into air above the rim. Check `placed`, `reason` and `landed_at`, then wait for falling water and inspect again since `rpm_now` is immediate. For a single-wheel task, stop after verifying the wheel and leave shafts and loads out. If the flow score and RPM are zero, inspect the adjacent flow and adjust one cell at a time.
3. **Prove transmission on each side.** Connect the water wheel's axle face to a shaft and to the crushing wheel's axle face along the same line. The shaft and crushing wheel each use `axis`. If the water wheel turns but the crushing wheel is at zero, read the cells along that branch in order. Look for the first zero-RPM part, wrong axis, missing contact or overstress. Correct one offending cell and reread it. Leave water alone while the source spins. Keep the opposite branch independent and give it opposite flow.
4. **Prove the controller.** The two parallel crushing wheels are two cells apart across their axes. Leave the single middle cell empty so Create can generate the controller. With X axles separated along Z, north +8 and south −8 give downward feed. Inspect both crushing wheels for opposite nonzero effective RPM, then require `createlab.machine.inspect(gap).crusher.valid` and `facing == "down"`. If the gap holds a shaft or another block, clear the obstruction. Keep intake above and outlet below clear for hoppers.
5. **Prove output and clean up.** Feed one `minecraft:gravel` through the intake hopper and check for new `minecraft:sand` in the collection hopper or barrel with `numen.scan.container`. If the owner is feeding, report ready only after the preceding readings pass and wait for the test. Check approach blocks and unintended water, and remove unnecessary changes. Report the failing check if a measured condition is still unmet. A placement receipt confirms placement only.
