# Pattern Checker 1.0.5 / 样板检测工具 1.0.5

Choose the JAR matching your game and loader. Remove older Pattern Checker JARs from `mods` before installing this update.

| Minecraft | Loader | File | Required AE2 |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47+ | `patternchecker-mc1.20.1-1.0.5.jar` | 15.4.9+ |
| 1.21.1 | NeoForge 21.1.200+ | `patternchecker-mc1.21.1-1.0.5.jar` | 19.2.17+ |

## Changes / 更新内容

- Fixed false invalid-pattern reports for fluid substitution and shapeless crafting. / 修复流体替换和无序合成样板误报。
- Restored the selected pattern when reopening the checker, kept the list near a processed entry, and prevented a stale collapsed icon from blocking other terminal controls. / 重开工具时恢复样板定位，并避免收起图标残留遮挡终端控件。
- Distinguished providers at the same coordinates in different dimensions when restoring selection. / 跨维度同坐标供应器现在使用不同的选中标识。
- Recognized Mekanism Magic machines. Context-dependent recipes are reported as unverified instead of incorrectly reported missing; machines that reject patterns are identified as invalid targets. / 识别 Mekanism Magic 机器；依赖灵体、仪式或催化剂状态的配方显示“未确认”，明确不接受样板的设备会报错。
- Minecraft 1.21.1: added Mystical Automation Infusion and Awakening Altarnator checks, including the central ingredient, counted essences and top-side input; expanded support for Ars Nouveau, Immersive Engineering and other registered processing recipes. / 1.21.1 增加 Mystical Automation 注魔及觉醒祭坛的材料与输入面检查，并扩展多模组处理配方适配。
- Improved 1.21.1 virtual-completion checks so decoding, input availability and machine targets are still validated; fixed reusable-output allocation, loose-container counts and provider-world lookup. / 改进虚拟完成样板的必要检查，修复返还材料分配、独立容器计数和跨维度供应器世界定位。

The user confirmed the current integrated code works in-game. Local builds and automated tests passed: 22 tests on Forge and 68 tests on NeoForge. The release JAR SHA-256 values are in `SHA256SUMS.txt`.
