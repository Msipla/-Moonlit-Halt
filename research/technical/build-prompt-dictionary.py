#!/usr/bin/env python3
"""Rebuild the pinned dictionary and disposable integration HTML; stable HTML is read-only.
Python 3.10+ and PyYAML 6 are required. No online translation or image downloads.
"""
import argparse, collections, concurrent.futures, csv, gzip, hashlib, io, json, pathlib, re, sqlite3, urllib.request

PINS = {'DominikDoom': {'repo': 'DominikDoom/a1111-sd-webui-tagcomplete', 'commit': '4170882f90b47be130a0ff9314f663c230b9153d', 'files': {'LICENSE': '72a5479833a0bc73e8b2e4b39467e7ccfc8bdc0c', 'README.md': 'd495692e1a2ebc08dc69bd2cb9d329e1b3dd1b4b', 'tags/danbooru.csv': '7a3cba59a8cd75bad45b0097338f8f882714a040'}}, 'ffdkj': {'repo': 'ffdkj/ffdkj-Danbooru_Tag-Chinese-English-Translation-Table', 'commit': '15f5f4b50428226649d371cbf7faff8ddaacdb24', 'files': {'LICENSE': '87f04113fd83a6e3027c4c056561cf8c48aa36c2', 'README.md': 'ba1249944183fde6ebf231aca3b66eb7f0a62de8', 'tag.sqlite': '6deb1381fdc5271c1663324cd1731759d1b398c2'}}, 'Physton': {'repo': 'Physton/sd-webui-prompt-all-in-one-assets', 'commit': '6dd9e03e8f8b9a0f7f4790bbaf1833c029e63b14', 'files': {'LICENSE': '6a558137580eafb43139416c9124ff7b21f71253', 'tags/danbooru.zh_CN.csv': '7803deb6ffecd8803ccb827d4b5db77d767ef393'}}, 'wfjsw': {'repo': 'wfjsw/danbooru-diffusion-prompt-builder', 'commit': 'bb52582cfadcca0b1741e49c1518ab5bf9a616c8', 'files': {'LICENSE': 'be3f7b28e564e7dd05eaf59d64adba1a4065ac0e', 'README.md': '05341a960238d28f66073a796387b1b31b56d3a2', 'data/danbooru_tag_post_count.json': '690b512edd827705be1dc3709731c0fb0152a432', 'data/tags/artistic-license.yaml': 'a8c014869e4e31c22cbfc4264f4caf01a2432299', 'data/tags/characters.yaml': '495add624edae07b0aef56e93ea0cfcdb8ceae8b', 'data/tags/human/actions.yaml': '1efc00336b1c89933f0444410daaa42a3fc98b3f', 'data/tags/human/body_ornament.yaml': '6550f496ef040a98b6029b1e4d40f1b879a1ab4a', 'data/tags/human/breasts.yaml': '02c42ce30fdb8b7a30d26850dd2ae3cdf04e4238', 'data/tags/human/clothes.yaml': '00d9805d41eedd64b665a199b9d6f7a1569cace4', 'data/tags/human/directions.yaml': '0e4df81e6388780abbe4c62c96f896e014043782', 'data/tags/human/ears.yaml': 'b406ad395b578d0dd21f9aa7ce574bed65535342', 'data/tags/human/face.yaml': '9d47734f61f8286e262bb21c4a5e3953764fd716', 'data/tags/human/hair.yaml': '5a6d22d78d033e51c530cc592c17a49f0984db61', 'data/tags/human/head_ornament.yaml': '3fddeb3911e49589f307f6a8f7f057f9287bb8bf', 'data/tags/human/humantype.yaml': 'd892dcd6d349f666fdbf811d62898a31876daf7f', 'data/tags/human/lowerbody.yaml': '3848dd4fb151a2251c2e1152a3f679598011409f', 'data/tags/human/neck.yaml': 'a08d1738fbad7bccb38e466de76433bb2a167e68', 'data/tags/human/quality.yaml': '440db38de977cdcbda643c4c0baa1947eb7f9cc8', 'data/tags/human/shoesock.yaml': 'a32e848753c66d0beba009110464a8035230603f', 'data/tags/human/upperbody.yaml': '33ca0deb46c7b543b57d91c55d7f8db5b8183969', 'data/tags/humanities/buildings.yaml': 'e7fdbc9ed3d4a5f61aa50a5bb613ec441a96681d', 'data/tags/humanities/cities.yaml': '5ccd7685e34ee84c581b5e6ad859aa06f31b2a6f', 'data/tags/humanities/food/breads.yaml': '4c63a2a0f622a77a7f57ebaf74459122751db989', 'data/tags/humanities/food/condiments.yaml': 'ba6f0015ccc698bb8eed44689023283ae04c4b20', 'data/tags/humanities/food/dairy.yaml': 'f6897b0c422f8957a81a41b45037dbbac7d8e380', 'data/tags/humanities/food/drink.yaml': '342da1c928cd9c9f81988dad56f55fcb9e49d758', 'data/tags/humanities/food/fruit.yaml': 'c0d684a06becf2f4c011d028b4ca76624a8dd7b3', 'data/tags/humanities/food/meal.yaml': '01c6335e61d72e31d4dbc836ec6f7e905ed726b8', 'data/tags/humanities/food/meat.yaml': '2d8f5596588af57db47c38e6efa2fba8357b2eb5', 'data/tags/humanities/food/sweets.yaml': '023fb049f4c77513d13872af900a9bb5edb0d7a1', 'data/tags/humanities/food/vegetable.yaml': '99a26aa90cd1bcbef5de4e3723d534ac6146d83b', 'data/tags/humanities/indoors.yaml': '22c9b781b0df2d9d8f2a60ade8c0bf4deb3bb114', 'data/tags/humanities/outdoors.yaml': '1ce5a67e84c84f6c726ef9663c3f14269617482b', 'data/tags/image-composition/articles.yaml': 'becf891b391c67c78beb25b318046e28032a6dc3', 'data/tags/image-composition/background.yaml': 'dd499a7c803d0edb85c41bf825f2d8d9d650767b', 'data/tags/image-composition/censorship.yaml': 'e44165ff85eaf7e470e928cb5eadb837b5bb2842', 'data/tags/image-composition/color.yaml': 'edde7868be8bc6a5d490b3666cd640c9898e2a28', 'data/tags/image-composition/composition.yaml': '6902c9481208aee682e47c2b01085e4268c66e6d', 'data/tags/image-composition/effects.yaml': '3f6ee7b5374ed3b0720d54bebd132e6e7293687d', 'data/tags/image-composition/format.yaml': '3be55aa024521a35560178e98df93353648a915a', 'data/tags/image-composition/perspective.yaml': '4a0db25b30cdc63b862422dab1b3c137f2b99e51', 'data/tags/image-composition/style.yaml': '3229bdf64543d3407927042975ca0f743347e962', 'data/tags/items.yaml': '0782b11a12a0d50a520615dcb1a512de180636f7', 'data/tags/natural/flowers.yaml': '153503588774ffb0e0f76bfac8e6dab5270911a9', 'data/tags/natural/outdoors.yaml': '591bfb7cb04def17510e848dd094f149557318f1', 'data/tags/natural/sky.yaml': '8a564f8c6b6212176ede29498f19c3f6b3ff758c', 'data/tags/restricted/r18-t1.yaml': 'aaddd65f4b866c8baaaee4fb9e521aa765f3c937', 'data/tags/restricted/r18-t2.yaml': '7e5bd7cbf5b25df7f4076ee28136b8ac0dac6bb4'}}}

