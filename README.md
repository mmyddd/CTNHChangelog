# CTNHChangelog

A Minecraft mod that displays changelog information and checks for updates.

## Configuration / 配置说明

### English

The configuration file is located at `config/ctnhchangelog-client.toml`.

| Field | Description |
|-------|-------------|
| `changelogUrl` | Default remote JSON changelog URL. Used by non-English/non-Russian languages and as the last fallback. |
| `changelogUrlEn` | English remote JSON changelog URL. English clients use this first. Other languages fall back to this when their matching URL is empty. |
| `changelogUrlRu` | Russian remote JSON changelog URL. Russian clients use this first; if empty, they fall back to `changelogUrlEn`. |
| `enableChangelogTab` | If `true`, displays the changelog tab in the "Create New World" menu. |
| `ModpackVersion` | Current modpack version. Used to compare with the remote version for update checks. |
| `enableVersionCheck` | If `true`, compares `ModpackVersion` with the highest parsed version in the remote file. |
| `buttonLocation` | Button display location. Options: `BOTH` (show on both Title Screen and Select World Screen), `TITLE_SCREEN` (only Title Screen), `SELECT_WORLD` (only Select World Screen). |
| `cacheTtlMinutes` | Remote changelog cache lifetime in minutes. While the cache is fresh, the mod uses the local cache without contacting the remote server. Set to `0` to check the remote file every time. |

Language selection follows the in-game language code. `en_*` uses `changelogUrlEn`, `ru_*` uses `changelogUrlRu`, and other languages use `changelogUrl`. Empty matching URLs fall back to `changelogUrlEn`, then `changelogUrl`.

### 中文

配置文件位于 `config/ctnhchangelog-client.toml`。

| 字段 | 说明 |
|------|------|
| `changelogUrl` | 默认远程 JSON 更新日志文件 URL。非英文/俄文语言使用此项，也作为最后兜底。 |
| `changelogUrlEn` | 英文远程 JSON 更新日志文件 URL。英文客户端优先使用此项，其他语言对应 URL 为空时回退到此项。 |
| `changelogUrlRu` | 俄语远程 JSON 更新日志文件 URL。俄语客户端优先使用此项；为空时回退到 `changelogUrlEn`。 |
| `enableChangelogTab` | 设为 `true` 时，在"创建新的世界"菜单中显示更新日志标签页。 |
| `ModpackVersion` | 当前整合包版本号。用于与远程最新版本对比，检测更新。 |
| `enableVersionCheck` | 设为 `true` 时，会自动与远程文件中解析出的最高版本比较。 |
| `buttonLocation` | 按钮显示位置。可选值：`BOTH`（标题界面和选择世界界面都显示）、`TITLE_SCREEN`（仅标题界面）、`SELECT_WORLD`（仅选择世界界面）。 |
| `cacheTtlMinutes` | 远程更新日志缓存有效期（分钟）。缓存未过期时直接使用本地缓存，不访问远端；设为 `0` 时每次都检查远端文件。 |

语言选择跟随游戏内语言代码：`en_*` 使用 `changelogUrlEn`，`ru_*` 使用 `changelogUrlRu`，其他语言使用 `changelogUrl`。对应语言 URL 为空时，会回退到 `changelogUrlEn`，再回退到 `changelogUrl`。

---

## Changelog JSON Format / 更新日志 JSON 格式

### Canonical v2 format / v2 标准格式

The in-game editor exports **CTNHChangelog v2**. New files should use this shape; `format`, `formatVersion`, `meta`, `types`, and `accent` are the canonical field names.

游戏内编辑器导出的是 **CTNHChangelog v2**。新文件应使用以下结构；`format`、`formatVersion`、`meta`、`types` 和 `accent` 是标准字段名。

```json
{
  "format": "ctnhchangelog",
  "formatVersion": 2,
  "meta": {
    "footer": "Thanks for playing!",
    "tagColors": {
      "important": "#FFFF5555",
      "transparent": "#8044AAFF"
    }
  },
  "entries": [
    {
      "version": "2.0.0",
      "date": "2026-07-24",
      "title": "Release title",
      "types": ["major"],
      "tags": ["important"],
      "accent": "#44AAFF",
      "changes": [
        "A top-level change",
        { "title": "Details", "children": ["Nested change"] }
      ]
    }
  ]
}
```

| v2 field | Description / 说明 |
|---|---|
| `format` | Must be `ctnhchangelog`. / 固定为 `ctnhchangelog`。 |
| `formatVersion` | Must be `2`. / 固定为 `2`。 |
| `meta.footer` | Optional footer text. / 可选的底部文本。 |
| `meta.tagColors` | Optional tag-color map. / 可选的标签颜色映射。 |
| `entries` | Ordered changelog entries. / 有序的更新日志条目。 |
| `entries[].types` | Array of types such as `major`, `minor`, `patch`, `hotfix`, or `danger`. An explicit empty array is preserved; a missing field defaults to `patch` for legacy compatibility. / 类型数组；显式空数组会保留，缺失字段才会为兼容旧数据默认成 `patch`。 |
| `entries[].accent` | Entry accent color. / 条目强调色。 |
| `entries[].changes` | Strings and/or recursive `{ "title", "children" }` sections. / 字符串和/或递归的 `{ "title", "children" }` 分组。 |

Colors accept `#RRGGBB` for opaque values and `#AARRGGBB` when alpha must be retained. `0xRRGGBB` and `0xAARRGGBB` are also accepted on input.

颜色支持不透明的 `#RRGGBB`，以及需要保留透明度时的 `#AARRGGBB`；输入也兼容 `0xRRGGBB` 和 `0xAARRGGBB`。

### Legacy compatibility / 旧格式兼容

Older unversioned documents that place `footer` and `tagColors` at the root and use `type`/`color` fields are accepted **as input only**. Re-exporting writes canonical v2 JSON. The bundled [legacy example](src/main/resources/changelog.json) remains readable for compatibility testing.


---

## In-Game Editor / 游戏内编辑器

### English

The mod includes a built-in changelog editor accessible from the changelog screen.

**Opening the Editor:**
- Click the "Edit" button in the top-right corner of the changelog overview screen

**Editor Features:**

| Tab | Description |
|-----|-------------|
| **Entries** | Add, delete, reorder changelog entries; edit version, date, title, type, tags, color, and changes |
| **Tag Colors** | Define custom tag colors for use in entries |
| **Footer** | Edit the gradient footer text displayed at the bottom |

**Editing Changes:**
- Double-click an existing change entry to edit it
- Press `Enter` to confirm the edit
- Press `Escape` to cancel and revert to the original text

**Import/Export:**
- Use the "Import" button to load a JSON file from the game directory
- Use the "Export" button to save the current changelog as JSON to the game directory

### 中文

模组内置了更新日志编辑器，可从更新日志界面打开。

**打开编辑器：**
- 点击更新日志概览界面右上角的"编辑"按钮

**编辑器功能：**

| 标签页 | 说明 |
|--------|------|
| **条目** | 添加、删除、排序更新日志条目；编辑版本号、日期、标题、类型、标签、颜色和变更内容 |
| **标签颜色** | 定义自定义标签颜色，供条目使用 |
| **脚注** | 编辑显示在底部的渐变色脚注文本 |

**编辑变更内容：**
- 双击已有变更条目即可进入编辑模式
- 按 `Enter` 确认修改
- 按 `Escape` 取消修改并恢复原文

**导入/导出：**
- 使用"导入"按钮从游戏目录加载 JSON 文件
- 使用"导出"按钮将当前更新日志保存为 JSON 文件到游戏目录

