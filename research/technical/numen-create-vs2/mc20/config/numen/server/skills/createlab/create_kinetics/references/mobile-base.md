# Small mobile bases with Numen Engineering

## Layout and execution

1. Get the local ground or shoreline and check `engineering.site.info()` once. Pick a heading, a boarding side and a work area. Sketch the side silhouette as well as the floor. Use a stepped bow or roof and one contrasting band to show orientation. Keep the helm near the forward sightline. Put seating, sleep or storage bays against the side, with a straight or gently turning passage between the entry and the useful spaces.
2. Reserve the passage first. Each route cell needs a solid, full top below it and two clear blocks at feet and head. Include the approach deck, the threshold, and a reachable tile beside each fixture. Avoid dropping or jumping through the doorway. Put the helm, anchor, and cargo beside the path. Put the anchor wherever it can be operated from the deck while leaving the walking surface continuous. For a grazing roof, raise only the cells above the route, then fit the furnishings around the clearance.
3. Generate the floor, walls, roof and fittings from one plan keyed by coordinates. Decide which block owns each cell before splitting into batches. Preview the current batch with `engineering.site.check(cells)` or `engineering.site.plan({cells=cells,walk=walk})` for its route. Each walk entry is a feet position. Fix the reported cells, preserve accepted batches, then use `engineering.site.apply(cells)`. Read the full placed route again with `engineering.site.plan({walk=walk})`. Keep the companion stationary for these writes.
4. Assemble the connected hull from blocks placed by your companion. Use the IDs in `engineering.site.info().hull`, one oak helm and one anchor. Read `batch_limit` and `hull_limit` separately. Older 0.2.6 installs have a 64-cell hull limit and no `hull_limit` field. Divide construction into orderly batches. Leave enough space in the site for hull rotation and travel. Read the palette before placing furniture or machines. When supported, use a reachable barrel, side seating and partial blocks to identify the cabin's uses.

## Blueprint lessons

The 1.20.1 yacht has a fully connected 626-cell body and a narrow hull below a tall mast. Preserve the deck and its entrance before adding the tall profile. The airship's largest connected component is 1,490 of 2,141 cells. Place all lift hardware in physical contact with the hull and size it for the final mass. The research submarine's connected hull is 417 cells after excluding terrain and separating disconnected shapes. Leave scenery, stands and stray parts outside the assembly.

Place an upward-facing creative motor directly below a millstone to form a simple workshop column. A hopper facing down takes items from the input barrel one cell above it. Put the column beside the aisle and give the input a reachable work surface. Read the moving machine's RPM and its input and output separately.

## Choose the carrier

- **Air.** Build the floor in empty space above the ground. Leave several blocks below the floor and above the balloons for stabilization, and leave side space for turns. Count both ordinary blocks and connected balloons before assembling. A 204-cell wooden workshop with 44 connected balloons hovered in this setup. A similar 184-cell cabin with 12 balloons sank. For a similarly light hull, start near one balloon per three to four other blocks. Spread them across the roof and count the final lift hardware after edits. This is an empirical starting point for these mod settings. Test the actual lift before attempting longer travel.
- **Surface.** Place flotation on both sides and keep the entry above the intended waterline. Leave a flat deck from the entry to the interior and space to turn without clipping the shore. Verify afloat, forward, turn and moor in that order.
- **Road.** Allow chassis and steering clearance under the floor, with door and interior access above it. Identify an installed compatible driven chassis before attempting powered travel.
- **Submerged.** Group the boarding hatch and vertical access together. Keep space for control, living and ballast apart. Verify depth hold, resurfacing and the dry interior independently before calling the result habitable.

## Read the result

Print `engineering_vessels.ship.inspect()` as a JSON string before and after a short drive. Read actual position, rotation and stability. When available, use `engineering_vessels.ship.inspect_onboard(dx,dy,dz)` with the machine's offset from the helm. A pilot leaving an engineering pulse triggers a stop. For a human voyage, use `handover()` after anchoring, with owner approval. The owner seats at the native helm and can engage Eureka cruise, dismount and retake the helm. The site edge and owner disconnect still stop the voyage. Use actual game observations to verify both the walk and the vessel displacement. Verify first and repeated input and output while anchored and while moving.