SCHEMA = ['tag','zh','source_category','count','aliases','group','source_mask','translation_source','flags','source_groups','alternate_zh','source_counts']
# IDs are fixed for provenance; the first available count is the displayed count.
SOURCE_IDS = {'ffdkj':0,'DominikDoom':1,'Physton':2,'wfjsw':3}
BASELINE = '83310c41154cde087d5905f4866c1d9a0706326c'
CATEGORY_NAMES = {0:'通用待分类',1:'画师',3:'作品与版权',4:'角色',5:'元数据'}
# These are transparent heuristics, never asserted as Danbooru ratings or age facts.
GROUP_RULES = [
 ('服装与覆盖',r'\b(dress|shirt|skirt|pants|shorts|coat|jacket|uniform|suit|swimsuit|bikini|underwear|panties|bra|nude|naked|topless|bottomless|clothes|clothing|sleeves|robe|apron|sweater|hoodie|jeans|vest|kimono|leotard)\b'),
 ('发型与发色',r'\b(hair|ponytail|twintails|braid|bangs|ahoge|hime cut|bob cut)\b'),
 ('外貌与表情',r'\b(eyes|eye|face|smile|smiling|blush|tears|crying|skin|fang|teeth|mouth|tongue|lips|eyebrows|eyelashes|freckles)\b'),
 ('人物与身体',r'\b(girl|girls|boy|boys|woman|women|man|men|adult|female|male|muscle|muscular|breasts|thighs|hips|waist|navel|stomach|legs|arms|hands|fingers|feet|body|abs|tail|wings|ears)\b|^\d+(girl|boy)s?$'),
 ('饰品与鞋袜',r'\b(hat|ribbon|bow|glasses|jewelry|earrings|necklace|choker|gloves|shoes|boots|socks|stockings|pantyhose|sandals|hairband|hairclip|headphones)\b'),
 ('姿势与动作',r'\b(standing|sitting|lying|kneeling|walking|running|jumping|crouching|holding|hand on|hands on|crossed|stretching|dancing|leaning|looking|reaching|hugging|fighting)\b'),
 ('构图与视角',r'\b(view|shot|portrait|close up|full body|upper body|cowboy shot|from above|from below|from behind|perspective|pov|foreshortening|cropped|centered|profile)\b'),
 ('光影与色彩',r'\b(light|lighting|shadow|shadows|backlighting|rim lighting|sunlight|sunbeam|glow|glowing|neon|monochrome|greyscale|gradient|chromatic|colorful)\b'),
 ('场景与自然',r'\b(background|indoors|outdoors|sky|cloud|clouds|sun|moon|stars|night|day|forest|tree|trees|flower|flowers|water|ocean|beach|mountain|snow|rain|street|city|building|room|bedroom|window|church|castle|garden|grass)\b'),
 ('画风与媒介',r'\b(style|art|painting|watercolor|sketch|lineart|comic|manga|anime|pixel|realistic|photorealistic|3d|render|traditional media|digital media)\b'),
 ('物件与食物',r'\b(sword|weapon|gun|book|phone|camera|umbrella|bag|chair|table|car|bicycle|motorcycle|food|fruit|bread|cake|drink|coffee|tea|cup|bottle|plate|animal|cat|dog|bird)\b')]
