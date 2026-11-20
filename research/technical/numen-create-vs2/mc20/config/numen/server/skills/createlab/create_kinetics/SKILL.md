---
name: create_kinetics
description: Build, inspect, and repair Create 6.0.8.1 water-powered crushing wheels on Fabric 1.20.1 using signed RPM, stress, controller facing, and a real recipe output.
---

Create 6.0.8.1 small water wheels supply 8 signed RPM when water moves around the perimeter. A source cell alone is not proof of flow. Put each crusher on a shaft whose axis matches the crusher axis. Put the two wheels two cells apart perpendicular to their shaft axes. For wheels along X, separated along Z, the wheel to the north should turn +8 and the one to the south −8 to feed downward. A controller block appears in the gap.

Keep the intake and outlet clear. Feed one gravel via a hopper into the controller with a collection hopper/barrel below. Verify sand in the barrel. If the build receipt reports en-route placed blocks, check and clear the feeding path explicitly.

Use `numen.scan.block(position)` to read block ID, axis, and facing. If `createlab.machine` is available, `createlab.machine.inspect(position)` reads effective and theoretical RPM, stress, capacity, water flow, crusher validity and processing speed. Use `numen.scan.container` at the output barrel to confirm the actual result.

For a stalled side, check water wheel RPM, then compare shaft axis and crushing wheel RPM. If the water wheel is turning, the wheel is at zero, and the shaft axis is wrong, replace that shaft in the approved construction area. Re-read the controller and test the recipe after repair. Treat a placement receipt as a placement result and verify machine behavior separately.
