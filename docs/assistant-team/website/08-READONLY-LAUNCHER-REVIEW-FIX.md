# 原08：启动接线增量复核补修（正式续作，整批处理）

日期：2026-10-01。负责人已批准本轮隔离只读启动接线开发，随后要求继续；这是总控技术复核退回的同范围补修，不是新阶段。原08在原工作树、原分支/HEAD以GPT-6.1 Sol / High执行。先完成当前纯元数据归档，停止该小批后执行本单；不得同时启动其它任务。本单必要门禁无异常就直接实施，不再接令检查/等待负责人二次批准。

## 两项差异及原因（不预设领域SQL修法）

1. **独立构建目录兼容。** 总控已确认最新获验构建为 `D:/知识图谱/frontend/dist/06-ui-polish-UIPolish-20261001T002724731/site`；当前 `AssistantReadAcceptanceApplication.staticSite` 却要求目录与 `D:/知识图谱/frontend/dist` 完全相等。dist根已有其它index/assets及历史批次，不能为了接线覆盖或删除。入口须能使用明确获审且hash固定的独立子目录，而非强迫读取根目录旧页面。限定主项目dist边界，继续严格拒绝目录外、重定向/穿越、未审manifest及/api回退；不要放宽到任意项目/磁盘目录，也不实际读取或修改真实build，本轮仍使用target合成build。
2. **安全错误身份留证。** 新DataSource.call及getConnection吞掉SQLException后只返回 `new SQLException(通用消息)`，连安全的SQLState/vendor errorCode也丢失。真实复验可能无法区分MySQL3024与其它JDBC失败。保持脱敏消息、无敏感cause/密码/SQL全文，但保留真实SQLException的SQLState和数值errorCode供技术证据使用；不能伪造错误身份或把初始化/权限失败映射为通过。这不改变业务SQL、权限、超时或冻结HTTP契约。

## 允许文件与前像

仅修改原08本轮新增的两份源码与两份新测试（主项目仍只读）：
- `AssistantReadAcceptanceApplication.java`，原raw `4d4c7086a02176714ec8424494e87a06e06edbb670c121649fee9e865898e463`
- `AssistantReadAcceptanceDataSource.java`，原raw `42164a24d37f66b1dd2de0b966213accf3f75f07e0c15c2e6f9308c6204ad05c`
- `AssistantReadAcceptanceTest.java`，原raw `dd971f173752ff91193591b6f9da1424981ac4a9fb3bd1721d2cfb9f2dda75ec`
- `AssistantReadAcceptanceDataSourceTest.java`，原raw `ec3ba6a2c77280bbd8f3185095b6c304247b25672031ac552113ee49b865395d`

源码/测试目录仍按原任务；其它本轮Config/Evidence和原30项依赖只读。在原已批准 `docs/assistant-team/website/08-readonly-acceptance-launcher/evidence/<新唯一runId>-review-fix/` 新建前像归档、报告、overlay/索引、补充启动说明、Maven日志和独立XML；编译/合成输出仅自己的backend-java/target。原报告、overlay、delivery-index、元数据增量、r01/r02及历史expected hash不改。

修改前将上述四文件按原字节保全到新归档并核对原hash。原清单是历史前像记录，后续由新增overlay明确版本迁移/旧文件归档映射，不改历史hash冒充绿灯。未知变动/前像不符停止。

## 验证与交付

复用总控既有152项两轮XML、30项前像、71份历史保护和元数据结论，不全文重审。补充合成回归须确认：受控dist子目录/manifest的启动接线可用，dist外/重定向/资源变更仍拒绝；新独立目录不需要覆盖根dist；原路径及/api门禁不弱化。对模拟SQLException(秘密message/cause,真实HY000/3024)覆盖连接失败与statement执行失败的safe code/state留存和秘密隐藏；原预算/关闭/旧断言不变。

只允许一次额外集中离线复验（原任务第2次、最后一次修订复验），精确选择原5类，用独立suffix，不修改旧77项断言、不连接真实数据库/宿主/模型、不读真实凭据或真实构建目录。如这轮仍阻断，保留结果并停止，不无限重跑或追加探针。定义/展开/执行/历史轮次分别统计。

完成交付新报告、四文件前后hash、保护/归档、新说明和本次XML；元数据乱码归档结果一并引用，但不得将其当源码补修/测试次数。总控只规划审核，代码与测试由原08执行，汇集/真实环境执行者无。完成即停止待总控审核，不启动06、I3/W2或部署。

