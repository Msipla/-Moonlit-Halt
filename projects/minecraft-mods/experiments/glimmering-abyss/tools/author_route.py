"""Small Phase 5 addition to the original workshop, authored with vanilla blocks.

The generator and Phase 4 template stay unchanged. One composite NBT explicitly
places the refined workshop, a finite shaft, one iron bridge and one overlook.
"""
from pathlib import Path
import gzip, hashlib, json, runpy

P = Path(__file__).resolve().parents[1]
a = runpy.run_path(str(P / 'tools/author_workshop.py'))
b, put, box, rail = (a[k] for k in ('b', 'put', 'box', 'rail'))

# Preserve the workshop footprint, windows, interior, roof and tower silhouette.
# Add a brick string course, capped brick chimneys and restrained iron cresting.
for x in range(34, 57):
    put(x, 46, 40, 'bricks')
for x, z in [(37, 23), (53, 31)]:
    box(x, 60, z, x+1, 64, z+1, 'bricks')
for x in [31, 59]:
    z = 39 if x == 31 else 41  # Right finial must not overlap tower warm windows.
    box(x, 54, z, x, 56, z, 'polished_blackstone_bricks')
    put(x, 57, z, 'polished_blackstone_brick_slab', type='bottom', waterlogged='false')
    put(x, 58, z, 'iron_bars', north='false', south='false', east='false', west='false', waterlogged='false')
for z in range(18, 41):
    put(45, 62, z, 'iron_bars', north='true', south='true', east='false', west='false', waterlogged='false')
# Small pointed porch pediment; clear opening below is unchanged.
for x in range(39, 46):
    h = 44 + min(x-39, 45-x)
    put(x, h, 43, 'polished_blackstone_brick_stairs', facing='east' if x < 42 else 'west', half='bottom', shape='straight', waterlogged='false')
put(42, 48, 43, 'iron_bars', north='false', south='false', east='false', west='false', waterlogged='false')

# Finite local excavation, 101 blocks below the walk level. No height rewrite,
# void damage, global fog or lighting change. Dark floor is retained at y=-63.
# Irregular perimeter and lower rock ribs give the opening a natural edge.
shaft_columns = []
for x in range(-23, 30):
    for z in range(-20, 61):
        if ((x-3)/26.0)**2 + ((z-20)/40.0)**2 > 1.0:
            continue
        # Keep the original near giant pillar and its embedded emitters intact.
        if (x-12)**2 + (z-7)**2 <= 10**2:
            continue
        shaft_columns.append((x, z))
        box(x, -62, z, x, 37, z, 'air')
        put(x, -63, z, 'black_concrete')
# A few unlit ribs descend into the blue-black space, away from the walk line.
for x, z in [(-18, 26), (23, 22), (22, 51)]:
    box(x, -62, z, x+1, -8, z+1, 'deepslate_tiles')

# 53-block iron span; the closed top-half trapdoors have a surface at y=39.
# Real iron bars, end masonry and a simple under-deck truss, no custom blocks.
box(-24, 39, 33, 29, 42, 37, 'air')
for x in range(-24, 30):
    for z in range(33, 38):
        put(x, 38, z, 'iron_trapdoor', facing='north', half='top', open='false', powered='false', waterlogged='false')
rail(-24, 32, 28, 32)
rail(-24, 38, 28, 38)
for z in [32, 38]:
    box(-24, 35, z, 28, 35, z, 'polished_blackstone_brick_slab', type='top', waterlogged='false')
    for x in range(-24, 29):
        y = 36 + (abs(x+24) % 6 // 3)
        put(x, y, z, 'iron_bars', north='false', south='false', east='true', west='true', waterlogged='false')
for x in [-24, 28]:
    for z in [32, 38]:
        box(x, -54, z, x+1, 37, z+1, 'deepslate_tiles')
        for y in [-20, 8, 30]:
            box(x-1, y, z-1, x+2, y, z+2, 'polished_deepslate')

# A single compact destination: open lookout, not a second building.
box(-32, 36, 27, -25, 37, 44, 'deepslate_bricks')
box(-32, 38, 27, -25, 38, 44, 'polished_deepslate')
box(-31, 39, 28, -26, 42, 43, 'air')
rail(-32, 27, -25, 27)
rail(-32, 44, -25, 44)
rail(-32, 27, -32, 44)
rail(-25, 27, -25, 32)
rail(-25, 38, -25, 44)
for x,z in [(-30,29),(-30,42)]:
    box(x-1,-50,z-1,x+1,35,z+1,'deepslate_tiles')
# Two modest end lamps and one destination lamp; center of span stays dark.
for x,z in [(28,38),(-25,38),(-32,30)]:
    box(x,41,z,x,42,z,'polished_blackstone_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
    put(x,43,z,'lantern',hanging='false',waterlogged='false')

origin = (-32,-63,-20)
size = (100,139,93)
palette, lookup, blocks = [], {}, []
for pos,s in sorted(b.items()):
    key = json.dumps(s,sort_keys=True)
    if key not in lookup:
        lookup[key] = len(palette)
        palette.append(s)
    blocks.append({'pos':[p-o for p,o in zip(pos,origin)],'state':lookup[key]})
nbt = {'DataVersion':3465,'author':'Mispla / Codex original Phase 5 route','size':list(size),'palette':palette,'blocks':blocks,'entities':[]}
packed = gzip.compress(b'\x0a\0\0'+a['payload'](nbt)[1],mtime=0)
target = P/'src/main/resources/data/glimmeringabyss/structures/exploration.nbt'
target.write_bytes(packed)
manifest = dict(schema=1,template='glimmeringabyss:exploration',origin=origin,size=size,blocks=len(blocks),palette_states=len(palette),template_sha256=hashlib.sha256(packed).hexdigest(),entities=0,shaft_floor_y=-63,walk_surface_y=39,shaft_depth_blocks=102,excavated_columns=len(shaft_columns),bridge_deck='minecraft:iron_trapdoor',bridge_span_blocks=54,additional_buildings=0,authoring='Original vanilla-block finite local shaft and walking route; Phase 4 template and terrain generator unchanged')
(P/'src/main/resources/data/glimmeringabyss/architecture/exploration.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Phase 5:',len(blocks),'explicit positions;',len(packed),'bytes')
