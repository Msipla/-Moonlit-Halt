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
 box(x-1,floor+5,door,x-1,floor+h,door,'chain',axis='y',waterlogged='false')
 for zz in (z+3,z+d-4):
  box(x+w-1,floor+2,zz,x+w-1,floor+4,zz+1,'glimmeringabyss:warm_window')
  put(x+w-1,floor+5,zz,'glimmeringabyss:warm_window')
 for xx in (x+3,x+w-4):
  box(xx,floor+2,z,xx+1,floor+4,z,'glass_pane',east='true',west='true',north='false',south='false',waterlogged='false')
 lamp(x+w-3,floor+h-1,z+3,True)
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
 [(84,38,21),(67,38,21),(67,38,35),(74,38,35)],[(65,39,-24),(65,39,-30)]]
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
for x,y,z in [(68,43,-38),(82,42,-20),(82,41,19),(68,41,25)]:
 ground=38+(z<=-26)+(z<=-43)
 box(x,ground+1,z,x,y-1,z,'dark_oak_fence',east='false',west='false',north='false',south='false',waterlogged='false');lamp(x,y,z)

original_rooms=[(31,65,17,44),(32,44,-20,-4),(50,63,-19,-4),(64,80,-11,7),(26,39,-43,-27),(52,65,-64,-47),(-59,-42,25,44)]
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

