"""Layout-100 spatial sample. Reuse shipped architecture without changing either old NBT.
The inhabited shelf stays compact; a real, guarded loop enters the folded galleries.
"""
from pathlib import Path
import gzip,json,hashlib,math,importlib.util
# NBT helpers are reused from the existing authoring script, without executing it.
source=Path(__file__).with_name('author_natural_town.py').read_text()
namespace={'__file__':str(Path(__file__).with_name('author_natural_town.py'))};exec(source[:source.index("n=read_nbt")],namespace)
read_nbt,payload=namespace['read_nbt'],namespace['payload']
P=Path(__file__).resolve().parents[1]
n=read_nbt(gzip.decompress((P/'src/main/resources/data/glimmeringabyss/structures/natural_town.nbt').read_bytes()))
b={tuple(v+o for v,o in zip(row['pos'],(-64,34,-106))):n['palette'][row['state']] for row in n['blocks']}
def put(x,y,z,name,**props):b[x,y,z]={'Name':name if ':' in name else 'minecraft:'+name,**({'Properties':{k:str(v) for k,v in props.items()}} if props else {})}
# Local floor coordinates. Axis-aligned legs allow safe stairs and reproducible real walking.
route=[(47,40,-60),(47,40,-84),(22,37,-84),(22,37,-100),(-28,31,-100),(-28,31,-124),(-78,27,-124),(-78,25,-148),(-126,24,-148),(-126,20,-184),(-151,20,-184),(-151,16,-214),(-90,16,-214),(-90,22,-176),(-66,22,-176),(-66,27,-124),(-78,27,-124)]
floors={};facing={}
for a,c in zip(route,route[1:]):
 length=max(abs(c[0]-a[0]),abs(c[2]-a[2]));dx=(c[0]-a[0])//length;dz=(c[2]-a[2])//length
 for i in range(length+1):
  x,z=a[0]+dx*i,a[2]+dz*i;y=round(a[1]+(c[1]-a[1])*i/length)
  nexty=round(a[1]+(c[1]-a[1])*min(i+1,length)/length)
  for offset in range(-2,3):
   pos=(x+(offset if dz else 0),z+(offset if dx else 0));floors[pos]=max(y,floors.get(pos,-99))
   if nexty!=y:facing[pos]=('east' if dx>0 else 'west') if dx else ('south' if dz>0 else 'north')
# The first leg joins the existing north street, so it is an entrance rather
# than an exposed endpoint. Clear the old terrace rail as part of this connector.
entrance={(x,-60) for x in range(46,49)}
# Rock path footing/clearance; walls only on exposed path edges, not a fence through junctions.
for (x,z),y in floors.items():
 for h in range(y-5,y+1):put(x,h,z,'deepslate' if h<y else 'cobbled_deepslate')
 for h in range(y+1,y+7):put(x,h,z,'air')
for (x,z),y in floors.items():
 # A half-height tread where the next floor rises or falls. Choose the higher floor and face uphill.
 neighbors=[((x+dx,z+dz),direction) for dx,dz,direction in [(1,0,'east'),(-1,0,'west'),(0,1,'south'),(0,-1,'north')]]
 higher=next((direction for q,direction in neighbors if floors.get(q,y)==y+1),None)
 if higher:put(x,y+1,z,'cobbled_deepslate_stairs',facing=higher,half='bottom',shape='straight',waterlogged='false')
 if (x,z) not in entrance and any(q not in floors for q,_ in neighbors):put(x,y+1,z,'cobbled_deepslate_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
# Explicit wall arms survive FORCE_STATE placement; neighbouring posts form a continuous guard.
edge={(x,z) for (x,z),y in floors.items() if b.get((x,y+1,z),{}).get('Name')=='minecraft:cobbled_deepslate_wall'}
for x,z in edge:
 y=floors[x,z]
 arms={name:('low' if (x+dx,z+dz) in edge else 'none') for dx,dz,name in [(0,-1,'north'),(0,1,'south'),(1,0,'east'),(-1,0,'west')]}
 put(x,y+1,z,'cobbled_deepslate_wall',up='true',waterlogged='false',**arms)
# Occasional familiar warm lights mark the way back. No combat/biomes/new regions.
for x,y,z in route:
 edge=next(((xx,zz) for xx,zz in floors if abs(xx-x)+abs(zz-z)<=3 and any((xx+dx,zz+dz) not in floors for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)])),None)
 if edge and edge not in entrance:put(edge[0],floors[edge]+2,edge[1],'lantern',hanging='false',waterlogged='false')
# New dock approaches the existing southern lane; no hairpin to the old shaft foot.
for x in range(18,40):
 for z in range(52,66):
  for y in range(34,39):put(x,y,z,'deepslate' if y<38 else 'polished_deepslate')
  for y in range(39,47):put(x,y,z,'air')
