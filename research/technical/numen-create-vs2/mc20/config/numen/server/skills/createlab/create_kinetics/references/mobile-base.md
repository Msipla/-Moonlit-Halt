# Small mobile bases with Numen Engineering

When the brief gives the site and deck height, use the constructor's candidate check to find any occupied cells. For an empty pool with an unknown waterline, sample one short vertical column at your chosen site and continue to construction. Read additional positions when a specific decision depends on them. Keep the Lua short with loops and execute the design in the same tool call.

```lua
local cells, byPos = {}, {}
local function put(x, y, z, state)
  local key = x .. "," .. y .. "," .. z
  local i = byPos[key] or (#cells + 1)
  byPos[key] = i
  cells[i] = {pos={x=x,y=y,z=z}, state=state}
end
-- Generate your layout and choose feetPath and motorToMill before this call.
local checks = {walk=feetPath, drive=motorToMill}
local r = my.create_survey.construct(cells, checks)
print(r.stage, r.batches, r.confirmed, r.written, r.next, r.through)
if r.error then print(r.error) end
for _, reject in ipairs(r.rejects or {}) do print(reject.pos, reject.reason) end
```

## Pick a route and build in passes

1. Use the supplied site and terrain facts. If a fact changes the layout, read it once. For site info, print the specific fields or palette entries you need. Printing the whole table can abbreviate the palette. Choose a heading, short boarding route and useful bays. If the deck is at Y, reserve Y+1 for feet and Y+2 for head along the route. Start the roof at Y+3 or higher. Put helm, anchor, stowage and the mill column beside that reservation.
2. Commit to a footprint and side profile. The yacht's usable main body is narrow under its tall rig. Use one narrower bow or transom, a low cabin volume and a side glazing band; preserve the useful route when changing width. A short three-station sequence of entry, control and rest/work is sufficient for the first fit-out. Leave decoration for after the tests.
3. For a vessel plan or repair, pass 2–32 consecutive feet positions from the entrance through the cabin and the face-adjacent motor, optional shafts, and mill positions in order. `construct(cells, checks)` reuses the native route and drive checks before writing and reads each batch back. For an incomplete phase or cabin-only exercise, use `construct(cells)`.
4. Before assembly, run `my.create_survey.check_vessel(checks)` on the built geometry. It requires the complete walk and live mill RPM. Then count real lift/flotation and assemble one connected companion-built hull with one helm and one anchor, within `info.hull_limit`. Verify onboard mill power, float or hover, forward, turn and anchor. Report the measured values.

### Make the first useful batch immediately

For a deck block at Y, the walking cells are at Y+1 and Y+2. The first roof block over that route can be at Y+3. Put the helm and barrel on top of the deck, in side bays. Lua loops count up by default. Use a step of `-1` for descending bounds. Specify the shaft axis or faces for drive connections that cross batches.

- `stage="complete"` confirms the listed edits. Continue with the required route or machine test. Use `engineering.site.plan({walk=feetPositions})` to check the built route once.
- A `plan` rejection can identify duplicate design coordinates before writing. A `check` rejection gives the coordinate and reason. Keep the original design in the Lua call so you can use the `confirmed` prefix. Correct the relevant edit and submit only the remaining suffix starting at `next`. `through` is the last cell of the failed batch.
- After an `apply` or `readback` failure, cancellation or missing receipt, stop and use `engineering.site.inspect_many(positions)` on groups of up to 16 affected coordinates. Reconcile the actual state, permissions and any reported backup before submitting outstanding edits. Preserve completed batches and protected blocks.
- If the owner module is unavailable, read `engineering.site.info().batch_limit` and use the positional forms `engineering.site.check(batch)` and `engineering.site.apply(batch)` on each legal batch. Read back before advancing. Use `print(numen.api.help("engineering.site.plan"))` only for a missing signature.

## Blueprint lessons

The 1.20.1 yacht has a fully connected 626-cell body and a narrow hull below a tall mast. Preserve the deck and its entrance before adding the tall profile. The airship's largest connected component is 1,490 of 2,141 cells. Place all lift hardware in physical contact with the hull and size it for the final mass. The research submarine's connected hull is 417 cells after excluding terrain and separating disconnected shapes. Leave scenery, stands and stray parts outside the assembly.

Place a creative motor with `facing=up` directly below the millstone. A hopper facing down takes items from the input barrel one cell above it. Put the column beside the aisle and give the input a reachable work surface. For this layout, collect output from the millstone inventory. The warning about a blocked bottom face applies to downward extraction. Check every required transmission part against `engineering.site.info().hull` before assembly. In 0.2.7, a side cogwheel can run on shore, then stay behind at assembly and leave the onboard millstone at 0 RPM. Read RPM and inventory again with `engineering_vessels.ship.inspect_onboard` before driving and after motion.

## Choose the carrier

- **Air.** Build the floor in empty space above the ground. Leave several blocks below the floor and above the balloons for stabilization, and leave side space for turns. Count both ordinary blocks and connected balloons before assembling. A 204-cell wooden workshop with 44 connected balloons hovered in this setup. A similar 184-cell cabin with 12 balloons sank. For a similarly light hull, start near one balloon per three to four other blocks. Spread them across the roof and count the final lift hardware after edits. This is an empirical starting point for these mod settings. Read the actual lift with `engineering_vessels.ship.inspect()` on the turn after assembly, then decide whether to drive.
- **Surface.** Start the floaters on both sides in the first air layer above the sampled water surface. Build and assemble there, then let the ship settle while afloat. Engineering 0.2.7 rejects water as a site-edit state, including as `expected`. Keep the drive in air above that surface. Leave a flat deck from the entry to the interior, and space to turn without clipping the shore. Verify afloat, forward, turn and moor in that order.
- **Road.** Allow chassis and steering clearance under the floor, with door and interior access above it. Identify an installed compatible driven chassis before attempting powered travel.
- **Submerged.** Group the boarding hatch and vertical access together. Keep space for control, living and ballast apart. Verify depth hold, resurfacing and the dry interior independently before calling the result habitable.

## Read the result

Print `engineering_vessels.ship.inspect()` as a JSON string before and after a short drive. Compare position, rotation and static state on the next turn. `drive(heading,forward,up,left,ticks)` uses the fourth argument for hull yaw. Changing heading alone can move the hull sideways. Verify a measurable change in the rotation quaternion before reporting a turn. When available, use `engineering_vessels.ship.inspect_onboard(dx,dy,dz)` with the machine's offset from the helm. A pilot leaving an engineering pulse triggers a stop. For a human voyage, use `handover()` after anchoring, with owner approval. The owner seats at the native helm and can engage Eureka cruise, dismount and retake the helm. The site edge and owner disconnect still stop the voyage. Use actual game observations to verify both the walk and the vessel displacement. Verify first and repeated input and output while anchored and while moving.
