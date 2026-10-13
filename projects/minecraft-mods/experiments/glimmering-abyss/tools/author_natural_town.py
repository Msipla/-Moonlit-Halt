"""Derive a sparse architectural template without touching the historical town NBT.

The old template contains two million excavation/rock positions. Only occupied
architecture and pedestrian clearance are reused; new geology comes from CavernShape.
"""
from pathlib import Path
import gzip,struct,io,json,hashlib
P=Path(__file__).resolve().parents[1]
def read_nbt(raw):
 f=io.BytesIO(raw)
 def num(fmt):return struct.unpack('>'+fmt,f.read(struct.calcsize('>'+fmt)))[0]
 def string():return f.read(num('H')).decode()
 def tag(t):
  if t in (1,2,3,4,5,6):return num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==8:return string()
  if t==9:
   child,n=num('B'),num('i');return [tag(child) for _ in range(n)]
  if t==10:
   out={}
   while (child:=num('B')):
    name=string();out[name]=tag(child)
   return out
  if t in (7,11,12):return [num({7:'b',11:'i',12:'q'}[t]) for _ in range(num('i'))]
  raise ValueError(t)
 t=num('B');string();return tag(t)
def u(s):v=s.encode();return struct.pack('>H',len(v))+v
def payload(v):
 if isinstance(v,str):return 8,u(v)
 if isinstance(v,int):return 3,struct.pack('>i',v)
 if isinstance(v,list):return 9,bytes([payload(v[0])[0] if v else 10])+struct.pack('>i',len(v))+b''.join(payload(x)[1] for x in v)
 if isinstance(v,dict):return 10,b''.join(bytes([payload(x)[0]])+u(k)+payload(x)[1] for k,x in v.items())+b'\0'
 raise TypeError(v)
n=read_nbt(gzip.decompress((P/'src/main/resources/data/glimmeringabyss/structures/town.nbt').read_bytes()))
b={}
rooms=[(32,58,18,44,39,76),(32,44,-20,-4,39,67),(50,63,-19,-4,39,67),(64,80,-11,7,39,65),(26,39,-43,-27,40,63),(52,65,-64,-47,40,79)]
def clearance(x,y,z):
 return any(a<=x<=c and d<=z<=e and lo<=y<=hi for a,c,d,e,lo,hi in rooms) or (39<=y<=43 and ((-32<=x<=29 and 27<=z<=44) or (22<=x<=42 and 43<=z<=73) or (44<=x<=50 and -61<=z<=20)))
for row in n['blocks']:
 x,y,z=(v+o for v,o in zip(row['pos'],(-55,-63,-106)));s=n['palette'][row['state']];name=s['Name']
 if y<35:continue
 if name in ('minecraft:deepslate','minecraft:cobbled_deepslate','minecraft:black_concrete'):continue
 if name=='minecraft:air' and not clearance(x,y,z):continue
 b[x,y,z]=s
original_windows={p:s for p,s in b.items() if s['Name']=='glimmeringabyss:warm_window'}
def put(x,y,z,name,**props):b[x,y,z]={'Name':name if ':' in name else 'minecraft:'+name,**({'Properties':{k:str(v) for k,v in props.items()}} if props else {})}
def box(x1,y1,z1,x2,y2,z2,name,**props):
 for x in range(x1,x2+1):
  for z in range(z1,z2+1):
   for y in range(y1,y2+1):put(x,y,z,name,**props)
def lamp(x,y,z):put(x,y,z,'lantern',hanging='false',waterlogged='false')
def bed(x,y,z):
 put(x,y,z,'blue_bed',facing='north',part='foot',occupied='false');put(x,y,z-1,'blue_bed',facing='north',part='head',occupied='false')