GROUP_RULES = [(g,re.compile(p)) for g,p in GROUP_RULES]
# Fallback classification uses existing Chinese text, not a new translation.
ZH_GROUP_RULES = [(g,re.compile(p)) for g,p in [
 ('文字与标记',r'文字|字样|字幕|水印|文本|标志|标识|符号|用户名|对话框|签名|笔名|艺术家姓名|角色名|版权名|语音气泡|艺术家推特'),
 ('服装与覆盖',r'服装|衣服|连衣|上衣|外套|短裤|长裤|裙|衬衫|毛衣|卫衣|泳装|比基尼|内衣|胸罩|内裤|护甲|盔甲|装甲|肩带|吊带|衣领|衣袖|袖口|无袖|蕾丝|围裙|和服|浴衣|围巾|腰带|皮带|裹胸|兜裆|布料|裸露|裸体|全裸'),
 ('饰品与鞋袜',r'饰品|配饰|发夹|头饰|发带|发箍|耳环|耳钉|项链|项圈|戒指|手镯|手链|腕带|手套|帽|头盔|鞋|靴|袜|护目镜|眼镜|面具|绷带|创可贴|绑带|腿带|背包|挎包|手提包|蝴蝶结'),
 ('发型与发色',r'头发|发型|发髻|发辫|辫子|马尾|刘海|卷发|短发|长发|鬓发|鬓角|假发|呆毛|染发|秃头|发际线'),
 ('外貌与表情',r'表情|笑容|微笑|大笑|哭泣|眼泪|汗珠|汗滴|害羞|脸红|红晕|脸颊|脸型|面部|面孔|五官|瞳孔|瞳色|虹膜|异色瞳|眼睑|眼皮|眼圈|眉毛|睫毛|嘴唇|唇膏|口红|化妆|妆容|眼影|眼线|雀斑|伤疤|纹身|咬牙|吐舌|犬齿|肤色|指甲油'),
 ('人物与身体',r'身体|身材|体型|肌肉|胸肌|腹肌|胸部|乳房|肩膀|锁骨|腋下|腰部|腹部|肚脐|臀部|胯部|大腿|小腿|膝盖|脚趾|脚底|脚掌|脚踝|手指|指甲|手腕|手掌|手臂|前臂|肘部|后背|脊柱|翅膀|尾巴|兽耳|人类|女性|男性|女人|男人|女孩|男孩|少女|少年|御姐|幼女|幼男|机器人|精灵|妖精|兽人|恶魔|天使|吸血鬼|女巫|魔女|人鱼|女仆|学生|教师|护士|警察|军人|角色扮演'),
 ('姿势与动作',r'姿势|动作|站立|坐着|坐姿|躺|卧姿|跪|蹲|奔跑|跑步|跳跃|跳舞|行走|步行|鞠躬|弯腰|伸展|伸手|举手|举臂|张开|双臂|双腿|交叉|盘腿|翘腿|抬腿|抬脚|挥手|握拳|握住|抓住|拿着|扶着|撑着|捧着|抱着|拥抱|搂抱|回头|侧身|转身|趴|匍匐|凝视|仰望|俯视|低头|点头|闭眼|眨眼|翘起|抬起|捂住|抚摸|拉扯|拉着|扭腰|踢|挥舞|拔刀|举剑|持剑|持枪|射击|投掷|骑乘|骑车|驾驶|弹奏|演奏|进食|吃着|喝水|睡觉|睡眠|漂浮|飞行|倒立|运动|战斗'),
 ('构图与视角',r'构图|视角|视图|镜头|特写|肖像|全身像|半身像|上半身|下半身|全景|景深|背景虚化|透视|剪裁|裁切|侧面|正面|背面|前景|远景|近景|分屏|边框|画面分割'),
 ('光影与色彩',r'光线|光照|灯光|光源|阴影|投影|背光|逆光|侧光|轮廓光|发光|荧光|霓虹|曝光|色调|渐变|配色|色差|单色|灰度|饱和度|明暗|反光|反射|高光|色彩'),
 ('场景与自然',r'背景|场景|室内|室外|户外|房间|卧室|浴室|厨房|教室|走廊|大厅|阳台|楼梯|床上|天空|星空|夜景|城市|街道|巷道|建筑|高楼|城堡|教堂|庙宇|神社|公园|花园|庭院|游乐场|车站|机场|港口|森林|丛林|树林|树叶|树枝|树干|竹林|草地|草原|草丛|山脉|山峰|沙漠|沙滩|海滩|海洋|海底|河流|湖泊|池塘|瀑布|岩石|石头|火山|洞穴|冰川|雪地|雪花|下雪|雨滴|下雨|雷电|闪电|暴风|雾气|云朵|白云|乌云|太阳|夕阳|日落|日出|月亮|月光|夜晚|黄昏|清晨|季节|春天|夏天|秋天|冬天|花朵|花瓣|鲜花|玫瑰|樱花|百合花|蒲公英|向日葵|郁金香|植被|植物'),
 ('画风与媒介',r'画风|画法|绘画|水彩|油画|版画|素描|草稿|线稿|厚涂|平涂|像素画|水墨|写实|卡通|二次元|漫画|摄影|照片|渲染|艺术风格|传统媒体|数字媒体|艺术许可'),
 ('物件与食物',r'物件|物品|工具|家具|玩具|玩偶|布偶|抱枕|枕头|床单|毛毯|被子|窗帘|椅子|凳子|沙发|桌子|书桌|柜子|柜台|书架|武器|刀|剑|枪|长矛|弓箭|盾|子弹|炮弹|匕首|镰|斧|锤|锁链|手机|电话|相机|电视|电脑|屏幕|键盘|鼠标|耳机|麦克风|扬声器|游戏机|汽车|轿车|卡车|自行车|摩托|列车|火车|飞机|飞船|宇宙船|轮船|小船|航母|坦克|机械|引擎|齿轮|食物|食品|料理|水果|蔬菜|面包|蛋糕|饼干|糖果|巧克力|奶油|冰淇淋|甜点|面条|拉面|米饭|寿司|汉堡|披萨|肉类|肉串|牛肉|猪肉|鸡肉|鱼肉|牛奶|咖啡|红茶|绿茶|果汁|汽水|啤酒|葡萄酒|水杯|茶杯|酒杯|瓶子|餐盘|盘子|碗|筷子|叉子|勺子|苹果|香蕉|草莓|西瓜|葡萄|柠檬|番茄|土豆|胡萝卜|洋葱'),
 ('动物与生物',r'动物|生物|宠物|野兽|小猫|猫咪|小狗|狗狗|柴犬|狐狸|狼|老虎|狮子|豹子|兔子|松鼠|老鼠|豚鼠|仓鼠|熊猫|北极熊|棕熊|浣熊|鹿|长颈鹿|大象|犀牛|河马|绵羊|山羊|骆驼|斑马|骏马|乌龟|蜥蜴|鳄鱼|蟒蛇|蝴蝶|蜜蜂|昆虫|蜘蛛|蜻蜓|甲虫|蟑螂|萤火虫|蚂蚁|毛毛虫|鸟类|小鸟|麻雀|乌鸦|鹦鹉|猫头鹰|鸽子|天鹅|企鹅|鸭子|公鸡|母鸡|海鸥|老鹰|孔雀|金鱼|鲤鱼|鲨鱼|鲸鱼|海豚|章鱼|水母|海星|海豹|海狮|贝壳|螃蟹|龙虾|恐龙|巨龙'),
 ('情绪与状态',r'情绪|感情|心情|愤怒|生气|悲伤|难过|快乐|开心|兴奋|惊讶|吃惊|困惑|迷茫|焦虑|紧张|恐惧|害怕|疲惫|疲劳|困倦|无聊|失望|尴尬|自信|冷漠|严肃|认真|专注|满足|陶醉|发呆|发汗|出汗|湿润|潮湿|淋湿|湿透|沾湿|污渍|脏污|破损|撕裂|损坏|凌乱'),
 ('关系与题材',r'异性恋|同性恋|百合|爱情|恋爱|情侣|夫妻|夫妇|婚礼|结婚|约会|家庭|亲子|亲情|友情|朋友|兄弟|姐妹|母亲|父亲|姐姐|妹妹|哥哥|弟弟|女儿|儿子|儿女|赛博朋克|蒸汽朋克|科幻|幻想题材|奇幻|魔法|虚拟主播|虚拟YouTuber'),
]]
RISK_RULES = [
 ('age_term',re.compile(r'\b(child|children|childlike|underage|minor|toddler|baby|infant|teen|teenage|teenager|loli|lolicon|shota|shotacon|young girl|young boy)\b|幼女|幼男|未成年|萝莉|正太')),
 ('nonconsent_term',re.compile(r'\b(rape|raping|nonconsensual|non consensual|forced|unconscious|sleeping|drugged|molestation)\b|非自愿|强奸|迷奸')),
 ('other_high_risk_term',re.compile(r'\b(incest|bestiality|zoophilia|necrophilia)\b|乱伦|兽交|奸尸'))]
