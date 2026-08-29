-- Text knowledge snapshots from https://wiki.deuterium.cafe/.
-- TacZ pages are intentionally excluded.

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_home', 'wiki', '欢迎来到中央图文馆',
       'Deuterium,中央图文馆,服务器介绍,公益服务器,Deuterium VIII,官网,QQ群,反馈',
       '来源：https://wiki.deuterium.cafe/zh/home
Deuterium 中央图文馆是服务器管理组主导建设的综合性知识库，收录 Deuterium VIII 服务器相关内容。Deuterium VIII 是持续运营中的综合型纯公益 Minecraft 服务器，强调精心设计、稳定内容产出、性能保障、多样化玩法和富有经验的运营团队。中央图文馆与相关站点仅代表当前 Deuterium VIII 官方站点，不代表此前所有 Deuterium 系列服务器。',
       5.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_home');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_qanda', 'wiki', '服务器全流程常见问题解答',
       '常见问题,进服,JDK17,JDK21,客户端,Yes Steve Model,Distant Horizons,建筑,新手礼包,邀请码,信用点,抽奖,AllMusic,点歌',
       '来源：https://wiki.deuterium.cafe/zh/Q&A
进服流程：需要正版账号，下载群文件中的 Deuterium VIII 客户端，正确解压，在 Deuterium_VIII 文件夹运行「点我手动自动更新.exe」更新到最新版本，并确认有 JDK17 或以上，推荐 JDK21。客户端异常可先重新解压。Yes Steve Model 模型为空通常是模型文件传输需要 1-5 分钟；模型黑块可尝试进入单人再回服，或清理 yes_steve_model/cache。Distant Horizons 需下载可选模组后启用，LOD 距离、质量预设和 CPU 性能占用会明显影响帧率。建筑原则上可在坐标 -2560 至 10752 内且无他人领地处自由建设，Carlander 新岛屿和异世界黑海岸建筑需审批。新手礼包在物品栏左上角任务/FTB 任务界面领取。邀请码用 /yq Accept 邀请码，双方可获得 1000 信用点。信用点可通过在线奖励获取，普通在线每小时 70 信用点，节日活动可能翻倍。抽奖券可在出生点附近商店购买并在 NPC 小橘处兑换抽奖道具。AllMusic 点歌常用 /music search、/music select、/music list、/music cancel、/music vote、/music stop、/music mute。',
       6.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_qanda');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_recruitment', 'wiki', '一起共创更好的服务器',
       '共创,招募,wiki维护,中央图文馆,贡献者,Markdown,Wiki.js',
       '来源：https://wiki.deuterium.cafe/zh/Recruitment
中央图文馆维护者需要具备文字编辑能力、清晰表达、逻辑性，熟悉 Deuterium 游戏内容与玩家社区，无历史封禁记录，了解 Markdown / Wiki.js 基础编辑方式，能核对玩法、活动、团队与资料信息，避免过期或未确认内容。主要工作包括更新页面、清理过期重复内容、补充教程活动记录和社群资料、整理标签和图片素材、与管理员及内容提供者核对信息。参与者可加入服务器贡献者名单，熟悉后获得 Wiki 上传权限和游戏内贡献者称号。',
       4.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_recruitment');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_getop', 'wiki', '服务器权限申请详细规则',
       '权限申请,创造权限,OP权限,机械动力,高危物品,建筑,投影,处罚',
       '来源：https://wiki.deuterium.cafe/zh/GetOP
创造权限申请条件：注册满 7 天、累计在线 20 小时以上、无恶意违规、账号状态正常、具备独立完成一定完成度建筑或内饰能力，团队施工可由符合条件玩家担保，原则上单次申请不超过 168 小时。创造权限仅能用于合法建筑创作，不能刷物品、修改物品属性、协助他人获取违规物品或权限。OP 权限原则上当前不接受新申请。机械动力高危物品申请要求注册满 15 天、累计在线 40 小时以上、理解机械动力主流机械原理，具备卡顿评估和优化能力，禁止制造恶意漏洞机器或造成服务器卡顿的机器。申请需填写腾讯文档表格，投影前应先在单人存档检查，非原创建筑需审核。',
       5.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_getop');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_history', 'wiki', 'Deuterium服务器发展历程',
       'Deuterium,历史,整合包,Deuterium VIII,公益服务器,1.20.1,xiwanzi,Tomoria_neko,Eveleth',
       '来源：https://wiki.deuterium.cafe/zh/history
