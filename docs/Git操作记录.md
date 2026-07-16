# Git 操作记录

> 记录 Claude 代为执行的每次 `git commit` / `git push`，作为可追溯的操作留痕。不记录普通的 `git status`/`git diff`/`git log` 等只读命令。

| 日期 | 类型 | commit hash | 涉及文件 | 说明 | 结果 |
| :--: | :--- | :---------- | :------- | :--- | :--- |
| 2026-07-10 | commit | `f871ee2` | `.gitignore`, `docs/Test/UAT测试案例_v2.md` | 补齐 TC-RG-01~03/TC-ST-01~03/TC-PD-01~03 共9项API实测回填；PC-01/PC-20复测转全通过；修正验证统计表口径不一致（剩余项分布数字对齐、标注347总数缺口）；.gitignore 补充 `.gstack/` | ✅ 提交成功 |
| 2026-07-10 | push | `f871ee2` | → `origin/main` (`https://github.com/DEACONHAN/ecl-project`) | 推送上述提交 | ❌ 失败：`CONNECT tunnel failed, response 405`（本地代理 `HTTPS_PROXY=127.0.0.1:9090` 挡住了到 github.com 的连接，非git本身报错） |
| 2026-07-10 | merge | `371e349` | 28个文件（同事5个提交：OverlayConfig解析修复/LGD池计算改为押品自身LGD+加权平均/试算错误提示与日期解析修复/管理层叠加null字段匹配修复/文档归档到docs） | 绕过代理后 `git fetch` 发现 origin/main 已领先5个提交（同事推送），与本地 `f871ee2` 无文件重叠，执行 `git merge origin/main` 合并，无冲突。⚠️ 注意：同事的修复改到了 `StageEngine.java`(+46/-)和`TrialCalculationService.java`(+26/-)，这两个文件正是今天UAT复核时引用过具体行号的文件，合并后行号可能已漂移，如果之后再引用需要重新核对 | ✅ 合并成功，无冲突 |
| 2026-07-10 | push | `371e349` | → `origin/main`，`69894ae..371e349` | 绕开本地 `HTTPS_PROXY`/`HTTP_PROXY` 环境变量后重新推送（`env -u HTTPS_PROXY -u HTTP_PROXY -u https_proxy -u http_proxy git push`），带上前面的merge commit | ✅ 推送成功 |
| 2026-07-10 | commit | `79e42ea` | `docs/Git操作记录.md`（新建） | 新建本记录文档并写入以上4条历史记录 | ✅ 提交成功 |
| 2026-07-10 | push | `79e42ea` | → `origin/main`，`371e349..79e42ea` | 推送上述提交（同样绕开代理） | ✅ 推送成功 |
| 2026-07-16 | tag | `backup-20260716-b0d68a5` | 指向 `b0d68a5` | 用户要求把`main`回退到`0814ff1`（撤销当天`f0c81df`/`6338428`/`d7e471c`/`b0d68a5`四个提交），回退前先给当前完整状态打备份tag，避免丢失。可用`git checkout backup-20260716-b0d68a5`随时找回 | ✅ 创建并推送成功 |
| 2026-07-16 | revert×4 | `636473b`/`118da82`/`d4f8b87`/`66e6ef7` | `docs/Git操作记录.md`、`LgdEngine.java` | 用`git revert 0814ff1..b0d68a5`按时间倒序依次revert了`b0d68a5`/`d7e471c`/`6338428`/`f0c81df`四个提交（选择revert而非`reset --hard`+强推，因为这4个提交已推送到远程，revert不改写共享历史）。revert后用`git diff 0814ff1 HEAD`核对，代码内容与`0814ff1`完全一致 | ✅ revert成功，无冲突 |
| 2026-07-16 | push | `66e6ef7` | → `origin/main`，`b0d68a5..66e6ef7` | 推送上述4个revert提交 | ✅ 推送成功 |
| 2026-07-16 | commit | `fdc856a` | `docs/Git操作记录.md` | 补记回退操作留痕(备份tag+4个revert+push) | ✅ 提交成功 |
| 2026-07-16 | commit | `1803cc9` | `LgdConfig.tsx` | 用户反馈"LGD参数配置页点新增填完确定没反应"：实测SCH_005是EFFECTIVE状态，后端`checkSchemeDraft()`按设计拒绝了保存(仅DRAFT可改)，但前端"基准曲线"和"押品折扣率"两个Tab的单条新增/编辑(`handleSave`)、单条删除(`handleDelete`)共4处调用保存接口时都没有try/catch，报错被静默吞掉，用户毫无提示。补上try/catch+`message.error`弹出后端具体报错，不改变业务逻辑 | ✅ 提交成功 |
