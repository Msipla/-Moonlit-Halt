# 可迁移到 Work ship-bridge 的最小补丁

实测环境：VS 2.4.11 + Eureka 1.6.4，单机集成服务端，2026-11-22。
结果：装配 → 原生乘骑 → 前进 4.049 m → 转向 5.272 m → 锚定漂移 0.000 m。

## 现状问题

`live-lab/ship-bridge/LabShips.java` 的 `steer()` 只设置了 `SeatedControllingPlayer` 附件，
但 `EurekaShipControl.seatedPlayer` 始终为空。Eureka 的 `physTick` 在没有 seatedPlayer 时
不会进入 `getControlData/applyPlayerControl`，所以推力恒为 0。

此外还有三个隐藏前提，缺一个都拿不到位移。

## 补丁（`steer` 全量替换）

```java
private int steer(class_2168 source, String heading, float forward, float up) {
    var ship = ship(source);                     // 已是 getLoadedShips() 的 LoadedServerShip
    var direction = class_2350.method_10168(heading);
    var control = ship == null ? null : ship.getAttachment(EurekaShipControl.class);
    if (control == null || direction == null || !Set.of("north","east","south","west").contains(heading))
        return say(source, "{\"error\":\"native ship or horizontal heading missing\"}");

    // 1) 舵方块在 shipyard 坐标里，不在原世界坐标
    var helm = findHelmBlock(source.method_9225(), ship);
    if (helm == null) return say(source, "{\"error\":\"no helm block inside the ship\"}");

    // 2) 传送到舵上方（世界坐标），解除潜行，再原生右键
    var toWorld = ship.getShipToWorld();
    var seat = toWorld.transformPosition(new org.joml.Vector3d(
        helm.method_10263() + 0.5, helm.method_10264() + 1.5, helm.method_10260() + 0.5));
    var pilot = source.method_9222();             // 需要玩家执行该命令
    if (pilot == null) return say(source, "{\"error\":\"steer needs a player source\"}");
    pilot.method_14251(source.method_9225(), seat.x, seat.y, seat.z, 0f, 0f);
    pilot.method_5660(false);                     // 潜行会让 onUse 去开界面而不是坐下
    pilot.field_13974.method_14262(pilot, source.method_9225(), class_1799.field_8037, class_1268.field_5808,
        new class_3965(org.joml.Vector3d -> Vec3d.ofCenter(helm) 见下, class_2350.field_11036, helm, false));
    if (!pilot.method_5765()) return say(source, "{\"error\":\"native helm did not mount rider\"}");

    // 3) 控制输入
    control.setAnchorsActive(0);
    control.setSeatedPlayer(pilot);
    var input = new SeatedControllingPlayer(direction);
    input.setForwardImpulse(forward);
    input.setUpImpulse(up);
    ship.setAttachment(SeatedControllingPlayer.class, input);
    return sample(source);
}
```

BlockHitResult 的命中向量必须是 **shipyard 坐标**的方块中心：

```java
new class_3965(new class_243(helm.method_10263() + 0.5,
                             helm.method_10264() + 0.5,
                             helm.method_10260() + 0.5),
               class_2350.field_11036, helm, false)
```

`findHelmBlock`：在 `ship.getShipAABB()` 的整数范围内扫 `vs_eureka:oak_ship_helm`。

```java
private static class_2338 findHelmBlock(class_3218 level, LoadedServerShip ship) {
    var box = ship.getShipAABB();
    for (int x = box.minX(); x <= box.maxX(); x++)
        for (int y = box.minY(); y <= box.maxY(); y++)
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                var pos = new class_2338(x, y, z);
                if (class_7923.field_41175.method_10221(level.method_8320(pos).method_26204()).toString()
                        .equals("vs_eureka:oak_ship_helm")) return pos;
            }
    return null;
}
```

`stop` 增加两行：

```java
pilot.method_5848();                 // stopRiding
control.setSeatedPlayer(null);
control.setAnchorsActive(1);
```

## 还需要修的装配入口

`assemble()` 用 `ShipAssembler.collectBlocks` 可以成功，但它返回 `ServerShip`；
后续任何 `getAttachment` 都会抛
`NotImplementedError: Tried to retrieve an attachment on an unloaded ship`。
必须在同一 tick 之后从 `getShipObjectWorld().getLoadedShips()` 按 id 取 `LoadedServerShip`
（`sample()` 里已经这么做了，但 `steer()` 若直接用 `collectBlocks` 的返回值就会踩坑）。

## 实测数据

| 阶段 | 结果 |
| --- | --- |
| 前进 100 tick | 位移 4.049 m，v_z = -1.619 m/s |
| 转向 80 tick（朝东） | 位移 5.272 m，v_x = 1.819 m/s |
| 锚定 200 tick | 漂移 0.000 m |

单机集成服务端与历史探针（`marine-client-probe-1.2`）独立复核：
`mounted_through_native_helm=true`、`horizontal_distance=2.795`、`dismount_passed=true`。
