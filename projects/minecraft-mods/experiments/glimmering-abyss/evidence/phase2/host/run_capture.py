from pathlib import Path
import os,socket,subprocess,sys,time,urllib.parse
R=Path(__file__).resolve().parent
P=R.parent/'moonlit-halt-repo/projects/minecraft-mods/experiments/glimmering-abyss'
proxy=urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
opts=f'-XX:-UseContainerSupport -Djava.net.preferIPv4Stack=true -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={proxy.port} -Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port} -Dorg.lwjgl.glfw.libname={R}/glfw/libglfw.so'
env=dict(os.environ,JAVA_HOME=str(R/'jdk-17'),GRADLE_USER_HOME=str(R/'gradle-cache'),JAVA_TOOL_OPTIONS=opts,GRADLE_OPTS=opts,DISPLAY='127.0.0.1:111',LIBGL_ALWAYS_SOFTWARE='1',LP_NUM_THREADS='4',LD_LIBRARY_PATH=str(R/'x11/usr/lib/x86_64-linux-gnu'))
env.pop('WAYLAND_DISPLAY',None)
for f in (R/'gradle-cache/caches/fabric-loom').glob('.*.lock'):
    f.unlink() # No concurrent Gradle process; PID namespaces make completed-run locks stale.
xkb=Path('/usr/bin/xkbcomp')
if not xkb.exists():xkb.symlink_to(R/'x11/usr/bin/xkbcomp')
out=P/'evidence/phase2';out.mkdir(parents=True,exist_ok=True)
with (out/'xvfb.log').open('w') as log:
    xvfb=subprocess.Popen([str(R/'x11/usr/bin/Xvfb'),':111','-screen','0','1280x720x24','-nolisten','local','-nolisten','unix','-nolisten','inet6','-listen','tcp','-ac','-pn','-noreset'],env=env,stdout=log,stderr=subprocess.STDOUT)
    try:
        deadline=time.monotonic()+15
        while True:
            try:
                with socket.create_connection(('127.0.0.1',6111),timeout=1):break
            except OSError:
                if xvfb.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Xvfb failed: '+(out/'xvfb.log').read_text())
                time.sleep(.2)
        print('Software OpenGL display ready',flush=True)
        result=subprocess.run([sys.executable,'-u',str(P/'tools/run_experiment.py'),'--accept-eula','--skip-build','--capture','--phase2','--skip-asset-download','--server-template',str(P/'.runtime/server-template-phase2'),'--output','evidence/phase2'],cwd=P,env=env)
    finally:
        xvfb.terminate();xvfb.wait(timeout=15)
sys.exit(result.returncode)