SENSITIVE = re.compile(r'\b(nsfw|r18|r 18|explicit|sexual|sex|nude|nudity|naked|topless|bottomless|nipples|nipple|penis|vagina|pussy|anus|genitals|cum|semen|masturbation|intercourse|penetration|bondage|bdsm|fetish|orgasm|erection)\b')
WF_GROUP = {'hair':'发型与发色','face':'外貌与表情','ears':'人物与身体','humantype':'人物与身体','breasts':'人物与身体','clothes':'服装与覆盖','upperbody':'服装与覆盖','lowerbody':'服装与覆盖','neck':'饰品与鞋袜','shoesock':'饰品与鞋袜','head_ornament':'饰品与鞋袜','body_ornament':'饰品与鞋袜','actions':'姿势与动作','directions':'构图与视角','perspective':'构图与视角','composition':'构图与视角','background':'场景与自然','effects':'光影与色彩','color':'光影与色彩','style':'画风与媒介','format':'画风与媒介','quality':'元数据','items':'物件与食物','characters':'角色','censorship':'内容呈现','artistic-license':'画风与媒介','articles':'元数据'}
def key(s): return re.sub(r'\s+','_',s.strip().lower())
def blobsha(b): return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def fetch_sources(cache):
    def get(job):
        owner,path,sha=job; dst=cache/owner/path
        if not dst.exists():
            dst.parent.mkdir(parents=True,exist_ok=True)
            url=f'https://raw.githubusercontent.com/{PINS[owner]["repo"]}/{PINS[owner]["commit"]}/{path}'
            for attempt in range(3):
                try:
                    with urllib.request.urlopen(url,timeout=90) as r: data=r.read()
                    if blobsha(data)!=sha: raise ValueError('Downloaded source hash mismatch: '+path)
                    dst.write_bytes(data); break
                except Exception:
                    if attempt==2: raise
        if blobsha(dst.read_bytes())!=sha: raise ValueError('Cached source hash mismatch: '+str(dst))
    jobs=[(o,p,h) for o,v in PINS.items() for p,h in v['files'].items()]
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool: list(pool.map(get,jobs))
def build_data(cache):
    import yaml
    entries={}; raw_counts={}; invalid=collections.Counter(); conflicts=[]
    def add(tag,owner,zh='',category=None,count=None,aliases=(),original_group='',group=''):
        tag=tag.strip(); k=key(tag)
        if not tag or '\n' in tag or '\r' in tag:
            invalid[owner]+=1; return
        e=entries.setdefault(k,{'tag':tag,'category':None,'translations':{},'aliases':[],'groups':[],'sources':0,'counts':{},'wf_group':''})
        sid=SOURCE_IDS[owner];e['sources']|=1<<sid
        if category is not None:
            if e['category'] is None:e['category']=int(category)
            elif e['category']!=int(category): conflicts.append([tag,'category',e['category'],int(category),owner])
        if count is not None:e['counts'][sid]=int(count)
        if zh and zh.strip():e['translations'][owner]=zh.strip()
        if tag!=e['tag'] and tag not in e['aliases']:e['aliases'].append(tag)
        for a in aliases:
            if a and a not in e['aliases'] and key(a)!=k:e['aliases'].append(a)
        if original_group and original_group not in e['groups']:e['groups'].append(original_group)
        if group:e['wf_group']=e['wf_group'] or group
    db=sqlite3.connect('file:'+str((cache/'ffdkj/tag.sqlite').resolve())+'?mode=ro',uri=True)
    rows=db.execute('SELECT name,category,cn_name,post_count FROM tags').fetchall();raw_counts['ffdkj']=len(rows)
    for tag,cat,zh,count in rows:add(tag,'ffdkj',zh or '',cat,count)
    db.close()
    rows=list(csv.reader((cache/'DominikDoom/tags/danbooru.csv').read_text().splitlines()));raw_counts['DominikDoom']=len(rows)
    for row in rows:
        if len(row)<3:invalid['DominikDoom']+=1;continue
        add(row[0],'DominikDoom',category=row[1],count=row[2],aliases=row[3].split(',') if len(row)>3 else [])
    rows=list(csv.reader((cache/'Physton/tags/danbooru.zh_CN.csv').read_text(encoding='utf-8-sig').splitlines()));raw_counts['Physton']=len(rows)
    for row in rows:
        if len(row)<2:invalid['Physton']+=1;continue
        add(row[0],'Physton',row[1])
    wf_counts=json.loads((cache/'wfjsw/data/danbooru_tag_post_count.json').read_text());raw_counts['wfjsw']=0;materials={}
    for path in sorted(PINS['wfjsw']['files']):
        if not path.startswith('data/tags/'):continue
        raw=(cache/'wfjsw'/path).read_text();materials[path]=raw;data=yaml.safe_load(raw);stem=pathlib.Path(path).stem
        for tag,v in data['content'].items():
            raw_counts['wfjsw']+=1
            group='敏感来源标签' if '/restricted/' in path else '物件与食物' if '/food/' in path else '场景与自然' if '/humanities/' in path or '/natural/' in path else WF_GROUP.get(stem,'通用待分类')
            original='/'.join([*data.get('category',[]),data['name']])
            add(tag,'wfjsw',v.get('name',''),count=wf_counts.get(tag),aliases=v.get('alias') or [],original_group=original,group=group)
    records=[];groups=collections.Counter();flags_count=collections.Counter();trans=collections.Counter();untranslated=0;ambiguous=[]
    for k,e in entries.items():
        # Preserve the database primary tag, not a prettified English phrase.
        cat=e['category'];text=k.replace('_',' ')
        group=CATEGORY_NAMES.get(cat,'通用待分类')
        if cat==0 or cat is None:
            group=e['wf_group'] or next((g for g,p in GROUP_RULES if p.search(text)),'通用待分类')
        translations=e['translations'];order=['wfjsw','ffdkj','Physton']
        translated=[o for o in order if o in translations and re.search(r'[\u3400-\u9fff]',translations[o])]
        chosen=translated[0] if translated else next((o for o in order if o in translations),'')
        zh=translations.get(chosen,'');is_zh=bool(re.search(r'[\u3400-\u9fff]',zh));untranslated+=not is_zh
        ts=chosen if is_zh else 'untranslated';trans[ts]+=1
        alt=[[o,z] for o,z in translations.items() if z!=zh]
        if is_zh and any(re.search(r'[\u3400-\u9fff]',z) and z!=zh for _,z in alt):
            ambiguous.append({'tag':e['tag'],'selected_source':chosen,'selected':zh,'alternatives':alt})
        if group=='通用待分类':
            group=next((g for g,p in ZH_GROUP_RULES if p.search(zh)),'通用待分类')
        searchable=' '.join([text,*e['aliases'],zh,*[v for _,v in alt]]).lower().replace('_',' ')
        flags=[n for n,p in RISK_RULES if p.search(searchable)]
        if cat==4 or group=='角色':flags.append('character_age_unverified')
        if any('限制级' in g for g in e['groups']):flags.append('restricted_source')
        if SENSITIVE.search(text):flags.append('sensitive_heuristic')
        flags_count.update(flags);groups[group]+=1
        counts=sorted(e['counts'].items());count=counts[0][1] if counts else 0
        records.append([e['tag'],zh,cat,count,e['aliases'],group,e['sources'],ts,flags,e['groups'],alt,counts])
    records.sort(key=lambda r:(-r[3],r[0]))
    licenses={o:(cache/o/'LICENSE').read_text() for o in PINS}
    stats={'source_rows':raw_counts,'invalid_rows':dict(invalid),'unique_tags':len(records),'merged_occurrences':sum(raw_counts.values())-sum(invalid.values())-len(records),'chinese':len(records)-untranslated,'untranslated':untranslated,'chinese_percent':round((len(records)-untranslated)*100/len(records),2),'translation_sources':dict(trans),'translation_disagreements':len(ambiguous),'category_conflicts':len(conflicts),'display_groups':dict(groups),'flags':dict(flags_count),'source_categories':dict(collections.Counter(str(r[2]) for r in records))}
    return {'format':'prompt-dictionary','version':1,'built_date':'2026-11-09','columns':SCHEMA,'default_rating':'unknown','classification_note':'Display groups and lexical flags are project heuristics; raw source categories/groups are separate. Unknown rating is never SFW. Character age is not known.','translation_note':{'ffdkj':'AI translation + community correction claimed upstream; not individually verified. README prose/table disagree on model version.','Physton':'Community translation; per-entry method/review unknown.','wfjsw':'Curated community display names; per-entry review unknown.','untranslated':'No Chinese text; original name retained, including proper names.'},'count_precedence':['ffdkj','DominikDoom','wfjsw'],'sources':{o:{**v,'id':SOURCE_IDS[o],'license_text':licenses[o]} for o,v in PINS.items()},'source_materials':{'wfjsw_yaml':materials,'note':'Corresponding editable YAML source supplied here; no images or application code included. Build/conversion source is build-prompt-dictionary.py. Other inputs are pinned MIT datasets.'},'stats':stats,'conflicts':{'categories':conflicts,'translation_examples':ambiguous[:30]},'records':records}