# Site-specific landing and bells are placed last by the normal generator.
origin=(-180,-8,-290);size=(264,88,364);palette=[];lookup={};rows=[]
for pos,state in sorted(b.items()):
 key=json.dumps(state,sort_keys=True)
 if key not in lookup:lookup[key]=len(palette);palette.append(state)
 assert all(0<=v-o<dim for v,o,dim in zip(pos,origin,size)),pos
 rows.append({'pos':[v-o for v,o in zip(pos,origin)],'state':lookup[key]})
packed=gzip.compress(b'\x0a\0\0'+payload({'DataVersion':3465,'author':'Mispla / Codex v1 spatial sample','size':list(size),'palette':palette,'blocks':rows,'entities':[]})[1],mtime=0)
(P/'src/main/resources/data/glimmeringabyss/structures/abyss_town.nbt').write_bytes(packed)
(P/'src/main/resources/data/glimmeringabyss/architecture/abyss_town.json').write_text(json.dumps(dict(layout=100,origin=origin,size=size,stage='spatial-preview-before-building-expansion',buildings=7,route=route,blocks=len(rows),template_sha256=hashlib.sha256(packed).hexdigest(),old_templates_unchanged=True),indent=2)+'\n')
print(len(rows),len(packed),'route length',sum(abs(c[0]-a[0])+abs(c[2]-a[2]) for a,c in zip(route,route[1:])))

# Layout 101 grows on the occupied shelf. The layout-100 NBT above remains an
# immutable shipped recipe; neither geological carving nor a giant new plaza
# is baked into this sparse architecture blueprint.
base=b.copy()
def box(x1,y1,z1,x2,y2,z2,name,**props):
 for x in range(x1,x2+1):
  for z in range(z1,z2+1):
   for y in range(y1,y2+1):put(x,y,z,name,**props)
def lamp(x,y,z,hanging=False):put(x,y,z,'lantern',hanging=str(hanging).lower(),waterlogged='false')
def bed(x,y,z):
 put(x,y,z,'blue_bed',facing='north',part='foot',occupied='false')
 put(x,y,z-1,'blue_bed',facing='north',part='head',occupied='false')
def seat(x,y,z):put(x,y,z,'dark_oak_stairs',facing='east',half='bottom',shape='straight',waterlogged='false')
buildings=[]
def dwelling(kind,x,z,w,d,floor,wall):
 h=8;door=z+d//2
 # Only a building-sized footing; the surrounding natural shelf is unpainted.
 box(x-1,floor-3,z-1,x+w,floor,z+d,'deepslate')
 box(x,floor,z,x+w-1,floor,z+d-1,'dark_oak_planks')
 box(x,floor+1,z,x+w-1,floor+h,z+d-1,wall)
 box(x+1,floor+1,z+1,x+w-2,floor+h-1,z+d-2,'air')
 for xx in range(x-1,x+w+1):
  top=floor+h+1+min(xx-(x-1),x+w-xx)//2
  for zz in range(z-1,z+d+1):put(xx,top,zz,'deepslate_tile_stairs',facing='east' if xx<x+w/2 else 'west',half='bottom',shape='straight',waterlogged='false')
  if x<=xx<x+w:
   for zz in (z,z+d-1):box(xx,floor+h+1,zz,xx,top-1,zz,wall)
 # Timber/stone corner articulation, a west-facing open arch, lit rooms.
 for xx in (x,x+w-1):
  for zz in (z,z+d-1):box(xx,floor+1,zz,xx,floor+h,zz,'polished_deepslate')
 box(x,floor+1,door-1,x,floor+3,door+1,'air');lamp(x-1,floor+4,door,True)
 for zz in (z+3,z+d-4):
  box(x+w-1,floor+2,zz,x+w-1,floor+4,zz+1,'glimmeringabyss:warm_window')
  put(x+w-1,floor+5,zz,'glimmeringabyss:warm_window')
 for xx in (x+3,x+w-4):
  box(xx,floor+2,z,xx+1,floor+4,z,'glass_pane',east='true',west='true',north='false',south='false',waterlogged='false')
 lamp(x+w-3,floor+3,z+3)
 put(x+w-3,floor+1,z+d-3,'barrel',facing='up',open='false')
 put(x+3,floor+1,z+d-3,'crafting_table')
 buildings.append(dict(kind=kind,bounds=[x,z,x+w-1,z+d-1],floor=floor,door=[x,door]))
dwelling('bakehouse',74,-64,15,17,41,'stone_bricks')
put(85,42,-60,'furnace',facing='west',lit='false');put(85,42,-62,'smoker',facing='west',lit='false')
box(78,42,-61,80,42,-61,'dark_oak_slab',type='top',waterlogged='false')
seat(78,42,-60);box(76,42,-62,76,44,-60,'bookshelf')
box(85,50,-62,86,57,-61,'bricks')
dwelling('east-residence',90,-30,13,13,40,'bricks')
bed(98,41,-26);box(101,41,-28,101,43,-25,'bookshelf');seat(95,41,-27)
put(95,41,-28,'dark_oak_slab',type='top',waterlogged='false')
dwelling('garden-residence',90,14,13,15,39,'stone_bricks')
bed(98,40,18);box(101,40,16,101,42,19,'bookshelf');seat(95,40,17)
for x in range(92,102):
 put(x,39,31,'moss_block')
 if x%3==0:put(x,40,31,'potted_fern')
