import ctypes as C, json, os, tempfile
from PIL import Image
E=C.CDLL('libEGL.so.1'); P=C.c_void_p; I=C.c_int; U=C.c_uint; F=C.c_float
E.eglGetProcAddress.argtypes=[C.c_char_p];E.eglGetProcAddress.restype=P
getPlatform=C.CFUNCTYPE(P,U,P,C.POINTER(I))(E.eglGetProcAddress(b'eglGetPlatformDisplayEXT'))
d=getPlatform(0x31DD,None,None)
E.eglInitialize.argtypes=[P,C.POINTER(I),C.POINTER(I)]; a=I();b=I();assert E.eglInitialize(d,C.byref(a),C.byref(b))
E.eglBindAPI.argtypes=[U];assert E.eglBindAPI(0x30A0)
attrs=(I*15)(0x3033,1,0x3040,4,0x3024,8,0x3023,8,0x3022,8,0x3025,24,0x3038,0,0)
E.eglChooseConfig.argtypes=[P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I)];conf=P();n=I();assert E.eglChooseConfig(d,attrs,C.byref(conf),1,C.byref(n)) and n.value
E.eglCreatePbufferSurface.argtypes=[P,P,C.POINTER(I)];E.eglCreatePbufferSurface.restype=P
surface=E.eglCreatePbufferSurface(d,conf,(I*5)(0x3057,int(os.environ.get('MOONLIT_WIDTH','1280')),0x3056,int(os.environ.get('MOONLIT_HEIGHT','720')),0x3038))
E.eglCreateContext.argtypes=[P,P,P,C.POINTER(I)];E.eglCreateContext.restype=P
context=E.eglCreateContext(d,conf,None,(I*3)(0x3098,2,0x3038))
E.eglMakeCurrent.argtypes=[P,P,P,P];assert E.eglMakeCurrent(d,surface,surface,context)
def fn(name,ret,*args):return C.CFUNCTYPE(ret,*args)(E.eglGetProcAddress(name.encode()))
createShader=fn('glCreateShader',U,U);source=fn('glShaderSource',None,U,I,C.POINTER(C.c_char_p),C.POINTER(I));compile=fn('glCompileShader',None,U);getShader=fn('glGetShaderiv',None,U,U,C.POINTER(I));logShader=fn('glGetShaderInfoLog',None,U,I,C.POINTER(I),C.c_char_p)
createProgram=fn('glCreateProgram',U);attach=fn('glAttachShader',None,U,U);link=fn('glLinkProgram',None,U);getProgram=fn('glGetProgramiv',None,U,U,C.POINTER(I));use=fn('glUseProgram',None,U)
def make(shaders):
 p=createProgram()
 for s in shaders:
  sh=createShader(s['type']);ss=C.c_char_p(s['source'].encode());source(sh,1,C.byref(ss),None);compile(sh);ok=I();getShader(sh,0x8B81,C.byref(ok))
  if not ok.value:
   buf=C.create_string_buffer(4096);logShader(sh,4096,None,buf);raise Exception(buf.value)
  attach(p,sh)
 link(p);ok=I();getProgram(p,0x8B82,C.byref(ok));assert ok.value,'link failure';return p