WORKER_JS = r'''
function dictionaryWorker(){
  const norm=s=>String(s||'').normalize('NFKC').toLowerCase().replace(/_/g,' ').replace(/\s+/g,' ').trim();
  let rows=[], index=[], tagKeys=[], groups=[], builtin=[], importId=0;
  const unpack=r=>({tag:r[0],zh:r[1]||r[0].replaceAll('_',' '),cat:r[5],count:r[3],aliases:r[4],flags:r[8],sourceCategory:r[2],translation:r[7],sourceMask:r[6],sourceGroups:r[9],countKnown:r[11].length>0});
  function prepare(input){
    rows=input;
    const seen=new Set(rows.map(r=>norm(r[0])));
    for(const b of builtin)if(!seen.has(norm(b.tag))){rows.push([b.tag,b.zh,null,0,[],b.cat,0,'builtin',[],['内置/'+(b.originalCat||b.cat)],[],[]]);seen.add(norm(b.tag))}
    rows.sort((a,b)=>b[3]-a[3]||a[0].localeCompare(b[0]));
    tagKeys=rows.map(r=>norm(r[0]));
    index=rows.map(r=>norm([r[0],r[1],r[5],...r[4],...r[10].map(a=>a[1])].join(' ')));
    groups=[...new Set(rows.map(r=>r[5]))];
  }
  function parseCsv(text){
    const lines=[];let row=[],cell='',quoted=false;
    for(let i=0;i<text.length;i++){
      const c=text[i];if(c==='"'){if(quoted&&text[i+1]==='"'){cell+='"';i++}else quoted=!quoted}
      else if(c===','&&!quoted){row.push(cell);cell=''}
      else if((c==='\n'||c==='\r')&&!quoted){row.push(cell);if(row.some(x=>x.trim()))lines.push(row);row=[];cell='';if(c==='\r'&&text[i+1]==='\n')i++}
      else cell+=c;
    }
    if(quoted)throw Error('CSV 引号未闭合');
    row.push(cell);if(row.some(x=>x.trim()))lines.push(row);
    if(/^(tag|name)$/i.test(lines[0]?.[0]?.replace(/^\uFEFF/,'').trim()))lines.shift();
    const cats={0:'通用待分类',1:'画师',3:'作品与版权',4:'角色',5:'元数据'};const seen=new Set();const out=[];
    for(const c of lines){const tag=c[0].replace(/^\uFEFF/,'').trim();if(!tag||seen.has(norm(tag)))continue;seen.add(norm(tag));
      const n=Number(c[2]||0);if(!Number.isFinite(n)||n<0)throw Error('CSV 频次不是非负数');
      out.push([tag,c[4]?.trim()||'',Number(c[1]||0),n,(c[3]||'').split(',').filter(Boolean),cats[c[1]]||'通用待分类',0,'csv_unverified',[],[],[],[]]);}
    return out;
  }
  function search(m){
    const started=performance.now(),tokens=norm(m.q).split(' ').filter(Boolean),q=norm(m.q);
    const fav=new Set(m.favorites.map(norm)),selected=new Set(m.selected.map(norm)),common=new Set(builtin.map(b=>norm(b.tag)));
    const buckets=[[],[],[]];let count=0;
    for(let i=0;i<rows.length;i++){
      const r=rows[i],key=tagKeys[i];
      if(m.cat!=='全部'&&r[5]!==m.cat||m.favoritesOnly&&!fav.has(key)||m.commonOnly&&!common.has(key)||m.sourceCategory!==''&&String(r[2])!==m.sourceCategory)continue;
      const flags=r[8];if(m.flag&&!(m.flag==='risk'?flags.some(f=>/age_term|nonconsent|high_risk/.test(f)):flags.includes(m.flag)))continue;
      if(!tokens.every(t=>index[i].includes(t)))continue;
      count++;const zh=q?norm(r[1]):'';const rank=q&&(key===q||zh===q||r[4].some(a=>norm(a)===q))?0:q&&(key.startsWith(q)||zh.startsWith(q))?1:2;
      if(buckets[rank].length<120)buckets[rank].push({...unpack(r),chosen:selected.has(key),favorite:fav.has(key)});
    }
    postMessage({type:'results',seq:m.seq,count,total:rows.length,items:buckets.flat().slice(0,120),searchMs:performance.now()-started});
  }
  onmessage=async({data:m})=>{
    try{
      if(m.type==='init'){builtin=m.builtin;prepare([]);postMessage({type:'ready',groups,total:rows.length});}
      if(m.type==='reset'){importId++;prepare([]);postMessage({type:'loaded',groups,total:rows.length,stats:null,loadMs:0});}
      if(m.type==='load'){
        const id=++importId,started=performance.now();let text;
        if(m.file.size>100*1024*1024)throw Error('文件超过 100 MiB，请先检查文件内容');
        if(m.file.name.toLowerCase().endsWith('.gz')){
          if(typeof DecompressionStream==='undefined')throw Error('此浏览器不支持 gzip，请先解压为 .json 再导入');
          const reader=m.file.stream().pipeThrough(new DecompressionStream('gzip')).getReader();const chunks=[];let bytes=0;
          for(;;){const v=await reader.read();if(v.done)break;bytes+=v.value.length;if(bytes>200*1024*1024){await reader.cancel();throw Error('解压后超过 200 MiB')}chunks.push(v.value)}
          text=await new Blob(chunks).text();
        }else text=await m.file.text();
        let imported,stats=null;
        if(/^\s*\{/.test(text)){
          const d=JSON.parse(text);if(d.format!=='prompt-dictionary'||d.version!==1||!Array.isArray(d.records)||JSON.stringify(d.columns)!==JSON.stringify(COLUMNS))throw Error('不是支持的词库格式或字段版本');
          imported=d.records;stats=d.stats;
          const seen=new Set();
          for(const r of imported){if(!Array.isArray(r)||r.length!==12||typeof r[0]!=='string'||!r[0].trim()||typeof r[1]!=='string'||typeof r[5]!=='string'||typeof r[3]!=='number'||!Number.isFinite(r[3])||r[3]<0||![4,8,9,10,11].every(i=>Array.isArray(r[i]))||seen.has(norm(r[0])))throw Error('词库含无效或重复记录');seen.add(norm(r[0]));}
        }else imported=parseCsv(text);
        if(!imported.length)throw Error('文件没有有效标签');
        if(id!==importId)return;
        prepare(imported);
        const wanted=new Set(m.selected.map(norm));const selectedItems=rows.filter(r=>wanted.has(norm(r[0]))).map(unpack);
        postMessage({type:'loaded',groups,total:rows.length,stats,selectedItems,loadMs:performance.now()-started});
      }
      if(m.type==='search')search(m);
    }catch(e){postMessage({type:'error',message:e.message})}
  };
}
'''
INTEGRATION_JS = r'''
const dictionaryMetrics={loads:[],searches:[]};window.dictionaryMetrics=dictionaryMetrics;
const normTag=s=>String(s).normalize('NFKC').toLowerCase().replace(/_/g,' ').replace(/\s+/g,' ').trim();
let dictionaryGroups=[...new Set(builtin.map(x=>x.cat))],dictionarySequence=0;
const dictLookup=new Map(builtin.map(x=>[normTag(x.tag),x]));
const workerURL=URL.createObjectURL(new Blob(['const COLUMNS='+JSON.stringify(DICTIONARY_COLUMNS)+';('+dictionaryWorker.toString()+')()'],{type:'text/javascript'}));
const dictionary=new Worker(workerURL);URL.revokeObjectURL(workerURL);
function failDictionary(message){el('tagImportStatus').textContent='词库操作失败：'+message;el('tagCsv').disabled=false;el('tagResetLib').disabled=false;}
dictionary.onerror=e=>failDictionary(e.message||'后台线程无法启动，请使用 Chrome/Edge 或 Firefox');
dictionary.onmessage=({data:m})=>{
  if(m.type==='error'){failDictionary(m.message);return}
  if(m.type==='ready'||m.type==='loaded'){
    dictionaryGroups=m.groups;activeCat='全部';dictionarySequence++;
    if(m.type==='loaded'){
      dictLookup.clear();builtin.forEach(x=>dictLookup.set(normTag(x.tag),x));(m.selectedItems||[]).forEach(x=>dictLookup.set(normTag(x.tag),x));
      dictionaryMetrics.loads.push({total:m.total,loadMs:m.loadMs});
      el('tagImportStatus').textContent=m.stats?`已加载 ${m.stats.unique_tags.toLocaleString()} 条公开标签；中文覆盖 ${m.stats.chinese_percent}%。译文未经逐条核实；刷新后需重新选择文件。`:'已恢复内置标签库；CSV 导入只保留文件已有元数据。';
      el('tagCsv').disabled=false;el('tagResetLib').disabled=false;
    }
    renderCats();renderSelected();renderChoices();
  }
  if(m.type==='results'&&m.seq===dictionarySequence){
    dictionaryMetrics.searches.push(m.searchMs);if(dictionaryMetrics.searches.length>100)dictionaryMetrics.searches.shift();
    const limit=matchMedia('(max-width:820px)').matches?60:120;
    el('tagCount').textContent=`匹配 ${m.count.toLocaleString()} / ${m.total.toLocaleString()} 个标签；显示前 ${limit} 个，按精确匹配、前缀和来源频次排序`;
    const keep=new Set([...selected,...favorites].map(normTag));
    for(const k of dictLookup.keys())if(!keep.has(k))dictLookup.delete(k);
    builtin.forEach(x=>{if(!dictLookup.has(normTag(x.tag)))dictLookup.set(normTag(x.tag),x)});
    const root=el('tagChoices');root.replaceChildren();
    for(const item of m.items.slice(0,limit)){
      dictLookup.set(normTag(item.tag),item);
      const wrap=document.createElement('div');wrap.style.cssText='display:flex;align-items:stretch;gap:2px;max-width:100%';
      const b=document.createElement('button');b.className='tag-chip'+(item.chosen?' chosen':'');
      const risk=item.flags.some(f=>/age_term|nonconsent|high_risk/.test(f));
      b.disabled=risk;
      b.title=[item.cat,'源类别：'+(item.sourceCategory??'未知'),'频次：'+(item.countKnown?item.count:'未知（排序按0）'),'译文：'+item.translation,'分级：未知',...item.flags,...item.sourceGroups].join(' · ');
      const label=document.createElement('span');label.textContent=(risk?'资料标识 · ':item.chosen?'✓ ':'＋ ')+item.zh;
      const en=document.createElement('small');en.textContent=item.tag;
      if(item.flags.length){const f=document.createElement('small');f.textContent=risk?'年龄／非自愿等风险词，仅供分类核查':item.flags.includes('character_age_unverified')?'角色年龄未核实':item.flags.includes('restricted_source')?'来源标有限制级':'可能敏感（规则匹配）';b.append(label,en,f)}else b.append(label,en);
      b.onclick=()=>toggle(item.tag);
      const fav=document.createElement('button');fav.className='tag-chip';fav.textContent=item.favorite?'★':'☆';fav.title='收藏/取消收藏';fav.setAttribute('aria-label',item.zh+' 收藏');
      fav.onclick=()=>{const existing=[...favorites].find(t=>normTag(t)===normTag(item.tag));existing?favorites.delete(existing):favorites.add(item.tag);save();renderChoices()};
      wrap.append(b,fav);root.append(wrap);
    }
  }
};
dictionary.postMessage({type:'init',builtin});
el('tagCsv').addEventListener('change',ev=>{const file=ev.target.files?.[0];if(!file)return;el('tagCsv').disabled=true;el('tagResetLib').disabled=true;el('tagImportStatus').textContent='正在后台加载与建立索引……';dictionary.postMessage({type:'load',file,selected})});
el('tagResetLib').onclick=()=>{el('tagCsv').value='';dictionary.postMessage({type:'reset'})};
el('tagSourceCategory').onchange=renderChoices;el('tagFlag').onchange=renderChoices;
'''
def build_html(stable):
    html=stable.decode('utf-8')
    html=html.replace('<title>绘画提示词生成器','<title>独立词库测试 · 绘画提示词生成器',1)
    html=html.replace('<h1>✨ 绘画提示词生成器</h1>','<h1>绘画提示词生成器 · 独立词库测试</h1>')
    html=html.replace('单文件离线使用 · 无需 API','离线使用 · 导入完整词库 · 独立测试版')
    html=html.replace("promptMakerInputs'","promptMakerDictionaryTestInputs'").replace("promptMakerTagsV1'","promptMakerDictionaryTestTagsV1'")
    html=html.replace('accept=".csv,.txt,text/csv"','accept=".gz,.json,.csv,.txt,application/gzip,application/json,text/csv"')
    html=html.replace('导入公开 Danbooru 标签 CSV（本地文件，支持中英译名；不上传）','选择 prompt-dictionary.json.gz（也支持解压后的 JSON 或标准四/五列 CSV；本地读取）')
    html=html.replace('内置常用标签，可选导入大型公开标签库。','先选择完整词库文件。收藏与已选词独立保存；当前数据未经模型理解度或逐条译文验证。')
    html=html.replace('<div id="tagCats"', '<div class="tag-controls"><label>源类别 <select id="tagSourceCategory"><option value="">全部</option><option value="0">通用</option><option value="1">画师</option><option value="3">作品</option><option value="4">角色</option><option value="5">元数据</option></select></label><label>内容标识 <select id="tagFlag"><option value="">全部（分级未知）</option><option value="restricted_source">来源限制级</option><option value="sensitive_heuristic">敏感规则匹配</option><option value="risk">年龄／非自愿等风险词</option><option value="character_age_unverified">角色年龄未核实</option></select></label></div><p class="tip">显示分类为整理结果，源类别另存。内容标识并非完整安全审核；角色年龄未知，风险词仅作资料展示。</p><div id="tagCats"',1)
    html=html.replace('</head>','<style>.tag-chip{overflow-wrap:anywhere}.tag-chip:disabled{opacity:.6;cursor:default}select{max-width:100%;background:#101724;color:#e9efff;border:1px solid #35445d;border-radius:6px;padding:8px}.tag-search input{width:0}</style></head>')
    old=re.search(r'function renderChoices\(\).*?(?=\nel\(\x27tagFavoritesOnly\x27)',html,re.S)
    assert old, 'Baseline search function changed'
    render="""function renderChoices(){if(typeof dictionary==='undefined')return;dictionary.postMessage({type:'search',seq:++dictionarySequence,q:el('tagSearch').value,cat:activeCat,favorites:[...favorites],selected,favoritesOnly,commonOnly,sourceCategory:el('tagSourceCategory').value,flag:el('tagFlag').value})}"""
    html=html[:old.start()]+render+html[old.end():]
    html=html.replace("['全部',...new Set(LIB.map(x=>x.cat))]","['全部',...dictionaryGroups]")
    html=html.replace("const item=LIB.find(x=>x.tag===tag);const b=", "const item=dictLookup.get(normTag(tag))||LIB.find(x=>x.tag===tag);const b=")
    html=html.replace("selected=selected.includes(tag)?selected.filter(t=>t!==tag):[...selected,tag]","selected=selected.some(t=>normTag(t)===normTag(tag))?selected.filter(t=>normTag(t)!==normTag(tag)):[...selected,tag]")
    # Clear stale raw-category / flag filters when restoring built-ins; searches still apply normally.
    start=html.index("el('tagCsv').addEventListener('change',async ev=>")
    end=html.index('let searchTimer;',start)
    html=html[:start]+html[end:]
    injection='const DICTIONARY_COLUMNS='+json.dumps(SCHEMA)+';\n'+WORKER_JS+'\n'+INTEGRATION_JS
    builtin_groups={'上衣':'服装与覆盖','上装与外套':'服装与覆盖','下装与裙装':'服装与覆盖','主体人数':'人物与身体','人物年龄与体型':'人物与身体','光影与天气':'光影与色彩','光影与氛围':'光影与色彩','动作与手势':'姿势与动作','动作姿势':'姿势与动作','发型':'发型与发色','发色':'发型与发色','场景地点':'场景与自然','头发与发饰':'发型与发色','幻想与世界观':'关系与题材','时间与天气':'场景与自然','服装覆盖与造型':'服装与覆盖','服装风格':'服装与覆盖','画面质量与元信息':'元数据','眼睛与脸部':'外貌与表情','眼睛与面部':'外貌与表情','绘画风格':'画风与媒介','表情':'外貌与表情','视线与构图':'构图与视角','赛博朋克与科幻':'关系与题材','身形特征':'人物与身体','道具与效果':'物件与食物','镜头与构图':'构图与视角','鞋袜与配饰':'饰品与鞋袜','风格与画面质感':'画风与媒介'}
    builtin_code='const builtin=LIB.map(x=>({...x,originalCat:x.cat,cat:'+json.dumps(builtin_groups,ensure_ascii=False)+'[x.cat]||x.cat}));'
    html=html.replace('const builtin=LIB.slice();',builtin_code+'\n'+injection,1)
    html=html.replace("el('tagResetLib').onclick=()=>{el('tagCsv').value='';dictionary.postMessage({type:'reset'})}","el('tagResetLib').onclick=()=>{el('tagCsv').value='';el('tagSourceCategory').value='';el('tagFlag').value='';dictionary.postMessage({type:'reset'})}")
    return html

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--cache',type=pathlib.Path,default=pathlib.Path('/tmp/prompt-dictionary-sources'));ap.add_argument('--out-dir',type=pathlib.Path,default=pathlib.Path(__file__).resolve().parent);args=ap.parse_args()
    source=pathlib.Path(__file__).resolve().parent/'prompt-generator.html';stable=source.read_bytes()
    if blobsha(stable)!=BASELINE:raise SystemExit('Stable baseline changed; review the integration patch before rebuilding. Stable file was not modified.')
    fetch_sources(args.cache);data=build_data(args.cache)
    args.out_dir.mkdir(parents=True,exist_ok=True)
    raw=json.dumps(data,ensure_ascii=False,separators=(',',':')).encode();compressed=gzip.compress(raw,compresslevel=9,mtime=0)
    (args.out_dir/'prompt-dictionary.json.gz').write_bytes(compressed)
    (args.out_dir/'prompt-generator-dictionary-test.html').write_text(build_html(stable),encoding='utf-8')
    print(json.dumps({'stats':data['stats'],'json_bytes':len(raw),'gzip_bytes':len(compressed),'gzip_sha256':hashlib.sha256(compressed).hexdigest(),'stable_blob_sha':blobsha(source.read_bytes())},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
