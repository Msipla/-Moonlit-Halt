"""Promote only an entirely passing, installed release run; retain failed candidates."""
from pathlib import Path
import json,hashlib,shutil,sys,zipfile,struct,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
run=sys.argv[1];out=ROOT/'evidence/phase75/runs'/run
def read(name):return json.loads((out/name).read_text())
summary=read('summary.json');assert summary['status']=='TECHNICAL_PASS'
source=read('provenance.json')['source_commit'];checks=read('checksums.json')
jar=out/'glimmering-abyss-0.7.5.jar';digest=hashlib.sha256(jar.read_bytes()).hexdigest()
assert digest==summary['release_jar_sha256']==read('client-launch-provenance.json')['release_sha256']==checks['artifacts'][jar.name]['sha256']
assert not read('client-launch-provenance.json')['development_classes']
first,reload,second=[read('phase7-'+label+'.json') for label in ('first','reload','second')]
for audit in (first,reload,second):
 assert audit['pass'] and audit['layout']==75
 assert audit['warm_windows_preserved']==audit['warm_windows_native_12']==128
 assert audit['water_source_cells']==25 and audit['water_basin_contained']
 assert audit['architectural_missing_or_wrong_blocks']==0
 assert audit['scene_insertions_blocked'] and audit['outside_boundary_insertions_allowed'] and audit['loaded_scene_mobs']==0
 assert audit['doMobSpawning'] and audit['difficulty']=='hard' and audit['bedrock_intact']