cap=json.load(open(os.environ.get('MOONLIT_CAPTURE',os.path.join(tempfile.gettempdir(),'moonlit-scene-capture.json'))));programs={p['id']:make(p['shaders']) for p in cap['programs']}
getLoc=fn('glGetUniformLocation',I,U,C.c_char_p);getAttr=fn('glGetAttribLocation',I,U,C.c_char_p);mat=fn('glUniformMatrix4fv',None,I,I,U,C.POINTER(F));vec=fn('glUniform3fv',None,I,I,C.POINTER(F));scalar=fn('glUniform1f',None,I,F);integer=fn('glUniform1i',None,I,I)
gen=fn('glGenBuffers',None,I,C.POINTER(U));bind=fn('glBindBuffer',None,U,U);buffer=fn('glBufferData',None,U,C.c_ssize_t,P,U);enableAttr=fn('glEnableVertexAttribArray',None,U);pointer=fn('glVertexAttribPointer',None,U,I,U,U,I,P);draw=fn('glDrawArrays',None,U,I,I);enable=fn('glEnable',None,U);disable=fn('glDisable',None,U);clear=fn('glClear',None,U);clearColor=fn('glClearColor',None,F,F,F,F);viewport=fn('glViewport',None,I,I,I,I)
active=fn('glActiveTexture',None,U);genTex=fn('glGenTextures',None,I,C.POINTER(U));bindTex=fn('glBindTexture',None,U,U);texImage=fn('glTexImage2D',None,U,I,I,I,I,I,U,U,P);param=fn('glTexParameteri',None,U,U,I)
from pathlib import Path
root=Path(os.environ.get('MOONLIT_ASSET_ROOT','dist'))
textures={}
for tid,t in cap['textures'].items():
 v=U();genTex(1,C.byref(v));bindTex(0x0DE1,v)
 if t.get('path') and t['path']!='canvas':im=Image.open(root/t['path']).convert('RGBA')
 elif t.get('path')=='canvas':
  # Only legacy v1 used a browser canvas. Reproduce its board for comparison.
  from PIL import ImageDraw,ImageFont
  im=Image.new('RGBA',(512,96),'#192b30');dr=ImageDraw.Draw(im);dr.rectangle((6,6,505,89),outline='#b2a27e',width=3);fo=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf',24);dr.text((256,33),'M O O N L I T   H A L T',font=fo,fill='#ddd1ab',anchor='mm')
 else:im=Image.new('RGBA',(1,1),tuple(t.get('fallback',[255]*4)))
 raw=im.tobytes();storage=C.create_string_buffer(raw);texImage(0x0DE1,0,0x1908,im.width,im.height,0,0x1908,0x1401,storage)
 for name,value in [(0x2801,0x2601),(0x2800,0x2601),(0x2802,0x812F),(0x2803,0x812F)]:param(0x0DE1,name,value)
 if im.width&(im.width-1)==0 and im.height&(im.height-1)==0:
  fn('glGenerateMipmap',None,U)(0x0DE1);param(0x0DE1,0x2801,0x2703)
 textures[int(tid)]=v.value
buffers={}
for bid,vertices in cap['buffers'].items():
 if not vertices:continue
 v=U();gen(1,C.byref(v));bind(0x8892,v);geo=(F*len(vertices))(*vertices);buffer(0x8892,C.sizeof(geo),geo,0x88E4);buffers[int(bid)]=v.value
w,h=cap['width'],cap['height'];viewport(0,0,w,h);clearColor(.03,.05,.09,1);clear(0x4100)
attrNames={0:b'aPosition',1:b'aNormal',2:b'aColor',3:b'aMaterial'}
for d in cap['draws']:
 if not d['count']:continue
 p=programs[d['program']];use(p);bind(0x8892,buffers[d['buffer']])
 (enable if d['depth'] else disable)(0x0B71);(enable if d['blend'] else disable)(0x0BE2);fn('glDepthMask',None,U)(d['mask']);fn('glBlendFunc',None,U,U)(0x0302,0x0303)
 for name,value in d['uniforms'].items():
  loc=getLoc(p,name.encode())
  if isinstance(value,list):
   if len(value)==16:mat(loc,1,0,(F*16)(*value))
   else:vec(loc,len(value)//3,(F*len(value))(*value))
  elif name in ['uAtlas','uMoon','uTexture']:integer(loc,int(value))
  else:scalar(loc,value)
 for u,tid in d['textures'].items():
  if tid in textures:active(0x84C0+int(u));bindTex(0x0DE1,textures[tid])
 shaderText=next(pp for pp in cap['programs'] if pp['id']==d['program'])['shaders'][0]['source']
 enabled=[]
 for index,desc in d['attributes'].items():
  index=int(index);name=attrNames.get(index)
  if index==0 and 'attribute vec2 a;' in shaderText:name=b'a'
  if index==1 and 'aUV' in shaderText:name=b'aUV'
  loc=getAttr(p,name)
  if loc<0:continue
  enableAttr(loc);pointer(loc,desc['size'],0x1406,0,desc['stride'],P(desc['offset']));enabled.append(loc)
 draw(4,0,d['count'])
 for loc in enabled:fn('glDisableVertexAttribArray',None,U)(loc)
 err=fn('glGetError',U)();assert err==0,hex(err)
raw=(C.c_ubyte*(w*h*4))();fn('glReadPixels',None,I,I,I,I,U,U,P)(0,0,w,h,0x1908,0x1401,raw)
im=Image.frombytes('RGBA',(w,h),bytes(raw)).transpose(Image.Transpose.FLIP_TOP_BOTTOM);im.save(os.environ.get('MOONLIT_RENDER',os.path.join(tempfile.gettempdir(),'moonlit-native-render.png')))
print(f"PASS: {len(programs)*2} shaders compiled, {len(programs)} programs linked; {len(cap['draws'])} captured passes replayed at {w}x{h}, GL error 0. Native offscreen GLES, not browser/mobile QA.")
