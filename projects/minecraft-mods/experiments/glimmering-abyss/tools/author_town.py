"""Original compact Phase 6 town, local shelf break, no natural generation."""
from pathlib import Path
import gzip, hashlib, json, math, runpy
P=Path(__file__).resolve().parents[1]
a=runpy.run_path(str(P/'tools/author_route.py'))
b,put,box,rail=(a[k] for k in ('b','put','box','rail'))
route_blocks=b.copy()
original_windows={p:s for p,s in b.items() if s['Name']=='glimmeringabyss:warm_window'}
pillars=[(-48,-43,11),(-24,-92,10),(36,-78,12),(69,-32,16),(-72,10,15),(12,7,10),(-5,-38,9)]
def protected(x,z):return any((x-px)**2+(z-pz)**2<=r*r for px,pz,r in pillars)
# A wide open cleft rather than an ellipse/funnel. Existing pillars stay intact.
# Its distant northern limit meets existing rock, outside the principal downward view.
columns=0
for x in range(-55,29):
 for z in range(-106,61):
  edge=22+int(2*math.sin(z/13)+1.5*math.sin(z/5))
  if x>=edge or protected(x,z):continue
  columns+=1
  box(x,-62,z,x,37,z,'air');put(x,-63,z,'black_concrete')
# Jagged, load-bearing rock shelf. Surface changes by only two blocks.
for z in range(-65,47):
 edge=22+int(2*math.sin(z/13)+1.5*math.sin(z/5))
 top=38+(z<=-26)+(z<=-43)
 for x in range(edge,84):
  if protected(x,z):continue
  for y in range(-62,top+1):
   # Small strata overhangs articulate the break, no round visible pit rim.
   if x<edge+2 and y<-12 and y%17<4:continue
   put(x,y,z,'deepslate' if y%18>2 else 'cobbled_deepslate')
  # Clear the inhabited volume; the cavern ceiling and giant pillar are kept.
  box(x,top+1,z,x,78,z,'air')
# Reapply the exact Phase 5 workshop/bridge. Broad geology never eats the workshop.
for p,s in route_blocks.items():
 if p[0]>=29 or p[2]>=27:b[p]=s
# The broad floor is now continuous below the original platform, so it is a shelf.
# Streets: a short main street, one workshop-side alley and a tiny plaza.
for z in range(-60,20):
 y=38+(z<=-26)+(z<=-43)
 for x in range(44,50):put(x,y,z,'polished_deepslate' if x in (44,49) else 'deepslate_tiles')
for x in range(26,66):
 for z in range(-2,6):put(x,38,z,'polished_deepslate' if z in (-2,5) else 'deepslate_tiles')
for x in range(29,33):
 for z in range(5,44):put(x,38,z,'deepslate_tiles')
# Open the former north platform rail into the short alley.
box(29,39,15,32,42,15,'air')
# Ground and stairs follow small shelf offsets, never requiring parkour.
for z,y in [(-26,39),(-43,40)]:
 for x in range(44,50):put(x,y,z,'deepslate_tile_stairs',facing='north',half='bottom',shape='straight',waterlogged='false')
def bars(x,y,z,axis='x'):
 put(x,y,z,'iron_bars',north=str(axis=='z').lower(),south=str(axis=='z').lower(),east=str(axis=='x').lower(),west=str(axis=='x').lower(),waterlogged='false')
def window(cx,y,z,lit=True):
 for x in range(cx-1,cx+2):
  for yy in range(y,y+3):put(x,yy,z,'glimmeringabyss:warm_window' if lit else 'polished_blackstone')
 put(cx,y+3,z,'glimmeringabyss:warm_window' if lit else 'polished_blackstone')
 for x in (cx-2,cx+2):box(x,y-1,z,x,y+2,z,'polished_deepslate')
 for x in range(cx-2,cx+3):put(x,y-1,z,'polished_blackstone_brick_slab',type='top',waterlogged='false')
