package lab;

import com.dwinovo.numen.api.sdk.Fn;
import com.dwinovo.numen.api.sdk.Doc;
import com.dwinovo.numen.api.sdk.Pending;
import com.dwinovo.numen.api.permission.Action;
import com.dwinovo.numen.script.ApiError;
import com.dwinovo.numen.script.ErrorKind;
import net.minecraft.class_2338;
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity;
import org.valkyrienskies.eureka.gui.shiphelm.ShipHelmScreenMenu;
import com.dwinovo.numen.api.sdk.ServerCall;
import java.util.ArrayList;
import java.util.List;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.core.api.ships.LoadedServerShip;

public final class Ships {
    public record Sample(String id, double x, double y, double z, double vx, double vy, double vz) {}

    public record Helm(@Doc("Static helm used to open the current Eureka menu.") class_2338 cell) {}
    public record Accepted(boolean accepted) {}

    @Fn("Press Assemble in the currently open Eureka helm menu.")
    public static Pending<Accepted> assemble(ServerCall call, Helm args) {
        var player = call.her();
        var level = player.method_51469(); // ServerPlayer.serverLevel()
        var p = args.cell();
        if (p.method_10263() != 8 || p.method_10264() != -53 || p.method_10260() != 2
                || !(level.method_8321(p) instanceof ShipHelmBlockEntity))
            throw new ApiError(ErrorKind.BAD_ARGUMENT, "Expected the helm on the isolated test stand.", null);
        if (!(player.field_7512 instanceof ShipHelmScreenMenu menu))
            throw new ApiError(ErrorKind.BAD_ARGUMENT, "Open the helm menu by sneaking and clicking it first.", null);
        return call.authorize(Action.useBlock(p, level.method_8320(p))).then(ignored ->
            new Accepted(player.field_7512 == menu && menu.method_7604(player, 0))); // clickMenuButton()
    }

    @Fn("Report position and velocity of locally loaded VS2 ships, in world coordinates.")
    public static List<Sample> motion(ServerCall call) {
        var level = call.her().method_51469(); // ServerPlayer.serverLevel()
        List<Sample> samples = new ArrayList<>();
        for (LoadedServerShip ship : ((IShipObjectWorldServerProvider)level).getShipObjectWorld().getLoadedShips()) {
            var p = ship.getTransform().getPositionInWorld();
            var v = ship.getVelocity();
            samples.add(new Sample(Long.toString(ship.getId()), p.x(), p.y(), p.z(), v.x(), v.y(), v.z()));
        }
        return samples;
    }
}
