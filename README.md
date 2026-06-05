# ACT0-Arcade

ACT0 街机对战玩法模组（Forge 1.20.1 / Forge 47.4.10，Java 17）。

提供统一配装系统与四种街机对战模式：**单挑 1v1、2v2、团队死斗、个人乱斗**。
设计文档见 [../docs/ARCADE_MODES_DESIGN.md](../docs/ARCADE_MODES_DESIGN.md)。

> 独立、可编译、可单测的全新实现。配装与多玩法框架为净室自研
> （见 [../docs/CLEAN_ROOM_POLICY.md](../docs/CLEAN_ROOM_POLICY.md)）。

## 模块结构

```
org.shee33.act0.arcade
├── Act0Arcade            主模组类（注册 MatchManager / 配置）
├── ArcadeConfig          Forge 配置
├── loadout/              配装核心（MC-free，可单测）
│   ├── PlayerClassType   兵种：ASSAULT/SUPPORT/ENGINEER/RECON
│   ├── LoadoutSlot       六槽：主/副武器、近战、道具×2、投掷物
│   ├── LoadoutRuleset    规则集：FULL（全槽）/ ARCADE（仅核心战斗槽，无缝禁用兵种道具）
│   ├── LoadoutItem       装备定义（key/槽位/允许兵种/解锁等级/SNBT 或工厂）
│   ├── LoadoutRegistry   装备注册表（按槽位/兵种/等级筛选）
│   ├── Loadout           玩家的配装选择（只读）
│   ├── LoadoutResolution 解析结果（每槽 GRANTED/FILTERED_BY_RULESET/…）
│   ├── LoadoutRuleEngine 纯逻辑解析器（只读 Loadout，绝不回写）
│   └── mc/LoadoutApplier MC 桥：SNBT → ItemStack → 玩家快捷栏（支持 TaCZ 真枪）
├── round/                回合框架（MC-free，可单测）
│   ├── RespawnPolicy     FIXED_SPAWN / NEAR_TEAMMATE / RANDOM
│   ├── RoundFormat       赛制：bestOf(N) / firstTo(N) → 统一胜利阈值
│   ├── MatchScore        比分板 + 赛点/胜者判定
│   ├── MatchPhase        阶段机：WAITING→COUNTDOWN→COMBAT→ROUND_RESULT→…→ENDED
│   └── PhaseTimer        刻级倒计时（到点边沿触发）
├── mode/                 模式描述层（MC-free，可单测）
│   ├── ScoringMode       ROUND_WIN（决斗）/ KILL_COUNT（死斗）
│   └── MatchSettings     模式描述符 + 四模式预设
├── arena/                竞技场数据（MC-free）
│   ├── SpawnPoint        维度 + 坐标 + 朝向
│   └── ArcadeArena       按方出生点 / 随机复活池 / 返回点 / 校验
├── storage/              持久化（Forge SavedData，随存档落盘）
│   ├── LoadoutCodec      Loadout ↔ NBT
│   ├── ArcadeLoadoutStore 按玩家 UUID 持久化配装（街机/战地共享）
│   ├── ArenaCodec        ArcadeArena/SpawnPoint ↔ NBT
│   ├── ArenaRegistry     按 arenaId 持久化竞技场
│   └── LoadoutCatalogIO  装备目录 JSON 数据驱动读写（config/act0_arcade/loadout/*.json）
├── command/              接入层
│   └── ArcadeCommand     /arcade arena|loadout|start|stop|list|browse|reload
├── network/              网络
│   ├── ArcadeNetwork     SimpleChannel + 数据包注册 + 目录同步入口
│   ├── OpenLoadoutPacket S→C 打开配装界面
│   ├── SaveLoadoutPacket C→S 保存配装
│   ├── SyncCatalogPacket S→C 下发装备目录（登陆/重载时）
│   └── OpenBrowserPacket S→C 打开游戏浏览器
├── client/               客户端（仅客户端加载）
│   ├── ClientCatalog     客户端装备目录缓存 + 预解析图标 ItemStack
│   ├── ClientLoadoutScreenOpener
│   ├── ClientBrowserScreenOpener
│   └── screen/
│       ├── PixelTheme    像素风配色 + 程序化九宫格面板（无需贴图）
│       ├── LoadoutScreen 像素风配装编辑界面（渲染真实物品图标）
│       └── ModeBrowserScreen 像素风游戏浏览器（模式/竞技场选择）
└── match/                对局运行时（MC 层）
    ├── ArcadeServices    服务持有者（注册表/应用器/管理器/队列）
    ├── TeleportHelper    维度解析 + 跨维度传送
    ├── ArcadeMatch       通用对局编排（单一实现驱动全部四模式）+ Title/ActionBar/Bossbar/音效 反馈
    ├── MatchScoreboard   每对局私有侧边栏计分板（直接发包，不污染全局计分板）
    ├── MatchLauncher     启局工厂（命令与队列共用：查场/构模式/校验/登记/开局）
    ├── MatchQueue        匹配队列（按 模式@竞技场 排队，凑够人数自动开局）
    └── MatchManager      Forge 事件路由（ServerTick + LivingDeath）+ 对局回收
```