# Layout 102 is one bounded finishing round. Keep the shipped 101 recipe above
# exact so an interrupted or existing preview.2 world never changes its recipe.
b=base.copy();buildings=[]
def finish_house(kind,x,z,w,d,floor,wall,rotate=False):
 global b
 saved=b;b={};dwelling(kind,0,0,w,d,floor,wall)
 if kind=='bakehouse':
  put(w-4,floor+1,4,'furnace',facing='west',lit='false');put(w-4,floor+1,2,'smoker',facing='west',lit='false')
  box(4,floor+1,3,6,floor+1,3,'dark_oak_slab',type='top',waterlogged='false');seat(4,floor+1,4)
  box(2,floor+1,2,2,floor+3,4,'bookshelf');box(w-4,floor+9,2,w-3,floor+15,3,'bricks')
 elif kind in ('east-residence','garden-residence'):
  bed(w-4,floor+1,4);box(w-2,floor+1,2,w-2,floor+3,5,'bookshelf');seat(4,floor+1,3)
  put(4,floor+1,2,'dark_oak_slab',type='top',waterlogged='false')
 elif kind=='weaving-workshop':
  put(w-4,floor+1,3,'loom',facing='west');put(w-4,floor+1,5,'stonecutter',facing='west')
  for zz in (d-4,d-2):put(w-4,floor+1,zz,'barrel',facing='west',open='false')
  box(4,floor+1,3,6,floor+1,3,'dark_oak_slab',type='top',waterlogged='false');seat(4,floor+1,4)
 cells=b;b=saved;record=buildings[-1]
 turn={'north':'east','east':'south','south':'west','west':'north'}
 for (xx,y,zz),state in cells.items():
  state=json.loads(json.dumps(state))
  if rotate:
   xx,zz=d-1-zz,xx
   props=state.get('Properties',{});old=props.copy()
   if old.get('facing') in turn:props['facing']=turn[old['facing']]
   if old.get('axis') in ('x','z'):props['axis']='z' if old['axis']=='x' else 'x'
   for direction in turn:
    if direction in old:props[turn[direction]]=old[direction]
  b[x+xx,y,z+zz]=state
 record.update(bounds=[x,z,x+(d if rotate else w)-1,z+(w if rotate else d)-1],door=[x+d-1-d//2,z] if rotate else [x,z+d//2],facing='north' if rotate else 'west')
finish_house('bakehouse',72,-92,13,15,40,'stone_bricks')
finish_house('east-residence',99,-45,11,13,40,'bricks')
finish_house('garden-residence',94,22,11,13,39,'stone_bricks',True)
finish_house('weaving-workshop',71,52,13,15,38,'deepslate_bricks',True)
# Short branching connections turn around living rock; no new all-town platform.
town_routes=[[(47,40,-78),(62,40,-78),(62,40,-72),(69,40,-72),(69,40,-85),(78,40,-85)],
 [(47,38,-24),(65,39,-24),(86,39,-24),(86,39,-32),(94,39,-32),(94,40,-39),(103,40,-39)],
 [(86,39,-24),(86,39,-14),(83,39,-14),(83,38,19),(100,38,19),(100,39,21),(100,39,26)],
 [(83,38,19),(83,38,42),(78,38,42),(78,38,58)],[(65,39,-24),(65,39,-31)]]
street={}
for path in town_routes:
 for a,c in zip(path,path[1:]):
  length=max(abs(c[0]-a[0]),abs(c[2]-a[2]));dx=(c[0]-a[0])//length;dz=(c[2]-a[2])//length
  assert dx==0 or dz==0
  for i in range(length+1):
   x,z=a[0]+dx*i,a[2]+dz*i;y=round(a[1]+(c[1]-a[1])*i/length)
   for offset in (-1,0,1):
    q=(x+(offset if dz else 0),z+(offset if dx else 0));street[q]=max(y,street.get(q,-99))
for (x,z),y in street.items():
 for h in range(y-3,y):put(x,h,z,'deepslate')
 put(x,y,z,'deepslate_tiles' if (x+2*z)%11<3 else 'cobbled_deepslate')
 for h in range(y+1,y+4):put(x,h,z,'air')
for (x,z),y in street.items():
 higher=next((name for dx,dz,name in [(1,0,'east'),(-1,0,'west'),(0,1,'south'),(0,-1,'north')] if street.get((x+dx,z+dz),y)==y+1),None)
 if higher:put(x,y+1,z,'cobbled_deepslate_stairs',facing=higher,half='bottom',shape='straight',waterlogged='false')
# Retain the existing public function in a smaller footprint, without more props.
for x,z in [(62,-35),(70,-35),(62,-29),(70,-29)]:box(x,39,z,x,44,z,'dark_oak_log',axis='y')
box(62,44,-35,70,44,-29,'dark_oak_slab',type='bottom',waterlogged='false')
for x in range(63,70):
 for z in range(-34,-29):put(x,45,z,'blue_wool' if x%4<2 else 'gray_wool')
for x,z in [(69,-33),(69,-31)]:put(x,40,z,'barrel',facing='up',open='false')
seat(63,40,-32);lamp(68,43,-30,True)
for x,y,z in [(67,44,-73),(92,43,-29),(81,42,20),(79,42,45)]:
 ground=38+(z<=-26)+(z<=-43)
 box(x,ground+1,z,x,y-1,z,'dark_oak_fence',east='false',west='false',north='false',south='false',waterlogged='false');lamp(x,y,z)
assert all(b[p]==base[p] for p in preserved),'Original building block was overwritten in finishing round'
assert all(b[p]==v for p,v in original_windows.items())
# Footprints retain natural stone between additions. Verify procedural natural shelf
# attachment, rather than merely claim that a small foundation is sufficient.
footprint_area=0;supports=[]
for house in buildings:
 x1,z1,x2,z2=house['bounds'];area=(x2-x1+1)*(z2-z1+1);footprint_area+=area;grounded=0
 for x in range(x1,x2+1):
  for z in range(z1,z2+1):
   top=38+(z<=-26)+(z<=-43);depth=max(0,top-(house['floor']-3))
   grounded+=((x-62-4*math.sin(z*.035))/(59+depth*.27))**2+((z+11+5*math.sin(x*.03))/(90+depth*.19))**2<1
 assert grounded/area>.85,(house,grounded,area)
 supports.append(dict(kind=house['kind'],natural_shelf_cells=grounded,footprint_cells=area))
assert footprint_area<844
assert all(p[0]<=111 for p in b.keys()-base.keys()),'Addition exceeds the covered town-side ceiling boundary'
origin=(-180,-8,-290);size=(292,88,364);palette=[];lookup={};rows=[]
for pos,state in sorted(b.items()):
 key=json.dumps(state,sort_keys=True)
 if key not in lookup:lookup[key]=len(palette);palette.append(state)
 assert all(0<=v-o<dim for v,o,dim in zip(pos,origin,size)),pos
 rows.append({'pos':[v-o for v,o in zip(pos,origin)],'state':lookup[key]})
packed=gzip.compress(b'\x0a\0\0'+payload({'DataVersion':3465,'author':'Mispla / Codex v1 limited settlement finish','size':list(size),'palette':palette,'blocks':rows,'entities':[]})[1],mtime=0)
(P/'src/main/resources/data/glimmeringabyss/structures/settlement_finish.nbt').write_bytes(packed)
(P/'src/main/resources/data/glimmeringabyss/architecture/settlement_finish.json').write_text(json.dumps(dict(layout=102,origin=origin,size=size,buildings=11,additions=buildings,street_routes=town_routes,blocks=len(rows),original_building_blocks_preserved=len(preserved),original_warm_windows_preserved=len(original_windows),warm_windows=sum(v['Name']=='glimmeringabyss:warm_window' for v in b.values()),new_house_footprint_cells=footprint_area,previous_new_house_footprint_cells=844,natural_shelf_attachment=supports,template_sha256=hashlib.sha256(packed).hexdigest()),indent=2)+'\n')
print('Finishing round:',len(rows),len(packed),'bytes; new house footprints',footprint_area,'vs 844; original blocks',len(preserved),'natural shelf attachment',supports)

# Layout 103 completes the existing rooms and makes the existing loop legible.
# Keep every shipped recipe above reproducible for old and interrupted worlds.
accepted=b.copy();entities={};room_changes={};current_room=''
# The old slate-house bay trim blocks standing headroom over its south door.
# Open only those four trim cells; warm windows and the bay silhouette remain.
door_repairs={(56,y,z) for y in (40,41) for z in (-6,-5)}
for p in door_repairs:
 assert b[p]['Name']=='minecraft:polished_deepslate',('Unexpected door obstruction',p,b[p])
 put(*p,'air')
# During the real client walk the upper floor catches the player's head while
# stepping off stair z24. Extend the existing opening by exactly one floor cell.
stair_repairs={(53,45,24)}
for p in stair_repairs:
 assert b[p]['Name']=='minecraft:polished_blackstone_bricks',(p,b[p])
 put(*p,'air')
guards={(x,z) for x in (52,54) for z in range(23,28)}|{(53,23)}
for x,z in guards:
 assert b[x,46,z]['Name']=='minecraft:air',(x,z,b[x,46,z])
 arms={name:str((x+dx,z+dz) in guards).lower() for dx,dz,name in [(1,0,'east'),(-1,0,'west'),(0,1,'south'),(0,-1,'north')]}
 put(x,46,z,'dark_oak_fence',waterlogged='false',**arms)
for i in range(7):
 assert all(b[53,y,21+i]['Name']=='minecraft:air' for y in range(40+i,43+i)),('Workshop stair headroom',i)
def furnish(x,y,z,name,**props):
 old=b.get((x,y,z),{}).get('Name')
 assert old=='minecraft:air',(current_room,(x,y,z),old,name)
 put(x,y,z,name,**props);room_changes[current_room]=room_changes.get(current_room,0)+1
def furniture_box(x1,y1,z1,x2,y2,z2,name,**props):
 for xx in range(x1,x2+1):
  for zz in range(z1,z2+1):
   for yy in range(y1,y2+1):furnish(xx,yy,zz,name,**props)
def chair(x,y,z,facing):furnish(x,y,z,'dark_oak_stairs',facing=facing,half='bottom',shape='straight',waterlogged='false')
def desk(x1,y,z1,x2,z2):furniture_box(x1,y,z1,x2,y,z2,'dark_oak_slab',type='top',waterlogged='false')
def storage(x,y,z,facing='up'):furnish(x,y,z,'barrel',facing=facing,open='false')

# The large original workshop: material/work bays and an upper drafting room.
current_room='original-workshop'
desk(35,39,26,37,28);furnish(35,39,30,'grindstone',face='floor',facing='east')
furnish(37,39,30,'anvil',facing='north');furnish(35,39,32,'stonecutter',facing='east')
for zz in (32,34,36):storage(54,39,zz,'west')
desk(46,39,29,49,30);chair(47,39,32,'north');storage(49,39,32)
furniture_box(35,46,22,35,48,27,'bookshelf')
furnish(38,46,24,'cartography_table');desk(39,46,24,42,24)
chair(40,46,26,'north');furnish(46,46,35,'lectern',facing='north',has_book='false',powered='false')
for xx in range(45,49):storage(xx,46,22)
# Stair opening is retained; no cabinet closes the entry, stairs or upper landing.

current_room='brick-residence'
desk(35,39,-9,36,-9);furnish(35,39,-7,'cauldron');storage(41,39,-17)
desk(40,39,-11,41,-11);chair(41,39,-10,'north')
furnish(35,40,-9,'potted_fern')
furniture_box(41,45,-16,41,46,-14,'bookshelf')
for xx in (38,39):furnish(xx,45,-13,'blue_carpet')
storage(35,45,-8);chair(40,45,-8,'west')

# The slate residence has a bookbinding work corner and a different sitting plan.
current_room='slate-residence'
furniture_box(60,39,-16,60,41,-13,'bookshelf');desk(57,39,-11,59,-11)
furnish(60,39,-9,'cartography_table');chair(58,39,-10,'north')
storage(54,39,-9);furnish(54,40,-9,'potted_azalea_bush')
desk(59,45,-9,60,-9);chair(59,45,-10,'south');storage(60,45,-15)

current_room='store-shop'
desk(75,39,0,76,0)
for zz in (-5,-3,-1):storage(66,39,zz,'east');storage(66,40,zz,'east')
furniture_box(68,39,-5,70,40,-5,'bookshelf')
storage(75,39,3);furnish(68,39,0,'composter');chair(76,39,2,'north')

current_room='tea-room'
desk(34,40,-32,35,-32);storage(36,40,-40);storage(36,41,-40)
desk(32,40,-39,33,-39);chair(32,40,-38,'north')
furnish(35,41,-32,'potted_fern')

current_room='clock-reading-room'
furniture_box(56,41,-61,57,43,-61,'bookshelf');desk(58,41,-56,59,-56)
chair(58,41,-55,'north');furnish(61,41,-59,'crafting_table');storage(61,41,-54)

current_room='library-refuge'
desk(-50,39,32,-48,33);chair(-51,39,32,'east');chair(-47,39,33,'west')
furnish(-49,40,32,'lantern',hanging='false',waterlogged='false')
storage(-54,39,41);furnish(-54,40,41,'potted_fern')
furnish(-51,39,29,'lectern',facing='south',has_book='false',powered='false')

# The four newer interiors keep their current walls, footprint and entry direction.
current_room='bakehouse'
desk(75,41,-81,77,-81);storage(82,41,-83);storage(82,42,-83)
furnish(83,41,-86,'composter');desk(79,41,-80,81,-80)
chair(79,41,-82,'south');furnish(76,42,-81,'potted_fern')

current_room='east-residence'
desk(101,41,-34,103,-34);storage(100,41,-43);storage(100,42,-43)
furniture_box(107,41,-34,107,42,-34,'bookshelf');chair(104,41,-35,'south')
for zz in (-36,-35):furnish(105,41,zz,'blue_carpet')

current_room='garden-residence'
desk(95,40,24,95,27);furnish(95,41,24,'potted_fern');storage(95,40,30)
desk(98,40,24,99,24);chair(98,40,25,'north')
for zz in (27,28,29):furnish(98,40,zz,'green_carpet')

current_room='weaving-workshop'
furnish(82,39,59,'loom',facing='west');desk(74,39,57,76,57)
storage(73,39,58);furnish(75,39,54,'cauldron')
furnish(73,39,54,'white_wool');furnish(73,40,54,'light_gray_wool')
chair(75,39,58,'north');storage(84,39,54,'west')

# One stopping bay on the already traversed west ledge, not a new region.
# Survey furniture and a sightline make the far end a recognisable destination.
bay={(x,z) for x in range(-154,-145) for z in range(-187,-179)}
for x,z in bay:
 for yy in range(15,21):put(x,yy,z,'deepslate' if yy<20 else 'cobbled_deepslate')
 for yy in range(21,27):put(x,yy,z,'air')
# Only perimeter segments outside the existing walk centerline have a parapet.
rim={(x,z) for x,z in bay if any((x+dx,z+dz) not in bay for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)])
     and not (z==-184 or x==-151)}
