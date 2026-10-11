"""Deterministic original material textures; numpy/Pillow for regeneration only."""
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter, ImageDraw
rng=np.random.default_rng(4319);N=256
out=Path(__file__).resolve().parent.parent/'dist/assets';out.mkdir(exist_ok=True)
y,x=np.mgrid[:N,:N];fine=rng.normal(0,1,(N,N))
def noise(size):
 a=rng.uniform(0,255,(size,size)).astype('uint8');return np.asarray(Image.fromarray(a).resize((N,N),Image.Resampling.BICUBIC)).astype(float)/255-.5
large=noise(7);mid=noise(30)
values=[]
values.append(.84+.26*large+.15*mid+.055*fine) # porous clay
values.append(.88+.16*large+.16*mid+.045*fine+ .05*np.sin(x*.085+y*.12))
wood=.84+.22*large+.08*mid+.12*np.sin(x*.56+2*np.sin(y*.014))+ .07*np.sin(x*1.74+np.sin(y*.035))
values.append(wood)
values.append(.80+.16*large+.06*mid+.06*fine+.065*np.sin(y*.43+x*.01))
values.append(.77+.22*large+.12*mid+.045*fine)
values.append(.79+.32*large+.18*mid+.08*fine)
values.append(.85+.15*large+.14*mid+.12*fine)
values.append(np.ones((N,N))*.9)
atlas=Image.new('RGB',(N*4,N*2))
for i,v in enumerate(values):
 rgb=np.stack([v,v,v],2)
 if i==4: # restrained rust freckles, most paint remains maintained
  rust=(large+.6*mid>.22)
  rgb[:,:,0]+=rust*.07;rgb[:,:,1]-=rust*.025;rgb[:,:,2]-=rust*.06
 tile=Image.fromarray((np.clip(rgb,0,1)*255).astype('uint8'))
 atlas.paste(tile,((i%4)*N,(i//4)*N))
atlas.save(out/'materials.jpg',quality=88,optimize=True)
# Lunar maria and sparse shallow craters, not a checkerboard or gray ball.
M=512;yy,xx=np.mgrid[:M,:M];ux=(xx-M/2)/(M/2);uy=(yy-M/2)/(M/2)
base=np.ones((M,M))*.94
for cx,cy,rx,ry,depth in [(-.28,.12,.35,.29,.20),(-.1,-.24,.26,.34,.12),(.34,.1,.22,.19,.16),(.02,.4,.22,.14,.09),(-.40,-.24,.22,.14,.09)]:
 base-=depth*np.exp(-(((ux-cx)/rx)**2+((uy-cy)/ry)**2)*2)
base+=rng.normal(0,.014,(M,M))
for i in range(120):
 cx,cy=rng.uniform(-.9,.9,2);r=rng.uniform(.009,.035);rr=np.sqrt((ux-cx)**2+(uy-cy)**2)
 base-=.07*np.exp(-(rr/r)**2*3);base+=.025*np.exp(-((rr-r)/(.20*r))**2)
base*=.9+.1*np.sqrt(np.maximum(0,1-ux**2-uy**2))
rgb=np.stack([base*.96,base*.985,base],2)
Image.fromarray((np.clip(rgb,0,1)*255).astype('uint8')).save(out/'moon.jpg',quality=92,optimize=True)
# Identical station sign for web and native rendering checks.
sign=Image.new('RGB',(1024,128),'#132529');d=ImageDraw.Draw(sign)
from PIL import ImageFont
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf',36)
small=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',14)
d.rectangle((8,8,1015,119),outline='#948571',width=3)
d.text((512,47),'M O O N L I T   H A L T',font=font,fill='#c9c6b5',anchor='mm')
d.text((512,91),'COUNTRY STATION  /  EST. 1869',font=small,fill='#9ea6a3',anchor='mm')
sign.save(out/'station-sign.png',optimize=True)
print('Original textures:',[(p.name,p.stat().st_size) for p in out.iterdir()])
