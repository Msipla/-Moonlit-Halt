package lab.createlab;

import java.util.Arrays;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_3218;

public final class ConstructionBounds {
    private static final int[] BOX = readBox();

    private ConstructionBounds() {}

    private static int[] readBox() {
        String configured = System.getProperty("createlab.scope");
        if (configured == null) return null;
        int[] box = Arrays.stream(configured.split(",", -1)).mapToInt(Integer::parseInt).toArray();
        if (box.length != 6 || box[0] > box[3] || box[1] > box[4] || box[2] > box[5]) {
            throw new IllegalArgumentException("createlab.scope requires minX,minY,minZ,maxX,maxY,maxZ");
        }
        return box;
    }

    public static boolean enabled() {
        return BOX != null;
    }

    public static boolean contains(class_3218 level, class_2338 pos) {
        if (!enabled() || !class_1937.field_25179.equals(level.method_27983())) return false;
        int x = pos.method_10263(), y = pos.method_10264(), z = pos.method_10260();
        return x >= BOX[0] && x <= BOX[3] && y >= BOX[1] && y <= BOX[4] && z >= BOX[2] && z <= BOX[5];
    }
}