for x,z in rim:
 arms={name:('low' if (x+dx,z+dz) in rim else 'none') for dx,dz,name in [(0,-1,'north'),(0,1,'south'),(1,0,'east'),(-1,0,'west')]}
 put(x,21,z,'cobbled_deepslate_wall',up='true',waterlogged='false',**arms)
put(-148,21,-182,'cartography_table');put(-147,21,-182,'barrel',facing='up',open='false')
put(-149,21,-181,'dark_oak_stairs',facing='north',half='bottom',shape='straight',waterlogged='false')
put(-148,22,-182,'lantern',hanging='false',waterlogged='false')
def sign(x,y,z,rotation,lines):
 put(x,y,z,'spruce_sign',rotation=rotation,waterlogged='false')
 text={'messages':[json.dumps({'text':line},ensure_ascii=False) for line in lines],
       'color':'black','has_glowing_text':0}
 entities[x,y,z]={'id':'minecraft:sign','front_text':text,'back_text':text,'is_waxed':1}
sign(48,41,-72,0,['岩廊环线','断层观测台','沿灯火下行','回镇走东侧坡道'])
sign(-75,28,-125,12,['西：断层观测台','北：下层岩廊','东：小镇','环线可返回'])
sign(-149,21,-185,12,['断层观测台','岩层在此交错','沿岩廊望向远端','循灯可到下层尽端'])
sign(-150,17,-211,8,['下层尽端观景','此处为环线远端','东行接回程上坡','灯火通向小镇'])
sign(-65,28,-125,12,['已接回镇路','沿东向岩坡上行','前方小镇与钟塔','返程可在此歇脚'])
# All old architectural solids are retained. Only empty room cells and the
# explicitly bounded existing observation bay/route hints changed.
for p,v in accepted.items():
 if v['Name']!='minecraft:air' and not (p[0],p[2]) in bay and p not in entities and p not in door_repairs|stair_repairs:
  assert b[p]==v,('Accepted solid changed',p)
