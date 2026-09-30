param([ValidateSet('Base','Regression','Variant','Budget','Scale')][string]$Suite='Base')
$ErrorActionPreference='Stop'
if ($Suite -eq 'Base' -and $env:KG_I2_AUTHORIZED -ne '8c6cfcb3-ba49-11f1-bf64-005056c00001') { throw 'I2 authorization missing' }
Set-Location -LiteralPath 'D:\知识图谱\backend-java'
$classes = switch ($Suite) {
  'Base' { 'I2JdbcBaselineTest,I2ReadOnlyPrivilegeTest' }
  'Variant' { 'I2FaultVariantTest' }
  'Budget' { 'I2QueryBudgetTest' }
  'Scale' { 'I2ScaleQueryTest' }
  'Regression' {
    'AssistantRuntimeTest,DecisionParserTest,IngestionAgentToolProviderTest,JdbcIngestionReadRepositoryTest,QualityAgentToolProviderTest,QualityEvidenceVerifierTest,JdbcQualityReadRepositoryTest,ResearchAgentToolProviderTest,JdbcResearchReadRepositoryTest,ResearchComparisonTest,ResearchDraftValidatorTest,LearningAgentToolProviderTest,JdbcLearningReadRepositoryTest,LearningArtifactHandoffTest,LearningDraftValidatorTest,AssessmentAgentToolProviderTest,JdbcAssessmentReadRepositoryTest,AssessmentArtifactHandoffTest,AssessmentDraftValidatorTest'
  }
}
& .\mvnw.cmd -o "-Dtest=$classes" test
exit $LASTEXITCODE
