const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const { createRequire } = require('node:module');
const nbt = createRequire(path.join(process.env.LAB_MC20 || path.resolve(__dirname, '../mc20'), 'lab.cjs'))('prismarine-nbt');

function regionChunk(world, group, x, z) {
  const filename = path.join(world, group, `r.${x >> 5}.${z >> 5}.mca`);
  if (!fs.existsSync(filename)) return undefined;
  const bytes = fs.readFileSync(filename);
  if (bytes.length === 0) return undefined;
  if (bytes.length < 8192) throw new Error('Truncated Anvil header: ' + filename);
  const header = 4 * ((x & 31) + 32 * (z & 31));
  const offset = bytes.readUIntBE(header, 3) * 4096;
  if (!offset) return undefined;
  const length = bytes.readUInt32BE(offset);
  if (bytes[offset + 4] !== 2) throw new Error('Unsupported Anvil compression');
  return nbt.simplify(nbt.parseUncompressed(zlib.inflateSync(bytes.subarray(offset + 5, offset + 4 + length))));
}

function longBits(value) {
  if (Array.isArray(value)) return (BigInt(value[0] >>> 0) << 32n) | BigInt(value[1] >>> 0);
  return BigInt.asUintN(64, BigInt(value));
}

function snapshot(world, box) {
  const [xmin, ymin, zmin, xmax, ymax, zmax] = box;
  const blocks = {}, blockEntities = [], items = [];
  const within = (x,y,z) => x >= xmin && x <= xmax && y >= ymin && y <= ymax && z >= zmin && z <= zmax;
  for (let cx = xmin >> 4; cx <= xmax >> 4; cx++) for (let cz = zmin >> 4; cz <= zmax >> 4; cz++) {
    const chunk = regionChunk(world, 'region', cx, cz);
    if (!chunk) continue;
    for (const entity of chunk.block_entities || []) if (within(entity.x, entity.y, entity.z)) blockEntities.push(entity);
    const entityChunk = regionChunk(world, 'entities', cx, cz);
    for (const entity of entityChunk?.Entities || []) {
      if (entity.id === 'minecraft:item' && within(...entity.Pos)) items.push({pos: entity.Pos, item: entity.Item});
    }
    for (const section of chunk.sections || []) {
      const sy = section.Y * 16;
      const states = section.block_states;
      if (sy + 15 < ymin || sy > ymax || !states) continue;
      const {palette, data} = states;
      const bits = Math.max(4, Math.ceil(Math.log2(palette.length)));
      const perLong = Math.floor(64 / bits);
      const mask = (1n << BigInt(bits)) - 1n;
      const longs = data?.map(longBits);
      for (let i = 0; i < 4096; i++) {
        const x = cx * 16 + (i & 15), y = sy + (i >> 8), z = cz * 16 + ((i >> 4) & 15);
        if (!within(x,y,z)) continue;
        const idx = longs ? Number((longs[Math.floor(i / perLong)] >> BigInt((i % perLong) * bits)) & mask) : 0;
        const state = palette[idx];
        if (state.Name !== 'minecraft:air') blocks[[x,y,z].join(',')] = state;
      }
    }
  }
  return {blocks, blockEntities, items};
}

module.exports = {snapshot};
