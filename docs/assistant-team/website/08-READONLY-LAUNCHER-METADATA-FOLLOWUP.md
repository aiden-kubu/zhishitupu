# 原08：启动接线交付元数据归档修订（正式续作）

日期：2026-10-01。执行者仍为原08「只读宿主与 API」，原工作树/分支/HEAD及GPT-6.1 Sol / High不变。此为原已批准交付的总控复核补修，不是新阶段，不再做无阻塞接令审批。

总控已读本轮4份新增源码、2份测试及启动交接，独立核对30份交付/证据大小和raw hash、30项两端前像、71份历史保护、两轮10份XML；r01=150、r02=152，全部failure/error/skipped=0。复用这些结论，不编译、不重跑、不再全文读旧报告。

## 唯一问题与固定前像

UTF-8读取原 `docs/assistant-team/website/08-readonly-acceptance-launcher/overlay.json` 后，以下5个字符串含乱码（不只是终端显示）：worktree、mainRootReadOnly、baseline.path、model.actualModel、model.actualReasoningEffort。原overlay raw SHA-256为 `c84c2bff2a4ff75cc2b0db6bbe030435bbe40ab72f39454724066eb9fdde34e7`；原delivery-index raw为 `d87339bb186ddae136983c6992163cce4327a44520003496aaed4daf52827198`。源码、XML、文件hash和既有保护无不符。

## 只允许新增归档增量

不要改原overlay、delivery-index、报告、源码、测试、XML或日志，不重新生成历史expected hash。

在本轮已批准 `docs/assistant-team/website/08-readonly-acceptance-launcher/evidence/<唯一runId>-metadata/` 下，仅新增：
- `metadata-overlay.json`：绑定上述原overlay和原delivery-index真实hash，列出5项字段旧值与正确新值、原因及UTF-8编码；
- `metadata-report.md`：简述本次只做元数据归档修订，源代码/测试结果未变，仍152个去重场景、302次历史执行，本轮测试执行0；
- `metadata-index.json`：新增两份内容的raw hash/大小及原件保护结果，不自引用自算hash。

正确路径：`D:/知识图谱-worktrees/assistant-08-host-api`、`D:/知识图谱`、`D:/知识图谱/docs/assistant-team/website/08-READONLY-LAUNCHER-BASELINE.json`。模型实况字段分别写“实际模型未核实”“实际推理强度未核实”，不能因补乱码变成已核实。

使用明确UTF-8 JSON读写（可使用Unicode转义）；解析后逐项与真实路径比较，不能仅检查JSON语法。只对本次新增文件及原件保护做静态hash/字段核对；原源码与已审核测试结论直接复用。main项目仍只读。

若原件已变、目录占用或需要扩大写入范围，停止并报告。无异常则直接归档修订，完成在原聊天回报并停止，不启动06，不读真实凭据、不连接数据库、不运行测试或新诊断。

