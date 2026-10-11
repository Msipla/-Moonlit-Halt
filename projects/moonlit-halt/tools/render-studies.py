"""Render actual scene commands in GLES. No browser/UI/phone execution."""
import os,json,subprocess,tempfile
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parent.parent;os.chdir(root)
views={
 'arrival':{'eye':[0,1.7,4],'yaw':0,'pitch':.035},
 'facade':{'eye':[0,1.7,-18],'yaw':-.04,'pitch':.14},
 'platform':{'eye':[8.0,2.16,-26.8],'yaw':-1.05,'pitch':.18},
}
out=root/'dist/studies';out.mkdir(exist_ok=True)
for version in os.environ.get('MOONLIT_STUDY_VERSIONS','v1,v2').split(','):
 for name,cam in views.items():
  env=os.environ.copy();env.update(MOONLIT_SCENE='dist/v1/scene.js' if version=='v1' else 'dist/scene.js',MOONLIT_CAPTURE_ONLY='1',MOONLIT_CAMERA=json.dumps(cam),MOONLIT_CAPTURE=str(Path(tempfile.gettempdir())/f'moonlit-{version}-{name}.json'),MOONLIT_RENDER=str(out/f'{version}-{name}.png'))
  subprocess.run(['node','tests/check-scene.cjs'],env=env,check=True);subprocess.run(['python','tests/render-gles.py'],env=env,check=True)
# Portrait shader/geometry check (no UI), matching a tall mobile viewport.
env=os.environ.copy();env.update(MOONLIT_SCENE='dist/scene.js',MOONLIT_CAPTURE_ONLY='1',MOONLIT_WIDTH='720',MOONLIT_HEIGHT='1280',MOONLIT_CAMERA=json.dumps(views['facade']),MOONLIT_CAPTURE=str(Path(tempfile.gettempdir())/'moonlit-v2-portrait.json'),MOONLIT_RENDER=str(out/'v2-portrait.png'))
subprocess.run(['node','tests/check-scene.cjs'],env=env,check=True);subprocess.run(['python','tests/render-gles.py'],env=env,check=True)
# Explicit labels separate the images; do not retouch the rendered pixels.
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',20)
board=Image.new('RGB',(1600,1070),'#0b1521');draw=ImageDraw.Draw(board)
for row,name in enumerate(['arrival','facade']):
 for col,v in enumerate(['v1','v2']):
  x=col*800;y=row*510+42;draw.text((x+18,y-30),f'{v.upper()}  /  {name.upper()}  /  same camera',font=font,fill='#c3c6c6')
  im=Image.open(out/f'{v}-{name}.png').convert('RGB').resize((800,450),Image.Resampling.LANCZOS);board.paste(im,(x,y))
draw.text((18,1030),'Actual offscreen OpenGL ES renders. Not Android screenshots. No UI overlay.',font=font,fill='#8b9eae')
board.save(out/'comparison.jpg',quality=94)
(out/'cameras.json').write_text(json.dumps({'views':views,'viewport':[1280,720],'time_seconds':12,'renderer':'Mesa EGL / OpenGL ES, native command replay','limitations':'No browser DOM/UI/audio execution. v1 canvas sign is approximated with Pillow glyphs.'},indent=2))
print('Render studies complete:',out)