assert all(b[p]==v for p,v in original_windows.items())
assert len(room_changes)==11
for x,y,z in [(42,39,39),(38,39,-6),(56,39,-7),(72,39,5),(32,40,-29),
              (58,41,-52),(-43,39,35),(72,41,-85),(99,41,-39),(100,40,22),(78,39,52)]:
 assert all(b[x,yy,z]['Name']=='minecraft:air' for yy in (y,y+1)),('Entry blocked',x,y,z)
origin=(-180,-8,-290);size=(292,88,364);palette=[];lookup={};rows=[]
for pos,state in sorted(b.items()):
 key=json.dumps(state,sort_keys=True)
 if key not in lookup:lookup[key]=len(palette);palette.append(state)
 assert all(0<=v-o<dim for v,o,dim in zip(pos,origin,size)),pos
 row={'pos':[v-o for v,o in zip(pos,origin)],'state':lookup[key]}
 if pos in entities:row['nbt']=entities[pos]
 rows.append(row)
packed=gzip.compress(b'\x0a\0\0'+payload({'DataVersion':3465,'author':'Mispla / Codex v1 Android feedback finish','size':list(size),'palette':palette,'blocks':rows,'entities':[]})[1],mtime=0)
(P/'src/main/resources/data/glimmeringabyss/structures/settlement_acceptance.nbt').write_bytes(packed)
(P/'src/main/resources/data/glimmeringabyss/architecture/settlement_acceptance.json').write_text(json.dumps(dict(
 layout=103,origin=origin,size=size,buildings=11,additions=buildings,street_routes=town_routes,
 route=route,exploration_nodes=[{'name':'fault-observation-bay','bounds':[-154,-187,-146,-180],'floor':20},
 {'name':'lower-gallery-terminus','position':[-151,16,-214]},{'name':'homeward-junction','position':[-66,27,-124]}],
 room_new_furnishings=room_changes,door_headroom_repairs=[list(p) for p in sorted(door_repairs)],stair_headroom_repairs=[list(p) for p in sorted(stair_repairs)],stair_guard_cells=len(guards),route_signs=[{'position':list(p),'text':[json.loads(t)['text'] for t in n['front_text']['messages']]} for p,n in entities.items()],
 blocks=len(rows),warm_windows=sum(v['Name']=='glimmeringabyss:warm_window' for v in b.values()),
 new_house_footprint_cells=footprint_area,historical_templates_unchanged=True,
 template_sha256=hashlib.sha256(packed).hexdigest()),indent=2,ensure_ascii=False)+'\n')
print('Android feedback finish:',len(rows),len(packed),'bytes;',room_changes,'route signs',len(entities))
