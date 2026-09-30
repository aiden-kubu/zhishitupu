# I1 实际源码与测试清单

共62个Java文件：43个源码、19个测试类。新增51个；主工作树已有公共核心11个原样保留。哈希完整值、规范化规则、原领域来源与所有重复副本见 [I1-BASELINE.json](D:/知识图谱/docs/assistant-team/integration/I1/I1-BASELINE.json)。此处按相对代码目录展示名称，链接均指向主项目真实文件。

| 归属 | 类型 | 动作 | 主项目文件 |
|---|---|---|---|
| 01-ingestion | 源码 | 新增副本 | [ingestion/IngestionAgentToolProvider.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/IngestionAgentToolProvider.java) |
| 01-ingestion | 源码 | 新增副本 | [ingestion/IngestionProposalValidator.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/IngestionProposalValidator.java) |
| 01-ingestion | 源码 | 新增副本 | [ingestion/IngestionReadRepository.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/IngestionReadRepository.java) |
| 01-ingestion | 源码 | 新增副本 | [ingestion/IngestionRecoveryAdvisor.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/IngestionRecoveryAdvisor.java) |
| 01-ingestion | 源码 | 新增副本 | [ingestion/JdbcIngestionReadRepository.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/JdbcIngestionReadRepository.java) |
| 01-ingestion | 源码 | 新增副本 | [source/JdbcSourceLocator.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/source/JdbcSourceLocator.java) |
| 01-ingestion | 源码 | 新增副本 | [source/SourceLocator.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/source/SourceLocator.java) |
| 01-ingestion | 测试 | 新增副本 | [ingestion/IngestionAgentToolProviderTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/ingestion/IngestionAgentToolProviderTest.java) |
| 01-ingestion | 测试 | 新增副本 | [ingestion/JdbcIngestionReadRepositoryTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/ingestion/JdbcIngestionReadRepositoryTest.java) |
| 02-quality | 源码 | 新增副本 | [quality/JdbcQualityReadRepository.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java) |
| 02-quality | 源码 | 新增副本 | [quality/QualityAgentToolProvider.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/quality/QualityAgentToolProvider.java) |
| 02-quality | 源码 | 新增副本 | [quality/QualityArtifactReader.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/quality/QualityArtifactReader.java) |
| 02-quality | 源码 | 新增副本 | [quality/QualityEvidenceVerifier.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/quality/QualityEvidenceVerifier.java) |
| 02-quality | 源码 | 新增副本 | [quality/QualityReadRepository.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/quality/QualityReadRepository.java) |
| 02-quality | 测试 | 新增副本 | [quality/JdbcQualityReadRepositoryTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepositoryTest.java) |
| 02-quality | 测试 | 新增副本 | [quality/QualityAgentToolProviderTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/quality/QualityAgentToolProviderTest.java) |
| 02-quality | 测试 | 新增副本 | [quality/QualityEvidenceVerifierTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/quality/QualityEvidenceVerifierTest.java) |
| 03-research | 源码 | 新增副本 | [research/JdbcResearchReadRepository.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/research/JdbcResearchReadRepository.java) |
| 03-research | 源码 | 新增副本 | [research/ResearchAgentToolProvider.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/research/ResearchAgentToolProvider.java) |
| 03-research | 源码 | 新增副本 | [research/ResearchArtifacts.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/research/ResearchArtifacts.java) |
| 03-research | 源码 | 新增副本 | [research/ResearchDraftValidator.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/research/ResearchDraftValidator.java) |
| 03-research | 源码 | 新增副本 | [research/ResearchEvidence.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/research/ResearchEvidence.java) |
| 03-research | 源码 | 新增副本 | [research/ResearchReadRepository.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/research/ResearchReadRepository.java) |
| 03-research | 测试 | 新增副本 | [research/JdbcResearchReadRepositoryTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/research/JdbcResearchReadRepositoryTest.java) |
| 03-research | 测试 | 新增副本 | [research/ResearchAgentToolProviderTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/research/ResearchAgentToolProviderTest.java) |
| 03-research | 测试 | 新增副本 | [research/ResearchComparisonTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/research/ResearchComparisonTest.java) |
| 03-research | 测试 | 新增副本 | [research/ResearchDraftValidatorTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/research/ResearchDraftValidatorTest.java) |
| 04-learning | 源码 | 新增副本 | [learning/JdbcLearningReadRepository.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/learning/JdbcLearningReadRepository.java) |
| 04-learning | 源码 | 新增副本 | [learning/LearningAgentToolProvider.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/learning/LearningAgentToolProvider.java) |
| 04-learning | 源码 | 新增副本 | [learning/LearningContextReader.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/learning/LearningContextReader.java) |
| 04-learning | 源码 | 新增副本 | [learning/LearningDraftValidator.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/learning/LearningDraftValidator.java) |
| 04-learning | 源码 | 新增副本 | [learning/LearningEvidence.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/learning/LearningEvidence.java) |
| 04-learning | 源码 | 新增副本 | [learning/LearningJson.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/learning/LearningJson.java) |
| 04-learning | 源码 | 新增副本 | [learning/LearningMaterials.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/learning/LearningMaterials.java) |
| 04-learning | 源码 | 新增副本 | [learning/LearningReadRepository.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/learning/LearningReadRepository.java) |
| 04-learning | 测试 | 新增副本 | [learning/JdbcLearningReadRepositoryTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/learning/JdbcLearningReadRepositoryTest.java) |
| 04-learning | 测试 | 新增副本 | [learning/LearningAgentToolProviderTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/learning/LearningAgentToolProviderTest.java) |
| 04-learning | 测试 | 新增副本 | [learning/LearningArtifactHandoffTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/learning/LearningArtifactHandoffTest.java) |
| 04-learning | 测试 | 新增副本 | [learning/LearningDraftValidatorTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/learning/LearningDraftValidatorTest.java) |
| 05-assessment | 源码 | 新增副本 | [assessment/AssessmentAgentToolProvider.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/assessment/AssessmentAgentToolProvider.java) |
| 05-assessment | 源码 | 新增副本 | [assessment/AssessmentContextReader.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/assessment/AssessmentContextReader.java) |
| 05-assessment | 源码 | 新增副本 | [assessment/AssessmentDraftValidator.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/assessment/AssessmentDraftValidator.java) |
| 05-assessment | 源码 | 新增副本 | [assessment/AssessmentEvidence.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/assessment/AssessmentEvidence.java) |
| 05-assessment | 源码 | 新增副本 | [assessment/AssessmentJson.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/assessment/AssessmentJson.java) |
| 05-assessment | 源码 | 新增副本 | [assessment/AssessmentMaterials.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/assessment/AssessmentMaterials.java) |
| 05-assessment | 源码 | 新增副本 | [assessment/AssessmentReadRepository.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/assessment/AssessmentReadRepository.java) |
| 05-assessment | 源码 | 新增副本 | [assessment/JdbcAssessmentReadRepository.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/assessment/JdbcAssessmentReadRepository.java) |
| 05-assessment | 测试 | 新增副本 | [assessment/AssessmentAgentToolProviderTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/assessment/AssessmentAgentToolProviderTest.java) |
| 05-assessment | 测试 | 新增副本 | [assessment/AssessmentArtifactHandoffTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/assessment/AssessmentArtifactHandoffTest.java) |
| 05-assessment | 测试 | 新增副本 | [assessment/AssessmentDraftValidatorTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/assessment/AssessmentDraftValidatorTest.java) |
| 05-assessment | 测试 | 新增副本 | [assessment/JdbcAssessmentReadRepositoryTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/assessment/JdbcAssessmentReadRepositoryTest.java) |
| root | 源码 | 保留原文件 | [contract/AssistantContracts.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/contract/AssistantContracts.java) |
| root | 源码 | 保留原文件 | [runtime/AgentRunner.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/runtime/AgentRunner.java) |
| root | 源码 | 保留原文件 | [runtime/AssistantRuntime.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/runtime/AssistantRuntime.java) |
| root | 源码 | 保留原文件 | [runtime/DecisionParser.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/runtime/DecisionParser.java) |
| root | 源码 | 保留原文件 | [runtime/DecisionProvider.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/runtime/DecisionProvider.java) |
| root | 源码 | 保留原文件 | [runtime/ProposalValidator.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/runtime/ProposalValidator.java) |
| root | 源码 | 保留原文件 | [runtime/RunBudget.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/runtime/RunBudget.java) |
| root | 源码 | 保留原文件 | [tool/AssistantTool.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/tool/AssistantTool.java) |
| root | 源码 | 保留原文件 | [tool/ToolRegistry.java](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/tool/ToolRegistry.java) |
| root | 测试 | 保留原文件 | [runtime/AssistantRuntimeTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/runtime/AssistantRuntimeTest.java) |
| root | 测试 | 保留原文件 | [runtime/DecisionParserTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/runtime/DecisionParserTest.java) |

其他新增交付物均在 docs/assistant-team/integration/I1 下：基线/保护/验证JSON、Verify-I1.ps1、阶段报告、本清单、验收清单与样本JSON/A/B原文、页面方案、写入口隔离文档、01～05报告归档、05清理前副本。没有pom、迁移、配置、前端或既有业务文件的本轮改动。

唯一在原领域工作树内的编辑是获准清理 05 报告中的对话文字；其源码、测试及冻结依赖不变。原报告与归档的清理核验记录在 I1-VERIFICATION.json。
