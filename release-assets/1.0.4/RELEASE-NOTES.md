# Pattern Checker / 样板检测工具 1.0.4

## 下载版本

- Minecraft 1.20.1：patternchecker-mc1.20.1-1.0.4.jar，Forge 47+，Java 17，AE2 15.4.9+。
- Minecraft 1.21.1：patternchecker-mc1.21.1-1.0.4.jar，NeoForge 21.1.200+，Java 21，AE2 19.2.17+。

请选择匹配游戏版本和加载器的文件，替换旧版工具模组，不要同时安装两个包。

## 更新内容

- 修复工具面板收起后，物品栏物品能拿起但无法放下的问题。
- 修复面板收起时重新打开终端，再展开后按钮变细条或不可见的问题。
- 缩小图标支持按住拖动并记住位置；单击松开后展开，拖动不会触发展开。
- 修复展开和收起切换时的鼠标捕获状态清理。
- 修复 1.20.1 的“编辑”按钮未随面板重新布局的问题。

## Changes

- Fixed inventory items being picked up but not placed while the checker is collapsed.
- Fixed narrow or invisible buttons when reopening a terminal with the checker collapsed, then expanding it.
- Added dragging for the collapsed icon with position persistence. Click and release to expand; dragging keeps it collapsed.
- Fixed mouse capture cleanup across panel state changes.
- Fixed the 1.20.1 Edit button not following panel layout changes.

## Validation

Both target builds and focused input/layout regression checks passed. Full in-game acceptance testing of these final packages has not been completed.