Deuterium 最初是 Ginsway 制作的基础优化整合包，使用 Rubidium / Embeddium / Sodium 等作为主力优化模组，追求兼容性强、开箱即用。Deuterium 系列早期服务器使用该整合包作为优化底包，因此得名。Deuterium 1 至 7 为历史周目，不一定由当前 Deuterium VIII 团队运营。Deuterium5 Rebirth 由 xiwanzi 与 Eveleth 制作，用于探索新技术与玩法。Deuterium VIII 由 xiwanzi、Tomoria_neko、Eveleth 共同开发，目标是突破短周目命运，制作精心设计、制作精良、耐心打磨的纯公益服务器；当前运营中，版本为 1.20.1。',
       4.50, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_history');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_jzbs', 'wiki', 'Summer Pockets 服务器夏季建筑赛',
       'Summer Pockets,夏季建筑赛,建筑赛,活动,投稿,奖项,归档',
       '来源：https://wiki.deuterium.cafe/zh/jzbs
「Summer Pockets」26S1 服务器夏季建筑赛为已结束活动，页面保留作归档和奖项说明。活动接受个人和团队参赛，无需报名，在期限内提交作品与创作者信息即可。活动时间为 2026-04-14 00:00:00 至 2026-05-15 23:59:59。主题围绕阳光、色彩、海风、蝉鸣、雪花、月光、炉火、热茶等季节意象。',
       4.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_jzbs');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_railway', 'wiki', 'Deuterium VIII 铁路设计规范',
       '铁路,铁路设计规范,交通,主干线,I级铁路,II级铁路,车站,施工标准',
       '来源：https://wiki.deuterium.cafe/zh/TB-00001-2025
Deuterium VIII 铁路设计规范用于规范服务器铁路施工标准，提升铁路系统安全性和效率，为铁路工程规划、设计与建设提供统一技术依据。I 级铁路是铁路网骨干，如服务器主干线，通常为宽轨，最低使用标准宽度铁轨，暂不接受玩家主动申报修建。II 级铁路服务较大区域，可承担支线和区域交通功能。铁路建设应兼顾线路标准、车站、区间、桥隧和景观融合。',
       4.50, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_railway');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_ban', 'wiki', '严重违规通报',
       '违规通报,封禁,刷信用点,违规物品,服务器规则,管理底线,Sky_Shimo,时沫',
       '来源：https://wiki.deuterium.cafe/zh/Ban
严重违规通报页面记录玩家时沫（Sky_Shimo）的严重违规行为，包括违规刷取信用点、恶意刷取违规物品、意图分裂服务器等。页面强调老玩家也应遵守服务器基本规则、管理底线和社区共识。对创造、OP、权限、钱包、信用点、违规物品等问题应严格遵守服务器公开规则，AI 不得协助玩家规避处罚、刷取资源、修改额度、修改权限或操作钱包。',
       4.50, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_ban');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_player_team', 'wiki', 'Deuterium VIII 玩家团队简介',
       '玩家团队,团队总览,春漫狸行,避风港,歌赫娜,鸽子咕咕咕,自由玩家,玩家照片墙',
       '来源：https://wiki.deuterium.cafe/zh/player-team
玩家团队总览汇总 Deuterium VIII 已公开归档的玩家团队与自由玩家资料，是团队分页面统一入口。已归档分队包括春漫狸行、致那永远的避风港、歌赫娜、鸽子咕咕咕，以及自由玩家资料入口。页面用于索引、统计、代表展示和维护记录，公开资料按页面独立维护。',
       4.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_player_team');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_team_chunman', 'wiki', 'Deuterium VIII 春漫狸行玩家团队简介',
       '春漫狸行,玩家团队,furrryqr,knd_qx,MiHuoR,muyudongji,团队成员',
       '来源：https://wiki.deuterium.cafe/zh/team-chunman
春漫狸行玩家团队资料按负责人、管理成员与成员名单展示。负责人 furrryqr 是春漫狸行队长。管理成员包括 knd_qx（春漫建筑师傅，负责建筑设计规划）和 MiHuoR（春漫机器师傅，负责机魂）。页面保留公开资料结构，并对栏目和占位文本做官方化整理。',
       4.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_team_chunman');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_team_bifenggang', 'wiki', 'Deuterium VIII 致那永远的避风港玩家团队简介',
       '致那永远的避风港,避风港,玩家团队,suuuuee,lingSongcn,pinzjiu,Yukiko_01,团队成员',
       '来源：https://wiki.deuterium.cafe/zh/team-bifenggang
致那永远的避风港玩家团队资料按负责人、管理成员与成员名单展示。负责人 suuuuee 为避风港队长。管理成员包括 lingSongcn、pinzjiu、Yukiko_01 等。成员名单包含 Mys_word、Edenms_shuai、Katoumegumi617、Summer_Miku、Lanternors、Anzy_2233、kirto_ing、yushuida、Mr_YILIANG、luoyinwuchen、Doctor_Lin 等公开资料。',
       4.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_team_bifenggang');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_team_gehenna', 'wiki', 'Deuterium VIII 歌赫娜玩家团队简介',
       '歌赫娜,玩家团队,LinAsteaia,ElmastorVox,KuLiTian,Endersama1,nuonuoke',
       '来源：https://wiki.deuterium.cafe/zh/team-gehenna
