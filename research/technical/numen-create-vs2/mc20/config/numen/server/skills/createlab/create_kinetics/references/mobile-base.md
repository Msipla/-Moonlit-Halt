# Small mobile bases with Numen Engineering

## Pick a route and build in passes

1. Read site info once; use one local read only if it resolves ground, water level or an obstruction. Choose a heading, short boarding route and useful bays. If the deck is at Y, reserve Y+1 for feet and Y+2 for head along the route. Start the roof at Y+3 or higher. Put helm, anchor, stowage and the mill column beside that reservation. Give the doorway a threshold and fit the furniture to the remaining space.
2. Commit to a footprint and side profile. The yacht's usable main body is narrow under its tall rig. Use one narrower bow or transom, a low cabin volume and a side glazing band; preserve the useful route when changing width. A short three-station sequence of entry, control and rest/work is sufficient for the first fit-out. Leave decoration for after the tests.
3. Generate a coordinate-keyed plan once. Put one state at each coordinate, omit reserved air from writes, and split the plan into batches of at most `info.batch_limit`. Read only failed cells, repair that batch, then proceed. Preview the first relevant batch with `site.plan({cells=cells,walk=walk})`; the walk is feet positions. Check and apply each subsequent batch, and read the placed route once with `site.plan({walk=walk})`. An already accepted batch is finished.
4. Stop when the drill's required features pass. For a vessel, assemble one connected companion-built hull, using `info.hull`, one helm and one anchor. Check `hull_limit` independently of the batch limit. Before assembly, count real connected lift/flotation, confirm the route stays clear and test the smallest allowed drive train. After assembly finish afloat or hovering, short forward, turn and anchor, then report the measured values. Once these pass, stop surveying.

### Make the first useful batch immediately

When the brief supplies an empty work area and deck height, the candidate check is the survey. Each entry is `{pos={x=x,y=y,z=z},state=block_id}`. In Lua call `local s=engineering.site; local p=s.check(cells); print(#cells,#p.rejects); if #p.rejects==0 then print(s.apply(cells).cells) end`. Add the helm, usable stowage and roof ahead of ornamental walls. Lua loops count up by default; sort the bounds or supply `-1` when intentionally descending. Print the batch size to catch an empty roof or wall loop.

## Blueprint lessons

The 1.20.1 yacht has a fully connected 626-cell body and a narrow hull below a tall mast. Preserve the deck and its entrance before adding the tall profile. The airship's largest connected component is 1,490 of 2,141 cells. Place all lift hardware in physical contact with the hull and size it for the final mass. The research submarine's connected hull is 417 cells after excluding terrain and separating disconnected shapes. Leave scenery, stands and stray parts outside the assembly.

Place an upward-facing creative motor directly below a millstone to form a simple workshop column. A hopper facing down takes items from the input barrel one cell above it. Put the column beside the aisle and give the input a reachable work surface. Use the direct vertical drive when output is removed from the mill inventory. Keep optional side gears and separate output lines out of the first functional pass. Read the moving machine’s RPM and inventories separately.

## Choose the carrier

- **Air.** Build the floor in empty space above the ground. Leave several blocks below the floor and above the balloons for stabilization, and leave side space for turns. Count both ordinary blocks and connected balloons before assembling. A 204-cell wooden workshop with 44 connected balloons hovered in this setup. A similar 184-cell cabin with 12 balloons sank. For a similarly light hull, start near one balloon per three to four other blocks. Spread them across the roof and count the final lift hardware after edits. This is an empirical starting point for these mod settings. Read the actual lift with `engineering_vessels.ship.inspect()` on the turn after assembly, then decide whether to drive.
- **Surface.** Place flotation on both sides and keep the entry above the intended waterline. Leave a flat deck from the entry to the interior and space to turn without clipping the shore. Verify afloat, forward, turn and moor in that order.
- **Road.** Allow chassis and steering clearance under the floor, with door and interior access above it. Identify an installed compatible driven chassis before attempting powered travel.
- **Submerged.** Group the boarding hatch and vertical access together. Keep space for control, living and ballast apart. Verify depth hold, resurfacing and the dry interior independently before calling the result habitable.

## Read the result

Print `engineering_vessels.ship.inspect()` as a JSON string before and after a short drive. Compare position, rotation and static state on the next turn. `drive(heading,forward,up,left,ticks)` uses the fourth argument for hull yaw. Changing heading alone can move the hull sideways. Verify a measurable change in the rotation quaternion before reporting a turn. When available, use `engineering_vessels.ship.inspect_onboard(dx,dy,dz)` with the machine's offset from the helm. A pilot leaving an engineering pulse triggers a stop. For a human voyage, use `handover()` after anchoring, with owner approval. The owner seats at the native helm and can engage Eureka cruise, dismount and retake the helm. The site edge and owner disconnect still stop the voyage. Use actual game observations to verify both the walk and the vessel displacement. Verify first and repeated input and output while anchored and while moving.