def seat(x,y,z):put(x,y,z,'dark_oak_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
# Residence: sleeping room upstairs, accessible by a real stair with a headroom opening.
for x,z,y in [(33,-19,39),(51,-18,39)]:
 for i in range(6):
  put(x+1,y+i,z+2+i,'dark_oak_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
  box(x+1,y+i+1,z+2+i,x+2,y+i+3,z+2+i,'air')
 bed(x+5,y+6,z+4);put(x+6,y+6,z+4,'barrel',facing='up',open='false');lamp(x+6,y+7,z+4)
 box(x+4,y,z+2,x+6,y+2,z+2,'bookshelf');seat(x+5,y,z+6);put(x+5,y,z+5,'dark_oak_slab',type='top',waterlogged='false')
# Tea room with a table, facing seats and a quiet book nook.
box(28,40,-41,29,42,-41,'bookshelf');box(30,40,-36,32,40,-36,'dark_oak_slab',type='top',waterlogged='false')
for x in (30,32):seat(x,40,-34)
lamp(34,40,-39)
# Storehouse becomes a useful workshop room rather than a hollow duplicate.
for x in (67,69,71):put(x,39,-8,'barrel',facing='south',open='false')
put(76,39,-8,'loom',facing='south');put(76,39,-6,'stonecutter',facing='west');lamp(72,39,2)
# Clock tower lower reading space, clear of its proven ladder and door.
box(60,41,-61,61,43,-61,'bookshelf');seat(60,41,-57);lamp(61,41,-55)
# Seventh building: small reading refuge on the opposite natural ledge.
box(-59,38,25,-42,38,44,'polished_deepslate')
box(-58,39,26,-43,48,43,'stone_bricks');box(-57,39,27,-44,47,42,'air')
box(-57,38,27,-44,38,42,'dark_oak_planks')
for x in range(-59,-41):
 h=49+min(x+59,-42-x)//2
 box(x,h,24,x,h,45,'deepslate_tile_stairs',facing='east' if x<-50 else 'west',half='bottom',shape='straight',waterlogged='false')
 if -58<=x<=-43:
  for z in (26,43):box(x,49,z,x,h-1,z,'stone_bricks')
box(-43,39,34,-42,41,36,'air')
# Glass offers an actual view from indoors; unchanged old warm windows stay exact.
for x in (-55,-49):
 box(x,40,43,x+2,43,43,'glass_pane',north='false',south='false',east='true',west='true',waterlogged='false')
 lamp(x+1,44,42)
box(-57,39,28,-57,42,39,'bookshelf');box(-53,39,28,-46,40,28,'bookshelf')
for x,z in [(-53,39),(-49,39),(-46,32)]:seat(x,39,z)
box(-53,39,36,-50,39,37,'dark_oak_slab',type='top',waterlogged='false');lamp(-52,40,36)
bed(-46,39,30)
for x in range(-42,-24):
 for z in range(33,38):
  put(x,38,z,'deepslate_tiles');box(x,39,z,x,43,z,'air')
 for z in (32,38):put(x,39,z,'cobbled_deepslate_wall',up='true',north='none',south='none',east='tall',west='tall',waterlogged='false')
lamp(-41,40,32)
# One small, fully enclosed static basin. Every water cell has stone below and
# either water or solid stone on all four sides, avoiding cascading fluid work.
box(72,37,-37,80,39,-29,'deepslate')
box(73,39,-36,79,43,-30,'air')
box(74,38,-35,78,38,-31,'water',level='0')
for x,z in [(73,-30),(79,-30)]:lamp(x,39,z)
assert all(b[p]==s for p,s in original_windows.items())
origin=(-64,34,-106);size=(148,46,180);palette=[];lookup={};rows=[]
for pos,s in sorted(b.items()):
 key=json.dumps(s,sort_keys=True)
 if key not in lookup:lookup[key]=len(palette);palette.append(s)
 assert all(0<=v-o<dim for v,o,dim in zip(pos,origin,size)),pos
 rows.append({'pos':[v-o for v,o in zip(pos,origin)],'state':lookup[key]})
packed=gzip.compress(b'\x0a\0\0'+payload({'DataVersion':3465,'author':'Mispla / Codex Phase 7.5 sparse town','size':list(size),'palette':palette,'blocks':rows,'entities':[]})[1],mtime=0)
(P/'src/main/resources/data/glimmeringabyss/structures/natural_town.nbt').write_bytes(packed)
(P/'src/main/resources/data/glimmeringabyss/architecture/natural_town.json').write_text(json.dumps(dict(layout=75,origin=origin,size=size,buildings=7,blocks=len(rows),warm_windows_preserved=len(original_windows),water_cells=25,template_sha256=hashlib.sha256(packed).hexdigest(),historic_town_unchanged=True),indent=2)+'\n')
print('Natural town:',len(rows),'positions;',len(packed),'bytes; seven buildings')
