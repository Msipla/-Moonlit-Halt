package lab;

import com.dwinovo.numen.api.NumenPlugins;
import com.dwinovo.numen.api.entity.Companions;
import com.dwinovo.numen.api.permission.Action;
import com.dwinovo.numen.api.sdk.Doc;
import com.dwinovo.numen.api.sdk.Fn;
import com.dwinovo.numen.api.sdk.Pending;
import com.dwinovo.numen.api.sdk.ServerCall;
import com.dwinovo.numen.script.ApiError;
import com.dwinovo.numen.script.ErrorKind;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod("numen_aero_probe")
public final class AeroProbe {
    public AeroProbe() {
        NumenPlugins.register("aerolab", api -> api.api("craft", "Observe and assemble a Sable test craft.", Craft.class));
        NeoForge.EVENT_BUS.addListener(AeroProbe::registerFixture);
    }

    private static void registerFixture(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("labspawn").requires(source -> source.hasPermission(4))
            .executes(ctx -> {
                var source = ctx.getSource();
                var owner = UUID.fromString("35d7e74e-b562-4f14-8fc4-a63df0998c02");
                Companions.summon(source.getServer(), owner, "LabBot", source.getLevel(),
                    new Vec3(8.5, -60.0, 5.5), arrival -> {
                        if (arrival.body() == null) {
                            source.sendFailure(Component.literal("LabBot did not spawn"));
                            return;
                        }
                        arrival.body().setGameMode(GameType.CREATIVE);
                        source.sendSuccess(() -> Component.literal("Fixture LabBot spawned"), false);
                    });
                return 1;
            }));
    }

    public static final class Craft {
        public record Args(@Doc("Assembler block on the static test stand.") BlockPos cell) {}
        public record Assembly(boolean accepted, String problem) {}
        public record Sample(String id, double x, double y, double z, double vx, double vy, double vz, double mass) {}

        @Fn("Assemble a reachable static Simulated assembler using its own server implementation.")
        public static Pending<Assembly> assemble(ServerCall call, Args args) {
            var level = call.her().serverLevel();
            var p = args.cell();
            if (call.her().position().distanceToSqr(Vec3.atCenterOf(p)) > 25 || !level.hasChunkAt(p))
                throw new ApiError(ErrorKind.OUT_OF_REACH, "Come within five blocks of the loaded assembler.", null);
            if (p.getX() < 5 || p.getX() > 16 || p.getZ() < 0 || p.getZ() > 8)
                throw new ApiError(ErrorKind.BAD_ARGUMENT, "This function is limited to the test stand.", null);
            if (!(level.getBlockEntity(p) instanceof PhysicsAssemblerBlockEntity))
                throw new ApiError(ErrorKind.NOT_FOUND, "The cell does not contain a static assembler.", null);
            return call.authorize(Action.useBlock(p, level.getBlockState(p))).then(ignored -> {
                if (!(level.getBlockEntity(p) instanceof PhysicsAssemblerBlockEntity assembler)
                        || call.her().position().distanceToSqr(Vec3.atCenterOf(p)) > 25)
                    return new Assembly(false, "The assembler changed or moved out of reach.");
                assembler.assembleOrDisassemble();
                var failure = assembler.getLastAssemblyException();
                return new Assembly(failure == null, failure == null ? "" : failure.getMessage());
            });
        }

        @Fn("Read actual Sable plot IDs, world position, velocity, and mass.")
        public static List<Sample> motion(ServerCall call) {
            var level = call.her().serverLevel();
            var container = SubLevelContainer.getContainer(level);
            List<Sample> samples = new ArrayList<>();
            if (container == null) return samples;
            for (ServerSubLevel craft : container.getAllSubLevels()) {
                var p = craft.logicalPose().position();
                var v = RigidBodyHandle.of(craft).getLinearVelocity();
                samples.add(new Sample(craft.getUniqueId().toString(), p.x(), p.y(), p.z(),
                    v.x(), v.y(), v.z(), craft.getMassTracker().getMass()));
            }
            return samples;
        }
    }
}
