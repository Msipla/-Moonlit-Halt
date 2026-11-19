package dev.mispla.redstonemusic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

record MusicProject(String title, String reference, List<String> instruments, Pack pack,
                    StructureTemplate template, List<BlockPos> noteBlocks) {
    private static final int MAX_COMPRESSED = 2 * 1024 * 1024;
    private static final int MAX_EXPANDED = 8 * 1024 * 1024;
    private static final int MAX_VOLUME = 50_000;
    private static final int MAX_BLOCKS = 12_000;
    private static final Set<String> SUPPORTED = Set.of(
        "note_block", "redstone_wire", "repeater", "observer", "comparator",
        "oak_button", "oak_wall_sign", "stone", "stone_slab", "redstone_block", "redstone_lamp",
        "glass", "white_wool", "sand", "dirt", "oak_planks", "oak_slab", "clay", "gold_block",
        "packed_ice", "bone_block", "iron_block", "soul_sand", "pumpkin", "emerald_block",
        "hay_block", "glowstone", "piston", "sticky_piston", "piston_head", "tinted_glass"
    );

    record Pack(String name, String version, URI url) {}

    static MusicProject read(Path metadata, Path structure) throws IOException {
        if (Files.size(metadata) > 16_384) {
            throw new IOException("Metadata exceeds 16 KiB.");
        }
        JsonObject info;
        try (Reader reader = Files.newBufferedReader(metadata)) {
            info = JsonParser.parseReader(reader).getAsJsonObject();
        }
        String title = info.get("title").getAsString().strip();
        if (title.isEmpty() || title.length() > 100) {
            throw new IOException("Project title must contain 1–100 characters.");
        }
        byte[] bytes;
        try (InputStream stream = Files.newInputStream(structure)) {
            bytes = stream.readNBytes(MAX_COMPRESSED + 1);
        }
        if (bytes.length > MAX_COMPRESSED) {
            throw new IOException("Compressed template exceeds 2 MiB.");
        }
        String sha = digest(bytes);
        if (!sha.equals(info.get("structureSha256").getAsString())) {
            throw new IOException("Template and metadata SHA-256 differ.");
        }
        byte[] expanded;
        try (GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            expanded = stream.readNBytes(MAX_EXPANDED + 1);
        }
        if (expanded.length > MAX_EXPANDED) {
            throw new IOException("Expanded template exceeds 8 MiB.");
        }
        CompoundTag root;
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(expanded))) {
            root = NbtIo.read(stream, new NbtAccounter(MAX_EXPANDED));
        }
        List<BlockPos> notes = validate(root);

        List<String> sounds = new ArrayList<>();
        for (var instrument : info.getAsJsonArray("instruments")) {
            String sound = instrument.getAsString();
            if (!sound.matches("[a-z_]{1,32}") || sounds.size() >= 16) {
                throw new IOException("Invalid instrument list.");
            }
            sounds.add(sound);
        }
        Pack pack = null;
        if (info.has("resourcePack") && !info.get("resourcePack").isJsonNull()) {
            JsonObject data = info.getAsJsonObject("resourcePack");
            String name = data.get("name").getAsString().strip();
            String version = data.has("version") ? data.get("version").getAsString().strip() : "";
            URI url = data.has("url") ? URI.create(data.get("url").getAsString()) : null;
            if (name.isEmpty() || name.length() > 100 || version.length() > 40
                || (url != null && (!"https".equalsIgnoreCase(url.getScheme())
                    || url.getHost() == null || url.getUserInfo() != null))) {
                throw new IOException("Invalid resource pack information. Links must use HTTPS.");
            }
            pack = new Pack(name, version, url);
        }
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), root);
        return new MusicProject(title, metadata.getFileName() + "#" + sha, List.copyOf(sounds), pack, template, notes);
    }

    private static List<BlockPos> validate(CompoundTag root) throws IOException {
        if (root.getInt("DataVersion") != 3465 || root.contains("palettes")
            || !root.contains("entities", Tag.TAG_LIST)
            || !root.getList("entities", Tag.TAG_COMPOUND).isEmpty()) {
            throw new IOException("Expected a Java 1.20.1 structure with no entities and one palette.");
        }
        ListTag size = root.getList("size", Tag.TAG_INT);
        if (size.size() != 3) {
            throw new IOException("Missing structure dimensions.");
        }
        int width = size.getInt(0);
        int height = size.getInt(1);
        int depth = size.getInt(2);
        if (width < 1 || height < 1 || depth < 1 || width > 96 || height > 64 || depth > 96
            || (long) width * height * depth > MAX_VOLUME) {
            throw new IOException("Prototype limit is 96×64×96 and 50,000 cells.");
        }
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        if (palette.isEmpty() || palette.size() > 512 || blocks.isEmpty() || blocks.size() > MAX_BLOCKS) {
            throw new IOException("Template is empty or exceeds 12,000 blocks.");
        }
        List<String> states = new ArrayList<>();
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag stateTag = palette.getCompound(index);
            ResourceLocation id = ResourceLocation.tryParse(stateTag.getString("Name"));
            if (id == null || !id.getNamespace().equals("minecraft") || !SUPPORTED.contains(id.getPath())
                || !BuiltInRegistries.BLOCK.containsKey(id)) {
                throw new IOException("Unsupported circuit block: " + stateTag.getString("Name"));
            }
            var state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), stateTag);
            CompoundTag actual = NbtUtils.writeBlockState(state).getCompound("Properties");
            CompoundTag expected = stateTag.getCompound("Properties");
            for (String property : expected.getAllKeys()) {
                if (!actual.contains(property, Tag.TAG_STRING)
                    || !actual.getString(property).equals(expected.getString(property))) {
                    throw new IOException("Invalid block property: " + id + "." + property);
                }
            }
            states.add(id.getPath());
        }
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> notes = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag entry = blocks.getCompound(index);
            ListTag position = entry.getList("pos", Tag.TAG_INT);
            int state = entry.getInt("state");
            if (position.size() != 3 || !entry.contains("state", Tag.TAG_INT)
                || state < 0 || state >= states.size()) {
                throw new IOException("Invalid block record.");
            }
            BlockPos pos = new BlockPos(position.getInt(0), position.getInt(1), position.getInt(2));
            if (pos.getX() < 0 || pos.getX() >= width || pos.getY() < 0 || pos.getY() >= height
                || pos.getZ() < 0 || pos.getZ() >= depth || !seen.add(pos)) {
                throw new IOException("Duplicate or out-of-bounds block position.");
            }
            if (entry.contains("nbt")) {
                if (!states.get(state).equals("oak_wall_sign")
                    || !entry.getCompound("nbt").getString("id").equals("minecraft:sign")) {
                    throw new IOException("Only plain project signs may have block-entity data.");
                }
                validatePlainSign(entry.getCompound("nbt"));
            }
            if (states.get(state).equals("note_block")) {
                notes.add(pos);
            }
        }
        if (notes.isEmpty()) {
            throw new IOException("The template contains no note blocks.");
        }
        return List.copyOf(notes);
    }

    private static void validatePlainSign(CompoundTag sign) throws IOException {
        CompoundTag front = sign.getCompound("front_text");
        ListTag lines = front.getList("messages", Tag.TAG_STRING);
        if (!sign.getAllKeys().equals(Set.of("id", "front_text"))
            || !front.getAllKeys().equals(Set.of("messages")) || lines.size() != 4) {
            throw new IOException("Unsupported sign fields.");
        }
        for (int index = 0; index < lines.size(); index++) {
            var text = JsonParser.parseString(lines.getString(index));
            if (!text.isJsonObject() || !text.getAsJsonObject().keySet().equals(Set.of("text"))
                || !text.getAsJsonObject().get("text").isJsonPrimitive()
                || !text.getAsJsonObject().get("text").getAsJsonPrimitive().isString()
                || text.getAsJsonObject().get("text").getAsString().length() > 120) {
                throw new IOException("Only plain sign text is supported.");
            }
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