def house(x,z,w,d,ground,wall,upper=True,steep=1):
 y=ground;h=12 if upper else 8
 box(x-1,y-1,z-1,x+w,y-1,z+d,'polished_deepslate')
 box(x,y,z,x+w-1,y+h,z+d-1,wall);box(x+1,y,z+1,x+w-2,y+h-1,z+d-2,'air')
 box(x+1,y-1,z+1,x+w-2,y-1,z+d-2,'dark_oak_planks')
 if upper:box(x+1,y+5,z+1,x+w-2,y+5,z+d-2,'dark_oak_planks')
 for xx in [x,x+w-1]:box(xx,y,z+d,xx,y+h,z+d,'polished_deepslate')
 for yy in [y+5,y+h]:box(x-1,yy,z+d,x+w,yy,z+d,'polished_blackstone_brick_slab',type='top',waterlogged='false')
 for xx in range(x-1,x+w+1):
  height=y+h+1+min(xx-(x-1),x+w-xx)//steep
  for zz in range(z-1,z+d+1):put(xx,height,zz,'deepslate_tile_stairs',facing='east' if xx<x+w/2 else 'west',half='bottom',shape='straight',waterlogged='false')
  if x<=xx<x+w:
   for zz in (z,z+d-1):box(xx,y+h+1,zz,xx,height-1,zz,wall)
 door=x+w//2
 box(door,y,z+d-1,door,y+2,z+d,'air')
 for cx in [x+2,x+w-3]:
  window(cx,y+1,z+d-1,cx==x+2)
  if upper:window(cx,y+7,z+d-1,cx==x+w-3)
 # Restore the door if window trim reaches a narrow facade's center.
 box(door,y,z+d-1,door,y+2,z+d,'air')
 put(door,y+3,z+d,'lantern',hanging='true',waterlogged='false')
 box(x+2,y+h+2,z+3,x+3,y+h+7,z+4,'bricks')
 box(x+1,y+h+8,z+2,x+4,y+h+8,z+5,'polished_blackstone_brick_slab',type='bottom',waterlogged='false')
 for xx,zz,block in [(x+2,z+2,'crafting_table'),(x+w-3,z+2,'barrel')]:
  put(xx,y,zz,block,**({'facing':'up','open':'false'} if block=='barrel' else {}))
 put(x+2,y,z+d-3,'lantern',hanging='false',waterlogged='false')
 return door
# Five additions, with different silhouettes, materials and height.
house(33,-19,10,14,39,'bricks',True,1)           # narrow brick residence
house(51,-18,11,12,39,'deepslate_bricks',True,1) # tall slate townhouse
house(65,-10,14,16,39,'bricks',False,2)          # low storehouse
house(27,-42,11,14,40,'stone_bricks',False,1)    # quiet tea room
# Small bay window on townhouse, facing the plaza, unlike the industrial workshop.
box(53,40,-6,56,43,-5,'polished_deepslate')
for x in range(54,56):
 for y in range(40,43):put(x,y,-5,'glimmeringabyss:warm_window')
box(53,44,-6,56,44,-5,'deepslate_tile_slab',type='bottom',waterlogged='false')
# Clock / observation tower on the northern upper terrace. A real vanilla ladder
# reaches a guarded open balcony; the timepiece is a restrained static block face.
box(53,40,-63,63,60,-52,'deepslate_bricks');box(54,41,-62,62,59,-53,'air')
box(54,40,-62,62,40,-53,'dark_oak_planks')
box(57,41,-52,59,44,-52,'air')
window(56,45,-52,True);window(61,45,-52,False)
for y in [41,50]:
 for z in (-64,-51):box(52,y,z,64,y,z,'polished_blackstone_brick_slab',type='top',waterlogged='false')
 for x in (52,64):box(x,y,-64,x,y,-51,'polished_blackstone_brick_slab',type='top',waterlogged='false')
box(57,41,-52,59,44,-52,'air')
# Opening roof, safe perimeter, balcony at y=59.
box(52,58,-64,64,58,-51,'deepslate_tiles');box(53,59,-63,63,62,-52,'air')
for x in range(52,65):
 for z in (-64,-51):
  put(x,59,z,'cobbled_deepslate_wall',up='true',north='none',south='none',east='tall',west='tall',waterlogged='false')
for z in range(-63,-51):
 for x in (52,64):
  put(x,59,z,'cobbled_deepslate_wall',up='true',north='tall',south='tall',east='none',west='none',waterlogged='false')
box(53,41,-58,53,60,-58,'deepslate_bricks')
for y in range(41,60):put(54,y,-58,'ladder',facing='east',waterlogged='false')
# Ladder passes through the balcony opening; move east at y=59 to step off.
box(57,59,-57,61,70,-53,'polished_blackstone_bricks')
for y in range(63,68):
 for x in range(58,61):put(x,y,-52,'calcite')
for x,y in [(59,65),(59,66),(60,65)]:put(x,y,-52,'polished_blackstone')
for level in range(4):box(56+level,71+level,-58+level,62-level,71+level,-52-level,'deepslate_tiles')
# Restore accessible upper terrace/door to the high viewpoint.
for x in range(49,60):
 for z in range(-51,-47):put(x,39,z,'deepslate_tiles');box(x,40,z,x,44,z,'air')
