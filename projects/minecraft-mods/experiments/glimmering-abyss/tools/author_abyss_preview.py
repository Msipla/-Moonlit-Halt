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
def put(x,y,z,name,**props):b[x,y,z]={'Name':'minecraft:'+name,**({'Properties':{k:str(v) for k,v in props.items()}} if props else {})}
# Local floor coordinates. Axis-aligned legs allow safe stairs and reproducible real walking.
route=[(47,40,-68),(47,40,-84),(22,37,-84),(22,37,-100),(-28,31,-100),(-28,31,-124),(-78,27,-124),(-78,25,-148),(-126,24,-148),(-126,20,-184),(-151,20,-184),(-151,16,-214),(-90,16,-214),(-90,22,-176),(-66,22,-176),(-66,27,-124),(-78,27,-124)]
floors={};facing={}
for a,c in zip(route,route[1:]):
 length=max(abs(c[0]-a[0]),abs(c[2]-a[2]));dx=(c[0]-a[0])//length;dz=(c[2]-a[2])//length
 for i in range(length+1):
  x,z=a[0]+dx*i,a[2]+dz*i;y=round(a[1]+(c[1]-a[1])*i/length)
  nexty=round(a[1]+(c[1]-a[1])*min(i+1,length)/length)
  for offset in range(-2,3):
   pos=(x+(offset if dz else 0),z+(offset if dx else 0));floors[pos]=max(y,floors.get(pos,-99))
   if nexty!=y:facing[pos]=('east' if dx>0 else 'west') if dx else ('south' if dz>0 else 'north')
# Rock path footing/clearance; walls only on exposed path edges, not a fence through junctions.
for (x,z),y in floors.items():
 for h in range(y-5,y+1):put(x,h,z,'deepslate' if h<y else 'cobbled_deepslate')
 for h in range(y+1,y+7):put(x,h,z,'air')
for (x,z),y in floors.items():
 # A half-height tread where the next floor rises or falls. Choose the higher floor and face uphill.
 neighbors=[((x+dx,z+dz),direction) for dx,dz,direction in [(1,0,'east'),(-1,0,'west'),(0,1,'south'),(0,-1,'north')]]
 higher=next((direction for q,direction in neighbors if floors.get(q,y)==y+1),None)
 if higher:put(x,y+1,z,'cobbled_deepslate_stairs',facing=higher,half='bottom',shape='straight',waterlogged='false')
 if any(q not in floors for q,_ in neighbors):put(x,y+1,z,'cobbled_deepslate_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
# Occasional familiar warm lights mark the way back. No combat/biomes/new regions.
for x,y,z in route:
 edge=next(((xx,zz) for xx,zz in floors if abs(xx-x)+abs(zz-z)<=3 and any((xx+dx,zz+dz) not in floors for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)])),None)
 if edge:put(edge[0],floors[edge]+2,edge[1],'lantern',hanging='false',waterlogged='false')
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