## 对局反馈 UI

`ArcadeMatch` 在关键节点给参战玩家推送可见反馈：

- **Title/副标题**：准备倒计时、逐秒 3-2-1 大数字、「战斗开始」、回合胜负、「胜利」。
- **音效**：倒计时滴答（音高递增）、开战号、击杀确认、胜利提示（仅推送给参战玩家，不受距离影响）。
- **ActionBar**：击杀 +1 / 阵亡重生倒计时。
- **Bossbar**：顶部血条集中显示阶段、比分与倒计时进度（倒计时按时间、战斗按比分）。
- **侧边栏计分板**：实时显示赛制目标与各方比分（胜方加 ▶ 标记）；每对局独立、互不干扰。

## 四种模式

| 模式 | id | 方数×人数 | 计分 | 赛制（默认） | 复活 |
|------|----|----------|------|------|------|
| 单挑 | `duel_1v1` | 2×1 | 赢回合 | 5 局 3 胜 | 固定出生点 |
| 2v2 | `duel_2v2` | 2×2 | 赢回合 | 5 局 3 胜 | 队友附近 |
| 团队死斗 | `team_deathmatch` | 2×N | 击杀 | 先到 N 杀 | 队友附近 |
| 个人乱斗 | `free_for_all` | N×1 | 击杀 | 先到 N 杀 | 随机 |

四模式共用 `LoadoutRuleset.ARCADE`，对战场兵种专属道具"无缝禁用"，配装数据与战场模式共享。

## 构建与测试（离线环境）

```powershell
$env:JAVA_HOME='D:\MiencraftDEV\ACT0DEV\Custom_loadouts\gradle_dl\jdk-17.0.17+10'
$env:GRADLE_USER_HOME='D:\gradle-home'
cd D:\MiencraftDEV\ACT0DEV\ACT0-Arcade
& 'D:\MiencraftDEV\ACT0DEV\Custom_loadouts\gradle_dl\gradle-8.5\bin\gradle.bat' compileJava test --no-daemon --console=plain
```

- 18 个单测覆盖 MC-free 核心（loadout / round / mode）。
- MC 依赖仅集中在 `loadout/mc`、`match`、`storage`、`command`、`network`、`client`。
- `gradle build` 可产出可加载 jar。

## 命令