dwelling('weaving-workshop',69,28,15,15,38,'deepslate_bricks')
put(80,39,31,'loom',facing='west');put(80,39,33,'stonecutter',facing='west')
for z in (39,41):put(80,39,z,'barrel',facing='west',open='false')
box(75,39,31,77,39,31,'dark_oak_slab',type='top',waterlogged='false');seat(75,39,32)

town_routes=[[(47,38,-24),(65,39,-24),(84,39,-24)],
 [(84,39,-24),(84,40,-40),(68,40,-40),(68,41,-56),(82,41,-56)],
 [(84,39,-24),(94,40,-24)],[(84,39,-24),(84,38,21),(94,39,21)],
 [(84,38,21),(65,38,21),(65,38,35),(74,38,35)],[(65,39,-24),(65,39,-30)]]
street={}
for path in town_routes:
 for a,c in zip(path,path[1:]):
  length=max(abs(c[0]-a[0]),abs(c[2]-a[2]));dx=(c[0]-a[0])//length;dz=(c[2]-a[2])//length
  for i in range(length+1):
   x,z=a[0]+dx*i,a[2]+dz*i;y=round(a[1]+(c[1]-a[1])*i/length)
   for offset in (-1,0,1):
    q=(x+(offset if dz else 0),z+(offset if dx else 0));street[q]=max(y,street.get(q,-99))
for (x,z),y in street.items():
 for h in range(y-3,y):put(x,h,z,'deepslate')
 put(x,y,z,'deepslate_tiles')
 for h in range(y+1,y+4):put(x,h,z,'air')
for (x,z),y in street.items():
 higher=next((name for dx,dz,name in [(1,0,'east'),(-1,0,'west'),(0,1,'south'),(0,-1,'north')] if street.get((x+dx,z+dz),y)==y+1),None)
 if higher:put(x,y+1,z,'deepslate_tile_stairs',facing=higher,half='bottom',shape='straight',waterlogged='false')

# A tiny covered public nook beside the lane, not a new rectangular town base.
for x,z in [(62,-35),(73,-35),(62,-27),(73,-27)]:box(x,39,z,x,44,z,'dark_oak_log',axis='y')
box(62,44,-35,73,44,-27,'dark_oak_slab',type='bottom',waterlogged='false')
for x in range(63,73):
 for z in range(-34,-27):put(x,45,z,'blue_wool' if x%4<2 else 'gray_wool')
for x,z in [(71,-33),(71,-31)]:put(x,40,z,'barrel',facing='up',open='false')
seat(63,40,-32);lamp(70,43,-30,True)
for x,y,z in [(68,43,-38),(82,42,-20),(82,41,19),(64,41,33)]:lamp(x,y,z)

original_rooms=[(32,58,18,44),(32,44,-20,-4),(50,63,-19,-4),(64,80,-11,7),(26,39,-43,-27),(52,65,-64,-47),(-59,-42,25,44)]
preserved=[p for p,v in base.items() if v['Name']!='minecraft:air' and any(a<=p[0]<=c and d<=p[2]<=e for a,c,d,e in original_rooms)]
assert all(b[p]==base[p] for p in preserved),'Original building block was overwritten'
original_windows={p:v for p,v in base.items() if v['Name']=='glimmeringabyss:warm_window'}
assert all(b[p]==v for p,v in original_windows.items())
origin=(-180,-8,-290);size=(292,88,364);palette=[];lookup={};rows=[]
for pos,state in sorted(b.items()):
 key=json.dumps(state,sort_keys=True)
 if key not in lookup:lookup[key]=len(palette);palette.append(state)
 assert all(0<=v-o<dim for v,o,dim in zip(pos,origin,size)),pos
 rows.append({'pos':[v-o for v,o in zip(pos,origin)],'state':lookup[key]})
packed=gzip.compress(b'\x0a\0\0'+payload({'DataVersion':3465,'author':'Mispla / Codex v1 sheltered settlement','size':list(size),'palette':palette,'blocks':rows,'entities':[]})[1],mtime=0)
(P/'src/main/resources/data/glimmeringabyss/structures/settlement_town.nbt').write_bytes(packed)
(P/'src/main/resources/data/glimmeringabyss/architecture/settlement_town.json').write_text(json.dumps(dict(layout=101,origin=origin,size=size,buildings=11,additions=buildings,street_routes=town_routes,blocks=len(rows),original_building_blocks_preserved=len(preserved),original_warm_windows_preserved=len(original_windows),warm_windows=sum(v['Name']=='glimmeringabyss:warm_window' for v in b.values()),template_sha256=hashlib.sha256(packed).hexdigest()),indent=2)+'\n')
print('Settlement:',len(rows),len(packed),'bytes; eleven buildings; original architectural blocks',len(preserved))
