# Small watermill workshop · Create Fabric 6.0.8.1

## Design before placing
Choose a level site and a compact workshop with a roof. Reserve the transmission route, dry walkable floor, two blocks of headroom, a door through an exterior wall and an indoor standing position adjacent to the millstone. Leave the cell above the millstone clear for hand feeding. The wall can share the drive plane if it leaves a hole for the shaft. A door, window or roof must never fill a mechanical, water or reserved access cell. Reuse Numen's building design rules for the shell and palette.

## A short transmission that turns upward
A small `create:water_wheel[facing=east]` or west occupies **one center cell**, with its axle on X. It needs no shaft in that same cell. Start the separate `create:shaft[axis=x]` on an adjacent east/west axle face. Keep the shaft run straight and touching, then terminate at `create:gearbox[axis=z]`. That gearbox exposes X and Y faces: put the millstone one cell directly above the gearbox, and its bottom drive face receives the rotation. For a wheel facing north or south, use Z shafts and a gearbox with `axis=x`. The `axis` in a gearbox block-state name is the axis **without** a port. The millstone has a bottom shaft port and can also take power from a small cogwheel at its side. Use the bottom-port path for the first compact build. Reserve the cell above for wheat and one side for empty-handed collection. Do not put a hopper between the gearbox and the millstone.

## Contain the source and prove the run
For an X-axis wheel, put the selected source one cell up and one cell north or south from the wheel center. Swap X and Z for a Z-axis wheel. Keep the falling rim cell below the source open. Enclose all four horizontal neighbors of the source, **including the cell directly above the wheel toward the source**. Line the falling column with solid sides, using the wheel as the inner side at wheel height. If the flow continues lower, enclose it on all four sides and give it a solid floor. Seal the chute and catch area before putting in water. This also keeps the aisle and axle ends dry.

1. Use `numen.build.diff` and `numen.build.place` for dry cells. Read back each drive block's axis or facing before adding water.
2. Read dry RPM. Use the tested one-cell action `createlab_water.source.place(source, wheel)` when exposed. Then wait for flow and inspect the wheel, shaft, gearbox and millstone using `createlab.machine.inspect(pos).kinetics`. All should be driven and below overload. If the source spins but a part is stationary, inspect the first stopped connection and its state before moving water. Keep one source and avoid any water outside the designed chute.
3. Drop `minecraft:wheat` from above the millstone. Allow time for the output (the recipe requires 150 processing ticks at a processing speed of 1). Empty-handed right-click retrieves the finished `create:wheat_flour`. For an independent lab check, leave the flour in its output inventory until the fixture samples it.

These are relative relationships, with room layout, orientation, footprint and door left to the agent. For a different entrance, preserve a two-block-high clear route through the wall and reroute the drive where needed. Recheck the intake, every transmission contact, and the product after changing the layout.

## Create sources

The target Create JAR's Ponder water wheel and millstone storyboards demonstrate rim flow, cogwheel input, top delivery and empty-hand retrieval. `GearboxBlock.hasShaftTowards` exposes faces perpendicular to its `AXIS`. `MillstoneBlock.hasShaftTowards` exposes the bottom. `data/create/recipes/milling/wheat.json` defines the recipe. Measure real speed from the world or the kinetics probe.
