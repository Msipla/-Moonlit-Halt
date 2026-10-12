from pathlib import Path
import json,gzip,struct,hashlib
P=Path(__file__).resolve().parents[1]
# Original authored structure; no external models/textures or schematic dependencies.
b={}
def state(name,**props):return {'Name':name if ':' in name else 'minecraft:'+name,**({'Properties':{k:str(v) for k,v in props.items()}} if props else {})}
def put(x,y,z,name,**props):b[(x,y,z)]=state(name,**props)
def box(x1,y1,z1,x2,y2,z2,name,**props):
 for x in range(x1,x2+1):
  for z in range(z1,z2+1):
   for y in range(y1,y2+1):put(x,y,z,name,**props)
def rail(x1,z1,x2,z2):
 for x in range(x1,x2+1):
  for z in range(z1,z2+1):
   for y in [39,40]:put(x,y,z,'iron_bars',north=str(z1!=z2).lower(),south=str(z1!=z2).lower(),east=str(x1!=x2).lower(),west=str(x1!=x2).lower(),waterlogged='false')
# Terrace, structural piers and brackets, no floating pavement.
box(29,36,15,67,37,46,'deepslate_bricks');box(29,38,15,67,38,46,'polished_deepslate')
for x in [31,64]:
 for z in [18,43]:
  box(x-1,-34,z-1,x+1,35,z+1,'deepslate_tiles')
  for y in [12,26,34]:box(x-2,y,z-2,x+2,y,z+2,'polished_deepslate')
rail(29,15,67,15);rail(67,15,67,46);rail(29,15,29,46);rail(29,46,34,46);rail(42,46,67,46)
# Five block walkway, linked at existing side rail only. Support beams under it.
box(35,37,47,41,38,72,'polished_deepslate');box(22,37,68,34,38,72,'polished_deepslate')
box(35,39,46,41,42,72,'air');box(22,39,69,34,42,71,'air')
rail(35,47,35,67);rail(41,47,41,72);rail(22,68,34,68);rail(22,72,40,72)
for z in [49,59,69]:box(34,34,z,42,36,z,'deepslate_tile_wall',up='true',north='none',south='none',east='tall',west='tall',waterlogged='false')
# Two storey rectangular masonry workshop. Gable faces observer (south).
box(33,39,19,57,53,39,'deepslate_bricks');box(34,39,20,56,52,38,'air')
box(34,38,20,56,38,38,'dark_oak_planks');box(34,45,20,56,45,38,'dark_oak_planks')
# Cornice and restrained vertical buttresses.
for y in [39,45,53]:
 box(32,y,18,58,y,40,'polished_blackstone_bricks')
 # restore interior void / floor after cornice
 if y in [39,53]:box(34,y,20,56,y,38,'air')
for x in [33,39,45,51,57]:
 box(x,40,40,x,52,40,'polished_deepslate');put(x,53,40,'deepslate_tile_slab',type='bottom',waterlogged='false')
# Original lit-window solid block supports native lighting, no transparent shader dependency.
def arch(center,y,z,lit=True,side=False):
 for dx in [-1,0,1]:
  for dy in range(4):
   if side:put(z,y+dy,center+dx,'glimmeringabyss:warm_window' if lit else 'black_stained_glass')
   else:put(center+dx,y+dy,z,'glimmeringabyss:warm_window' if lit else 'polished_blackstone')
 # narrowing arched head with staircase springers, half block reveals
 if side:
  put(z,y+4,center,'glimmeringabyss:warm_window' if lit else 'polished_blackstone');put(z,y+4,center-1,'polished_blackstone_brick_stairs',facing='south',half='top',shape='straight',waterlogged='false');put(z,y+4,center+1,'polished_blackstone_brick_stairs',facing='north',half='top',shape='straight',waterlogged='false')
 else:
  put(center,y+4,z,'glimmeringabyss:warm_window' if lit else 'polished_blackstone');put(center-1,y+4,z,'polished_blackstone_brick_stairs',facing='east',half='top',shape='straight',waterlogged='false');put(center+1,y+4,z,'polished_blackstone_brick_stairs',facing='west',half='top',shape='straight',waterlogged='false')
 for dx in range(-2,3):
  if side:put(z,y-1,center+dx,'polished_blackstone_brick_slab',type='bottom',waterlogged='false')
  else:put(center+dx,y-1,z,'polished_blackstone_brick_slab',type='bottom',waterlogged='false')
for x in [36,48,54]:
 for y in [40,47]:arch(x,y,39,lit=(x==36 and y==40) or (x==48 and y==47))
for z in [23,29,35]:
 for y in [40,47]:arch(z,y,33,lit=(z==29 and y==47),side=True)
