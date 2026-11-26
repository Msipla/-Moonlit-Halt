# Small mobile bases with Engineering 0.2.6-dev

## Layout and execution

1. Get the local ground or shoreline and check `engineering.site.info()` once. Pick a heading, a boarding side and a work area. Sketch the side silhouette as well as the floor. Use a stepped bow or roof and one contrasting band to show orientation. Keep the helm near the forward sightline. Put seating, sleep or storage bays against the side, with a straight or gently turning passage between the entry and the useful spaces.
2. Reserve the passage first. Each route cell needs a solid, full top below it and two clear blocks at feet and head. Include the approach deck, the threshold, and a reachable tile beside each fixture. Avoid dropping or jumping through the doorway. Put the helm, anchor, and cargo beside the path. Put the anchor wherever it can be operated from the deck while leaving the walking surface continuous. For a grazing roof, raise only the cells above the route, then fit the furnishings around the clearance.
3. Draw the shell, route, and controlled equipment as one block plan. Use `engineering.site.plan({cells=cells,walk=walk})`. Each walk entry is a feet position. Read `changes.rejects` and `clearance` and correct the relevant part before `engineering.site.apply(cells)`. Read the route again with `engineering.site.plan({walk=walk})` against the placed hull. Keep the companion stationary for these direct writes.
4. Assemble only a connected hull made by your companion, using the IDs in `engineering.site.info().hull`, a single oak helm and a single anchor. The direct apply batch limit and the 64-cell assembly limit are checked separately. Multiple construction batches still produce one hull subject to the assembly limit. Fit the project and enough room to travel inside the configured site. Keep the entire design within the assembly palette. Beds, barrels and Create machine blocks can be outside that palette even where site placement accepts them. Report unsupported furnishings or processing separately and plan the smallest useful hull.

## Choose the carrier

- **Air.** Make the balloon group physically part of the hull. Leave vertical room over the station and avoid burying the anchor or balloons inside the walkway. Test lift and settle at low altitude before a short drive and turn.
- **Surface.** Place flotation on both sides and keep the entry above the intended waterline. Leave a flat deck from the entry to the interior and space to turn without clipping the shore. Verify afloat, forward, turn and moor in that order.
- **Road.** Allow chassis and steering clearance under the floor, with door and interior access above it. Identify an installed compatible driven chassis before attempting powered travel.
- **Submerged.** Group the boarding hatch and vertical access together. Keep space for control, living and ballast apart. Verify depth hold, resurfacing and the dry interior independently before calling the result habitable.

## Read the result

Print `engineering_vessels.ship.inspect()` as a JSON string before and after a short drive. Read the actual positions, rotation and stability. Check the stationary cabin with a real player, then use a separate passenger during a measured drive. A pilot leaving this engineering drive triggers a stop. Pilot-free cruise requires an independently verified controller for that vessel. Keep stationary power and inventory tests separate from the same machine's test on an assembled moving ship. Confirm captured parts, real power and two fresh input/output rounds before claiming onboard processing.