for x in range(57,60):put(x,40,-51,'deepslate_tile_stairs',facing='north',half='bottom',shape='straight',waterlogged='false')
# Streetside life traces: benches, goods, two trees in planters, a small dry basin.
for x,z in [(35,3),(58,3),(46,-23),(46,-40)]:
 box(x,39+(z<=-26),z,x,42+(z<=-26),z,'polished_blackstone_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
 put(x,43+(z<=-26),z,'lantern',hanging='false',waterlogged='false')
# Lamps on street margins, not in the route's central walking lane.
for x,z in [(46,-23),(46,-40)]:
 for y in range(39,46):b.pop((x,y,z),None)
 x=49;y=39+(z<=-26)
 box(x,y,z,x,y+3,z,'polished_blackstone_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false');put(x,y+4,z,'lantern',hanging='false',waterlogged='false')
for x,z in [(60,10),(77,-17)]:
 box(x-1,38,z-1,x+1,39,z+1,'bricks');put(x,40,z,'azalea');box(x-1,42,z-1,x+1,43,z+1,'azalea_leaves',persistent='true',waterlogged='false');box(x,40,z,x,42,z,'oak_log',axis='y')
for x,z in [(36,7),(56,7)]:
 for xx in range(x,x+3):put(xx,39,z,'dark_oak_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
for x,z in [(69,8),(71,8),(74,8)]:put(x,39,z,'barrel',facing='up',open='false')
# One low parapet lookout lets a standing player see down without double iron bars.
box(19,37,-17,25,38,-5,'deepslate_bricks');box(20,39,-16,24,43,-6,'air')
for z in range(-17,-4):put(19,39,z,'cobbled_deepslate_wall',up='true',north='tall',south='tall',east='none',west='none',waterlogged='false')
for x in range(20,26):
 for z in (-17,-5):put(x,39,z,'cobbled_deepslate_wall',up='true',north='none',south='none',east='tall',west='tall',waterlogged='false')
# Low, 1.5-block collision parapets protect the accessible shelf perimeter.
# Keep the old bridge and southern research walkway gates open.
for z in range(-65,47):
 edge=22+int(2*math.sin(z/13)+1.5*math.sin(z/5));y=39+(z<=-26)+(z<=-43)
 if not (33<=z<=37 or -16<=z<=-6):put(edge,y,z,'cobbled_deepslate_wall',up='true',north='tall',south='tall',east='none',west='none',waterlogged='false')
 put(83,y,z,'cobbled_deepslate_wall',up='true',north='tall',south='tall',east='none',west='none',waterlogged='false')
for z,y in [(-65,41),(46,39)]:
 edge=22+int(2*math.sin(z/13)+1.5*math.sin(z/5))
 for x in range(edge,84):
  if z==46 and 35<=x<=41:continue
  put(x,y,z,'cobbled_deepslate_wall',up='true',north='none',south='none',east='tall',west='tall',waterlogged='false')
# A four-block low masonry viewing bay in the otherwise retained iron railing.
# Vanilla wall collision is 1.5 blocks high; standing eyes can look over its top.
for x in range(3,7):
 put(x,39,32,'polished_blackstone_wall',up='true',north='none',south='none',east='tall',west='tall',waterlogged='false');put(x,40,32,'air')
# Preserve all accepted workshop windows exactly.
for p,s in original_windows.items():b[p]=s
assert all(b[p]==s for p,s in original_windows.items())
origin=(-55,-63,-106);size=(139,143,180)
palette=[];lookup={};blocks=[]
for pos,s in sorted(b.items()):
 key=json.dumps(s,sort_keys=True)
 if key not in lookup:lookup[key]=len(palette);palette.append(s)
 blocks.append({'pos':[v-o for v,o in zip(pos,origin)],'state':lookup[key]})
nbt={'DataVersion':3465,'author':'Mispla / Codex original Phase 6 town','size':list(size),'palette':palette,'blocks':blocks,'entities':[]}
packed=gzip.compress(b'\x0a\0\0'+a['a']['payload'](nbt)[1],mtime=0)
(P/'src/main/resources/data/glimmeringabyss/structures/town.nbt').write_bytes(packed)
manifest=dict(schema=1,template='glimmeringabyss:town',origin=origin,size=size,blocks=len(blocks),template_sha256=hashlib.sha256(packed).hexdigest(),entities=0,buildings=6,additional_buildings=5,town_footprint=[22,83,-65,46],walk_surface_y=[39,40,41],preserved_workshop_windows=len(original_windows),warm_windows=sum(s['Name']=='glimmeringabyss:warm_window' for s in b.values()),excavated_columns=columns,finite_floor_y=-63,authoring='Original vanilla-block compact town and explicit local shelf break; generator and historical templates unchanged')
(P/'src/main/resources/data/glimmeringabyss/architecture/town.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Phase 6:',len(blocks),'explicit positions;',len(packed),'bytes;',manifest['warm_windows'],'warm windows')