assert first['geometry_sha256']==reload['geometry_sha256']
assert first['unchanged_surface_and_bedrock_chunks']==second['unchanged_surface_and_bedrock_chunks']==252
assert (first['x'],first['z'])!=(second['x'],second['z'])
assert read('journey.json')['pass'] and read('recovery.json')['pass'] and read('phase7-debug-permissions.json')['pass']
assert read('refuge-sleep.json')['actual_refuge_bed_sleep'] and read('refuge-sleep.json')['health']==20
assert read('legacy-upgrade.json')['pass_'] and read('phase7-legacy-after.json')['player_witness_gold']
assert read('phase7-legacy-after.json')['layout']==70
assert read('phase7-legacy-before.json')['geometry_sha256']==read('phase7-legacy-after.json')['geometry_sha256']
walks=read('walk.json');assert len(walks)==26 and all(r['pass'] for r in walks)
assert len(read('surface-access.json'))==3 and all(r['pass'] and r['health']==20 for r in read('surface-access.json'))
assert read('phase7-intrusion.json')['removed_after_crossing']
assert read('phase7-natural.json')['outside_loaded_monsters']>0 and read('phase7-natural.json')['inside_loaded_monsters']==0
rows=read('screenshots/captures.json');assert len(rows)>=24 and all(r['gl_error']==0 and r['health']==20 for r in rows)
for name in ['dream-1.png','dream-2.png','naturalized-cliff.png','natural-floor.png','contained-water.png','library-interior.png','second-seed-overview.png','reconnected-safe-station.png']:
 raw=(out/'screenshots'/name).read_bytes();assert raw[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',raw[16:24])==(1280,720)
tests=0
for file in out.glob('TEST-*.xml'):
 suite=ET.parse(file).getroot();assert all(int(suite.get(k,0))==0 for k in ('failures','errors','skipped'));tests+=int(suite.get('tests'))
assert tests>=14
motion=read('cabin-motion.json');assert len(motion)>500
movement=dict(samples=len(motion),horizontal_excursion=max(r['car_x'] for r in motion)-min(r['car_x'] for r in motion),max_relative_x=max(abs(r['x']-r['car_x']) for r in motion),max_relative_z=max(abs(r['z']-r['car_z']) for r in motion),vertical_relative_range=max(r['y']-r['car_y'] for r in motion)-min(r['y']-r['car_y'] for r in motion))
assert movement['horizontal_excursion']>27 and movement['max_relative_x']<.001 and movement['max_relative_z']<.001 and movement['vertical_relative_range']<.001
(out/'cabin-motion-summary.json').write_text(json.dumps(movement,indent=2)+'\n')
with zipfile.ZipFile(jar) as new,zipfile.ZipFile(ROOT/'artifacts/glimmering-abyss-0.7.0.jar') as old:
 assert json.loads(new.read('fabric.mod.json'))['version']=='0.7.5'
 for name in ['data/glimmeringabyss/structures/town.nbt','assets/glimmeringabyss/visual-profiles.json']:assert new.read(name)==old.read(name)
for name,info in checks['artifacts'].items():
 file=out/name;assert hashlib.sha256(file.read_bytes()).hexdigest()==info['sha256'];shutil.copy2(file,ROOT/'artifacts'/name)
checks['verified_runtime']=run;(ROOT/'artifacts/checksums-v0.7.5.json').write_text(json.dumps(checks,indent=2)+'\n')
(ROOT/'evidence/phase75/latest.json').write_text(json.dumps(dict(run=run,source_commit=source,status='TECHNICAL_PASS',aesthetic_acceptance='Pending user review; Android Amethyst ride comfort, dream display and suspected missing rendering still require actual device testing'),indent=2)+'\n')
print(json.dumps(dict(run=run,source=source,jar_sha256=digest,bytes=jar.stat().st_size,tests=tests,screenshots=len(list((out/'screenshots').glob('*.png'))),walking_legs=len(walks),geometry_sha256=first['geometry_sha256'],movement=movement),indent=2))

report=f"""# Phase 7.5 · 天然洞穴中的暖光庇护所

**正式v0.7.5工程技术验收通过；新美学与安卓Amethyst体验待用户最终验证。**

源码 `{source}`；真实Minecraft发布客户端/Fabric专用服务端运行 [{run}](https://github.com/Msipla/-Moonlit-Halt/actions/runs/{run})。正式[JAR](artifacts/glimmering-abyss-0.7.5.jar) {jar.stat().st_size:,}字节，SHA-256 `{digest}`。安装见[INSTALL-PHASE7.5](INSTALL-PHASE7.5.md)，完整原始证据在[evidence/phase75/runs/{run}](evidence/phase75/runs/{run}/)。

## 实现与取舍

- 在原252区块和原山体安全高度下塑造不对称、随高度变化的洞壁，三个侧洞、岩架、岩层、起伏底面和向下展宽的岩台；保留巨柱、宏大空间、冷蓝基准与原128暖窗。没有重写全世界地形，也没有以装饰代替地貌。
- 六栋原建筑保留，增加不同用途的家具、住宅上层楼梯/床铺；桥对岸增加第七栋阅览庇护所与自然承重岩架，真实步行可进屋并使用床铺。住宅楼梯的每条路线未全部逐栋实走，仍可在安卓验收时补查。
- 一处25格水源的封闭小池，不加入大片地下海洋或流体瀑布系统。
- 新布局缆车的上部仍穿越入口竖井，地下段向左与前方出游、再落到原站台。补客户端车辆插值，实测约{movement['horizontal_excursion']:.2f}格横向出游，乘客/车厢相对位置稳定。此结果不等于安卓主观舒适度已通过。
- 两晚正常床铺互动，醒来展示各2秒真实Minecraft场景静态图；没有跨维度传送、强制移动或可交互梦境维度。资源来源与哈希保存在`assets/glimmeringabyss/dream-scenes.json`，画面来自第四轮已肉眼核实、健康20/GL错误0/区块队列清空的有效单帧；第四轮最终见证金块断言失败仍完整保留。
- 寻梦微光区分搜索/重选、覆盖检查、生成进度、就绪和停止原因。新增仅创造模式的状态/安全定位/梦境预览/乘车验证；不泄露候选坐标，不开放重建或覆盖世界指令。
- 新景观布局75；缺少布局标记的Phase7存档读取为70，已完成景观不会重铺。旧存档保留旧缆车路径，只获得插值、状态提示与静态梦境更新。

## 真实验收

使用官方发布客户端、remapped发布JAR和普通主世界Fabric专用服，HARD、`doMobSpawning=true`；不是开发类路径或替身模拟。

| 检查 | 实际结果 |
| --- | --- |
| Java测试 | {tests}项，零失败、错误或跳过 |
| 两种子 | 74102002原点({first['x']},{first['z']})；-92345原点({second['x']},{second['z']})；两处均生成252区块，并各启动真实客户端取景 |
| 地表与基岩 | 两种子首次生成后252/252区块覆盖/底界保留；没有放宽完整覆土要求 |
| 原有暖窗 | 128/128保留，全部原生12级光；旧town.nbt与默认visual-profiles.json字节不变 |
| 建筑缺漏 | {first['architectural_nonair_blocks']:,}处明确建筑非空气方块逐一核对，缺漏/类型错误0；不把它说成所有地形体素均逐一检查 |
| 水体 | 25格水源仍在池内；每格下方与四周均有水源或实心边界；重启/第二种子再次通过 |
| 局部禁怪 | 每轮54处区域内插入拒绝、6处外部插入允许；内部加载生物0；外部实际自然怪物{read('phase7-natural.json')['outside_loaded_monsters']}只，规则与困难难度保留 |
| 外部侵入 | 外部僵尸移入景观后移除 |
| 睡眠与定位 | 两次实际床铺点击睡到天亮、两段GUI梦境截图、收到并使用寻梦微光；另实际使用对岸庇护所床铺 |
| 行走 | {len(walks)}段真实前进键行走，包含过桥、进阅览室、休息后离屋及返程；另3段地表阶梯实际进出 |
| 乘车 | 真实铃铛交互、正常上下往返、潜行提前返回；{len(motion)}个客户端运动采样，无乘客相对车厢漂移 |
| 途中停服/重连 | 停服前确实乘车；同存档同离线测试身份重连后健康20、离车并落到地表安全站台 |
| 新档重启 | {first['geometry_sample_count']:,}处已绘制体积/阶梯状态采样指纹一致：`{first['geometry_sha256']}` |
| 旧档升级 | 用正式0.7.0 JAR生成旧世界并放置金块，再换0.7.5重启；布局70、金块仍在、景观采样指纹一致 |
| 调试权限 | 操作员生存玩家仍被拒绝，创造玩家可以使用；生产调试命令无需testServer启动参数 |

## 原始画面与边界

{len(list((out/'screenshots').glob('*.png')))}张1280×720原始PNG，主组FOV75、Gamma0.5、16区块；主组所有采集帧GL错误0且健康20。摄影位置与生存可达性分开记录，摄影使用创造飞行并检查真实碰撞净空。没有提亮、调色或生成概念图代替实机。

关键原图：[全景](evidence/phase75/runs/{run}/screenshots/overworld-town-overview.png)、[岩台](evidence/phase75/runs/{run}/screenshots/naturalized-cliff.png)、[天然底面](evidence/phase75/runs/{run}/screenshots/natural-floor.png)、[侧洞](evidence/phase75/runs/{run}/screenshots/side-cave.png)、[桥对岸](evidence/phase75/runs/{run}/screenshots/bridge-destination.png)、[阅览室](evidence/phase75/runs/{run}/screenshots/library-interior.png)、[卧室](evidence/phase75/runs/{run}/screenshots/residence-bedroom.png)、[小池](evidence/phase75/runs/{run}/screenshots/contained-water.png)、[第一晚](evidence/phase75/runs/{run}/screenshots/dream-1.png)、[第二晚](evidence/phase75/runs/{run}/screenshots/dream-2.png)。与Phase7全景对照采用相同相对机位和显示设置，不同运行不比较FPS。

这是托管Linux软件OpenGL验证。安卓Amethyst最终乘坐舒适度、梦境显示兼容性、疑似未渲染方块和审美仍待用户实测；本轮模板零缺漏与采集无GL错误不证明原安卓问题根因已彻底消失。Windows/PCL2、用户GPU、Sodium/Iris/DH、第三方光影、长时多人、异常断电及所有区块独立卸载场景未测。上部竖井仍是垂直旅行；没有自由缆车物理、NPC、战斗或全球地下替换。

## 失败证据保留

- `38385676950`：构建/12项测试成功，但新洞顶覆盖需求过滤了全部4096候选，未进入生成与客户端。修正为原Phase7覆土包络，保护不放宽。
- `38387233527`：14项测试、生成、正常发现/行走/往返、权限与水体通过；旧摄影机位落入新岩体，后续窒息死亡导致停服测试未开始。黑帧/死亡帧原样保留，不能当作完整通过。
- `38390229816`：新梦境与进屋通过，但床铺交互停在较远位置，测试超时；移近后第四轮已真实睡眠通过。
- `38392139699`：两种子客户端、26段行走、床铺与重连、新旧档几何一致通过；见证金块命令被旧测试通道忽略，末项断言失败。第一段静态素材还残留历史死亡界面，肉眼复查后改用本轮有效侧洞原图。
- `38396057622`：全部新世界/客户端/梦境/床铺/重连测试通过；旧服目标区块卸载导致控制台setblock失败，尚不能证明改动保留。测试先强制加载见证区块再写入，不修改生产生成或保护代码。
- `{run}`：正式发布包完整技术验收通过，唯一正式v0.7.5来源。网页发布与浏览器验证另见现有展厅网站报告，不能把分支预览当成上线。
"""
(ROOT/'TEST-REPORT-PHASE7.5.md').write_text(report)
