package dev.mispla.abyss;

import java.util.ArrayList;
import java.util.List;

/** Pure terrain function: no world edits, RNG order, renderer or Minecraft dependency. */
public final class Terrain {
    public static final long SEED = 74102002L;
    public static final int MIN_Y = -64, HEIGHT = 192;
    public static final int AIR = 0, ROCK = 1, BEDROCK = 2, FLOOR = 3, RAIL_NS = 4, RAIL_EW = 5;
    private static final double[][] PILLARS = {
        {-48, -43, 8}, {-24, -92, 7}, {36, -78, 9}, {69, -32, 12},
        {-72, 10, 12}, {12, 7, 7}, {-5, -38, 6}
    };

    private Terrain() {}

    public record Fixture(int x, int y, int z) {}

    public record Column(int x, int z, boolean cavern, double floor, double roof,
                         double pillarRatio, double pillarPhase) {
        public int materialAt(int y) {
            if (y < MIN_Y || y >= MIN_Y + HEIGHT) return AIR;
            if (y == MIN_Y || y == MIN_Y + HEIGHT - 1) return BEDROCK;
            // Sealed calibration lane, isolated from the scene's other sources.
            if (x >= -80 && x <= -58 && z >= 98 && z <= 102) {
                if (y == 38) return FLOOR;
                if (y >= 39 && y <= 44) return AIR;
            }
            // An enclosed, level route, balcony and two-block-high iron guardrail.
            if (Math.abs(x) <= 3 && z >= 77 && z <= 112) {
                if (y == 38) return FLOOR;
                if (y >= 39 && y <= 44) return AIR;
            }
            if (Math.abs(x) <= 22 && z >= 62 && z <= 77) {
                if (y == 38) return FLOOR;
                if (y == 39 || y == 40) {
                    if (Math.abs(x) == 22) return RAIL_NS;
                    if (z == 62 || z == 77 && Math.abs(x) > 3) return RAIL_EW;
                }
                if (y >= 39 && y <= 46) return AIR;
            }
            if (!cavern || y <= floor || y >= roof) return ROCK;
            double thickness = 0.92 + 0.17 * Math.sin(y * 0.075 + pillarPhase)
                    + 0.055 * Math.sin(y * 0.31 + pillarPhase);
            return pillarRatio <= thickness ? ROCK : AIR;
        }
    }

    public static Column column(int x, int z, long seed) {
        double q = sq(x / 104.0) + sq((z + 44) / 124.0);
        double border = 1 + 0.075 * noise(x / 24.0, z / 24.0, seed + 1);
        double floor = -43 + 23 * q * q + 7 * noise(x / 28.0, z / 28.0, seed + 2);
        // Two raised shelves create a cliff across the valley floor.
        if (x > 24 && z < 5) floor += 12;
        if (x < -38 && z < -36) floor += 6;
        double roof = 100 - 33 * q + 7 * noise(x / 35.0, z / 35.0, seed + 3);
        double ratio = 100, phase = 0;
        for (int i = 0; i < PILLARS.length; i++) {
            double[] p = PILLARS[i];
            double d = Math.hypot(x - p[0], z - p[1]) / p[2];
            if (d < ratio) { ratio = d; phase = i * 1.71 + (seed & 255) * 0.01; }
        }
        return new Column(x, z, q < border, floor, roof, ratio, phase);
    }

    public static int materialAt(int x, int y, int z, long seed) {
        return column(x, z, seed).materialAt(y);
    }

    /** Embed sparse emitters into the observer-facing surfaces of actual pillars. */
    public static List<Fixture> fixtures(long seed) {
        List<Fixture> result = new ArrayList<>();
        result.add(new Fixture(-78, 41, 100));
        result.add(new Fixture(-3, 41, 91));
        result.add(new Fixture(3, 41, 82));
        result.add(new Fixture(-16, 39, 70));
        result.add(new Fixture(16, 39, 70));
        for (double[] pillar : PILLARS) {
            int x = (int) pillar[0], centerZ = (int) pillar[1];
            for (int y : new int[]{-18, 12, 43}) {
                for (int z = centerZ + (int) Math.ceil(pillar[2] * 1.3); z >= centerZ; z--) {
                    if (materialAt(x, y, z, seed) == ROCK
                            && materialAt(x, y, z + 1, seed) == AIR) {
                        result.add(new Fixture(x, y, z));
                        break;
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static double sq(double n) { return n * n; }

    static double noise(double x, double z, long seed) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz, seed), b = hash(ix + 1, iz, seed);
        double c = hash(ix, iz + 1, seed), d = hash(ix + 1, iz + 1, seed);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    private static double hash(int x, int z, long seed) {
        long n = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        n = (n ^ (n >>> 30)) * 0xBF58476D1CE4E5B9L;
        n = (n ^ (n >>> 27)) * 0x94D049BB133111EBL;
        return ((n ^ (n >>> 31)) >>> 11) * 0x1.0p-53 * 2 - 1;
    }
}