```
# 竞技场（需 OP）：在目标位置站好后录入出生点
/arcade arena create <id>        # 以当前位置为返回点新建竞技场
/arcade arena addspawn <id>      # 添加一个阵营固定出生点（决斗/团队按方取用）
/arcade arena addrandom <id>     # 添加一个随机复活点（个人乱斗用）
/arcade arena list|info <id>|remove <id>

# 配装（任意玩家，持久化、街机/战地共享）
/arcade loadout edit             # 打开像素风配装界面（含物品图标）
/arcade loadout class <职业>     # ASSAULT/SUPPORT/ENGINEER/RECON
/arcade loadout set <槽位> <装备key>
/arcade loadout clear <槽位> | show

# 游戏浏览器 / 目录重载
/arcade browse                   # 打开像素风游戏浏览器（管理员=一键开局；普通玩家=加入队列）
/arcade reload                   # 重载 config 装备目录并同步到在线玩家（需 OP）

# 匹配队列（任意玩家，凑够人数自动开局）
/arcade queue join <模式> <arena> # 按 模式@竞技场 排队（1v1=2人/2v2=4/TDM=4/FFA=4）
/arcade queue leave              # 离队
/arcade queue status             # 查看各队列人数

# 对局（需 OP）
/arcade start duel_1v1 <arena> <玩家>          # 需 2 人
/arcade start duel_2v2 <arena> <玩家>          # 需 4 人
/arcade start team_deathmatch <arena> <玩家>   # 偶数人分两队，先到 30 杀
/arcade start free_for_all <arena> <玩家>      # N 人混战，先到 20 杀
/arcade stop <matchId> | list
```

## 装备目录（JSON 数据驱动）

装备目录由服务端 `config/act0_arcade/loadout/*.json` **数据驱动**：

- 首次启动时，若目录为空会自动写出 `default.json`（含原版占位 + 一批 TaCZ 真枪示例）作为可编辑模板。
- 服务端启动 / `/arcade reload` 时加载全部 JSON，并通过 `SyncCatalogPacket` 下发给客户端（GUI 以服务端为准）。
- 枪械以物品 SNBT 表达，应用层直接还原，**无需对 TaCZ 编译期依赖**；未装 TaCZ 时相关条目静默跳过。

### 条目格式

```jsonc
{
  "entries": [
    {
      "key": "primary.ak47",          // 唯一键，约定 <槽位前缀>.<名称>
      "name": "AK-47",                 // 显示名
      "slot": "PRIMARY_WEAPON",         // PRIMARY_WEAPON/SECONDARY_WEAPON/MELEE/GADGET_1/GADGET_2/THROWABLE
      "classes": ["ASSAULT"],            // 省去/为空 = 全职业可用
      "unlockLevel": 0,                  // 解锁等级
      "snbt": "{id:\"tacz:modern_kinetic_gun\",Count:1b,tag:{GunId:\"tacz:ak47\"}}"
    }
  ]
}
```

### 如何新增枪包 / 装备（维护指南）

1. 在 `config/act0_arcade/loadout/` 新建一个 JSON（例如 `pack_modern.json`），按上表填写 `entries`。
2. **枪械 SNBT**：TaCZ 枪统一用物品 `tacz:modern_kinetic_gun`，具体型号由 NBT 中 `GunId` 决定；
   配件 / 弹药 / 皮肤等也都写在同一个物品 NBT 里（可在游戏内组装好枪后，对着枪用 `/data get entity @s SelectedItem` 取出 SNBT 直接粘贴）。
3. `/arcade reload`（或重启服务端）生效；新目录会自动同步到在线玩家。
4. 整个过程**无需改代码 / 重新编译**；多个 JSON 文件会合并加载，便于按枪包拆分管理。

> 内置默认条目见 `DefaultLoadoutCatalog`：原版占位保证未装 TaCZ 也能端到端联调，
> 同时附带 AK-47 / HK416D / MP5 / AWP / Glock 17 / 沙鹰 等 TaCZ 真枪示例供复制。

## 待办

- 浏览器内细化玩家选择器（当前管理员一键开局以自身 `@s` 为目标，普通玩家走队列）。
- 九宫格贴图美化（当前为程序化像素面板，无需贴图即可运行）。