歌赫娜玩家团队资料按负责人、管理成员与成员名单展示。公开资料描述为 D8 最神秘的团队，也许可以在任何地方看见成员，也可以寻求他们的帮助。负责人 LinAsteaia 是歌赫娜风纪委员会会长。管理成员包括 ElmastorVox 和 KuLiTian。成员名单包括 Endersama1、nuonuoke 等。',
       4.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_team_gehenna');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_team_gugugu', 'wiki', 'Deuterium VIII 鸽子咕咕咕玩家团队简介',
       '鸽子咕咕咕,玩家团队,MapleBleak,rui_mao_moa,nancho1175',
       '来源：https://wiki.deuterium.cafe/zh/team-gugugu
鸽子咕咕咕玩家团队资料按公开资料分组展示。负责人 MapleBleak 资料暂未补充。成员名单包括 rui_mao_moa（资料暂未补充）和 nancho1175（建筑见习，自由人，偶尔建点感兴趣的东西）。页面保留公开资料结构并作官方化整理。',
       4.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_team_gugugu');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_free_players', 'wiki', 'Deuterium VIII 自由玩家简介',
       '自由玩家,玩家资料,非固定团队,alex',
       '来源：https://wiki.deuterium.cafe/zh/free-players
自由玩家资料页是非固定团队玩家资料的独立归档入口。当前公开资料包含 alex，部分栏目仍为公开资料暂未补充。页面保留公开资料结构并对栏目与占位文本作官方化整理。',
       3.50, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_free_players');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_image', 'wiki', '玩家照片墙',
       '照片墙,图集,生日合照,狂感觉,白毛萝莉,空中仓库,KAF_aa,Bailuo_rain,8_6_2_0',
       '来源：https://wiki.deuterium.cafe/zh/image
玩家照片墙页面归档服务器公开图集：2026-04-23 生日合照纪念，2026-05-23 狂感觉（上传者 KAF_aa），2026-05-21 白毛萝莉专辑（上传者 Bailuo_rain），2026-05-23 空中仓库（上传者 8_6_2_0），2026-05-29 已严肃上传照片墙等。',
       3.50, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_image');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_mna', 'wiki', '魔法艺术3：巧工魔艺讲解',
       '魔法艺术3,巧工魔艺,MNA,自然资源,温特姆,觉苏莲,根愈兰,奥蓝堇,沙漠新星,塔玛根,秘鸣宝石,织魔,符文熔炉',
       '来源：https://wiki.deuterium.cafe/zh/mna
魔法艺术3：巧工魔艺基础讲解帮助玩家理解晋升路线和自然生成资源。温特姆原矿在 Y=-4 以上生成，需铁镐及以上挖掘，得到温特姆毛坯，煅烧为温特姆粉末，与铁锭合成温特姆覆膜铁锭。觉苏莲少量生成于自然水面，用于奥术复合物；根愈兰生成于草原和森林；奥蓝堇在草原森林大量生成；沙漠新星生成于沙漠；塔玛根在沼泽大量生成，直接采摘会伤害玩家，也可制作褐色染料。秘鸣宝石可从战利品箱或符合魔法等级后挖矿获得，魔法挖掘可提高概率。织魔是合成、激活仪式等重要机制，形式包括手动织魔、引导织魔、瓶中魔织和织魔放映机。符文熔炉无需燃料，可一次煅烧 16 个同类物品，并可通过左右基座和秘鸣晶体升级矿物双倍化、物品修复、烧炼加速等特殊功能。',
       5.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_mna');

INSERT INTO ai_knowledge_items (id, category, title, keywords, content, weight, active, created_at, updated_at)
SELECT 'know_wiki_yuushya', 'wiki', '方块小镇建模',
       '方块小镇,建模,Yuushya,实体方块,建模终端,图层,位移,旋转,缩放,碰撞箱',
       '来源：https://wiki.deuterium.cafe/zh/yuushya
方块小镇建模教程介绍实体方块建模入门。需要建模工具，获取方式可填写 Deuterium VIII OP 申请，申请理由写获取方块建模工具，或联系管理员。实体方块可在主城对应颜色箱子中取出，拆实体方块需要对应工具。建模时手持实体方块右键放置，再用建模终端右键打开操作页面。材料置于副手后可添加方块状态到图层列表；每个图层可分别调整位移、旋转、缩放。位移代表 X/Y/Z 坐标调整；旋转是以原点按轴旋转；缩放可按轴放大或缩小。可通过复制分享码查看 JSON 数据，Pos 表示位移，Rot 表示旋转，Scales 表示大小。不要把数据改得过于离谱。新版实体方块支持碰撞箱调整，可添加碰撞箱。',
       5.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_items WHERE id = 'know_wiki_yuushya');
