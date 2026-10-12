package dev.mispla.abyss;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class TerrainTest {
    @Test void safeRouteAndTwoBlockRailAreContinuous() {
        for (int z = 63; z <= 111; z++) {
            assertEquals(Terrain.FLOOR, Terrain.materialAt(0, 38, z, Terrain.SEED));
            assertEquals(Terrain.AIR, Terrain.materialAt(0, 39, z, Terrain.SEED));
            assertEquals(Terrain.AIR, Terrain.materialAt(0, 40, z, Terrain.SEED));
        }
        for (int x = -22; x <= 22; x++) for (int y = 39; y <= 40; y++) {
            assertNotEquals(Terrain.AIR, Terrain.materialAt(x, y, 62, Terrain.SEED));
            if (Math.abs(x) > 3) assertNotEquals(Terrain.AIR, Terrain.materialAt(x, y, 77, Terrain.SEED));
        }
        for (int z = 62; z <= 77; z++) for (int x : new int[]{-22, 22}) {
            assertEquals(Terrain.RAIL_NS, Terrain.materialAt(x, 39, z, Terrain.SEED));
            assertEquals(Terrain.RAIL_NS, Terrain.materialAt(x, 40, z, Terrain.SEED));
        }
    }

    @Test void seedControlsRockShapeButNotSafeRoute() {
        int changed = 0;
        for (int x = -90; x < 90; x += 3) for (int z = -150; z < 60; z += 3)
            for (int y = -50; y < 105; y += 3) {
                int first = Terrain.materialAt(x, y, z, Terrain.SEED);
                assertEquals(first, Terrain.materialAt(x, y, z, Terrain.SEED));
                if (first != Terrain.materialAt(x, y, z, Terrain.SEED + 1)) changed++;
            }
        assertTrue(changed > 1000, "Changing seed should affect rock, not just metadata.");
    }

    @Test void fixturesAreUniqueAndFarFixturesTouchExposedRock() {
        Set<Terrain.Fixture> unique = new HashSet<>(Terrain.fixtures(Terrain.SEED));
        assertEquals(Terrain.fixtures(Terrain.SEED).size(), unique.size());
        assertEquals(26, unique.size());
        for (Terrain.Fixture f : unique) if (f.z() < 60) {
            assertEquals(Terrain.ROCK, Terrain.materialAt(f.x(), f.y(), f.z(), Terrain.SEED));
            assertEquals(Terrain.AIR, Terrain.materialAt(f.x(), f.y(), f.z() + 1, Terrain.SEED));
        }
    }

    @Test void chamberHasDeepDropAndSealedRoof() {
        assertEquals(Terrain.AIR, Terrain.materialAt(28, 35, 30, Terrain.SEED));
        assertEquals(Terrain.AIR, Terrain.materialAt(28, -10, 30, Terrain.SEED));
        assertEquals(Terrain.ROCK, Terrain.materialAt(28, -50, 30, Terrain.SEED));
        assertEquals(Terrain.BEDROCK, Terrain.materialAt(28, 127, 30, Terrain.SEED));
        assertEquals(Terrain.BEDROCK, Terrain.materialAt(28, -64, 30, Terrain.SEED));
    }
}
