from pathlib import Path
import os,subprocess,sys,urllib.parse
R=Path(__file__).resolve().parent
P=Path('/workspace/scratch/f5fbcc4a8b17/moonlit-halt-repo/projects/minecraft-mods/experiments/glimmering-abyss')
proxy=urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
opts=f'-XX:-UseContainerSupport -Djava.net.preferIPv4Stack=true -Dorg.gradle.internal.http.connectionTimeout=10000 -Dorg.gradle.internal.http.socketTimeout=10000 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={proxy.port} -Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port}'
env=dict(os.environ,JAVA_HOME=str(R/'jdk-17'),GRADLE_USER_HOME=str(R/'gradle-cache'),JAVA_TOOL_OPTIONS=opts,GRADLE_OPTS=opts)
def run():
    with (R/'gradle-current.log').open('w') as log:
        return subprocess.run([str(R/'gradle-8.2.1/bin/gradle'),'--no-daemon']+sys.argv[1:],cwd=P,env=env,stdout=log,stderr=subprocess.STDOUT)
result=run()
if result.returncode and 'CurrentPlatform' in (R/'gradle-current.log').read_text(errors='replace'):
    (R/'gradle-unix-failure.log').write_bytes((R/'gradle-current.log').read_bytes())
    subprocess.run([sys.executable,str(P/'tools/cloud_loom_compat.py'),'--gradle-user-home',str(R/'gradle-cache'),'--gradle-install',str(R/'gradle-8.2.1'),'--jdk',str(R/'jdk-17')],check=True,env=env)
    result=run()
print('Gradle exit',result.returncode)
print('\n'.join((R/'gradle-current.log').read_text(errors='replace').splitlines()[-40:]))
sys.exit(result.returncode)