# Main entry: open arch and porch, preserving walkable passage.
box(41,39,39,43,42,40,'air');put(42,43,39,'air');put(41,43,39,'polished_blackstone_brick_stairs',facing='east',half='top',shape='straight',waterlogged='false');put(43,43,39,'polished_blackstone_brick_stairs',facing='west',half='top',shape='straight',waterlogged='false')
box(40,38,40,44,38,43,'polished_blackstone_bricks');box(40,44,40,44,44,43,'deepslate_tile_slab',type='bottom',waterlogged='false')
for x in [40,44]:box(x,39,42,x,43,42,'polished_blackstone_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
# Long pitched roof: stepped slabs/stairs + filled front/back gables.
for x in range(31,60):
 d=min(x-31,59-x);h=54+d//2
 for z in range(17,42):put(x,h,z,'deepslate_tile_stairs',facing='east' if x<45 else 'west',half='bottom',shape='straight',waterlogged='false')
 if 33<=x<=57:
  for z in [19,39]:box(x,54,z,x,h-1,z,'deepslate_bricks')
box(44,61,17,46,61,41,'deepslate_tile_slab',type='bottom',waterlogged='false')
arch(45,54,39,lit=False)
# Two capped chimneys, original vanilla campfires create restrained real smoke.
for x,z in [(37,23),(53,31)]:
 box(x,54,z,x+1,64,z+1,'polished_blackstone_bricks');box(x-1,65,z-1,x+2,65,z+2,'polished_blackstone_brick_slab',type='bottom',waterlogged='false')
 put(x,65,z,'campfire',facing='north',lit='true',signal_fire='false',waterlogged='false');put(x+1,65,z,'iron_bars',north='true',south='true',east='true',west='true',waterlogged='false')
# Tower at south-east corner, lower portion attached, complete silhouette and spire.
box(57,39,30,64,65,39,'polished_blackstone_bricks');box(58,39,31,63,64,38,'air')
for x in [57,64]:
 for z in [30,39]:box(x,39,z,x,66,z,'polished_deepslate')
for y in [45,53,61,66]:box(56,y,29,65,y,40,'polished_blackstone_brick_slab',type='bottom',waterlogged='false')
for y in [47,55]:arch(60,y,39,lit=y==55)
# Bell/louver level, no animated NPCs. Openings and iron balustrade.
for x in [59,60,61,62]:box(x,62,39,x,64,39,'iron_bars',north='false',south='false',east='true',west='true',waterlogged='false')
for level in range(5):
 lo=56+level;hi=65-level;y=67+level
 box(lo,y,29+level,hi,y,40-level,'deepslate_tiles')
box(60,72,34,61,73,35,'polished_blackstone');put(60,74,34,'iron_bars',north='false',south='false',east='false',west='false',waterlogged='false');put(60,75,34,'iron_bars',north='false',south='false',east='false',west='false',waterlogged='false')
# Light inhabited traces: worktables, stored goods, native lights. No decay/monsters.
for x,z in [(36,23),(50,25)]:put(x,39,z,'crafting_table');put(x+1,39,z,'barrel',facing='up',open='false')
for x,z in [(37,34),(50,34)]:put(x,39,z,'lantern',hanging='false',waterlogged='false')
put(53,39,22,'furnace',facing='south',lit='true');put(55,39,23,'barrel',facing='south',open='false')
# Replace top-floor open center after decorative strips. Interior staircase.
for i in range(7):
 put(53,39+i,21+i,'dark_oak_stairs',facing='south',half='bottom',shape='straight',waterlogged='false');put(53,40+i,21+i,'air');put(53,41+i,21+i,'air')
# Exterior sparse warm lamps at route turns, leave most windows dark.
for x,z in [(35,65),(31,44)]:
 box(x,39,z,x,41,z,'polished_blackstone_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false');put(x,42,z,'lantern',hanging='false',waterlogged='false')
# Encode a standard gzip NBT template; deterministic ordering and timestamp.
origin=(22,-34,15);size=(46,110,61);palette=[];lookup={};blocks=[]
for pos,s in sorted(b.items()):
 key=json.dumps(s,sort_keys=True)
 if key not in lookup:lookup[key]=len(palette);palette.append(s)
 blocks.append({'pos':[p-o for p,o in zip(pos,origin)],'state':lookup[key]})
def u(s):v=s.encode();return struct.pack('>H',len(v))+v
def payload(v):
 if isinstance(v,str):return 8,u(v)
 if isinstance(v,int):return 3,struct.pack('>i',v)
 if isinstance(v,list):
  t=payload(v[0])[0] if v else 10
  return 9,bytes([t])+struct.pack('>i',len(v))+b''.join(payload(x)[1] for x in v)
 if isinstance(v,dict):return 10,b''.join(bytes([payload(x)[0]])+u(k)+payload(x)[1] for k,x in v.items())+b'\0'
 raise TypeError(v)
nbt={'DataVersion':3465,'author':'Mispla / Codex original Phase 4 workshop','size':list(size),'palette':palette,'blocks':blocks,'entities':[]}
raw=b'\x0a\0\0'+payload(nbt)[1];packed=gzip.compress(raw,mtime=0)
target=P/'src/main/resources/data/glimmeringabyss/structures/workshop.nbt';target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(packed)
manifest={'schema':1,'template':'glimmeringabyss:workshop','origin':origin,'size':size,'blocks':len(blocks),'palette_states':len(palette),'template_sha256':hashlib.sha256(packed).hexdigest(),'authoring':'Original code-authored vanilla-block structure, no third-party assets','entities':0,'windows_luminance':12,'evidence_note':'Runtime audit reads the shipped NBT palette and all block positions directly'}
f=P/'src/main/resources/data/glimmeringabyss/architecture/workshop.json';f.parent.mkdir(parents=True,exist_ok=True);f.write_text(json.dumps(manifest,separators=(',',':'))+'\n')
print('Structure:',len(blocks),'states:',len(palette),'compressed bytes:',len(packed))
