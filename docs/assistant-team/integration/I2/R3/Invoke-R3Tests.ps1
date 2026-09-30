param([ValidateSet('Original','Ranking','Plan','Semantics','Base','Budget','Performance','LegacyWindow','IsolatedCount','CountPlan','WholeRegression','Matrix')][string]$Suite,[Parameter(Mandatory)][string]$ReportSuffix)
$ErrorActionPreference='Stop'
$root='D:\知识图谱'
$classes=switch($Suite){
    'Original' { $base=Get-Content -LiteralPath (Join-Path $root 'docs/assistant-team/integration/I1/I1-BASELINE.json') -Raw|ConvertFrom-Json; (@($base.files|Where-Object kind -eq 'test'|ForEach-Object{[IO.Path]::GetFileNameWithoutExtension($_.file)}) -join ',') }
    'Ranking' {'JdbcQualityInsightRankingRegressionTest'}
    'IsolatedCount' {'JdbcQualityInsightIsolatedCountRegressionTest'}
    'WholeRegression' {'JdbcQualityInsightPerformanceRegressionTest'}
    'Matrix' {'I2QualityInsightWholeTest'}
    'CountPlan' {'I2QualityIsolatedCountTest#realCountPlansAndIndependentSemantics'}
    'Plan' {'I2QualityInsightRankingTest#realRankingPlanAndScaleTop'}
    'Semantics' {'I2QualityInsightRankingTest#realSemanticMatrixAndProjectOracle'}
    'Base' {'I2JdbcBaselineTest,I2ReadOnlyPrivilegeTest'}
    'Budget' {'I2QueryBudgetTest'}
    'Performance' {'I2QualityInsightRankingTest#fullScaleSingleAndFourConcurrentSamples'}
    'LegacyWindow' {'I2ScaleQueryTest#scaledFixtureQueryWindowsAndScope'}
}
if($Suite -notin @('Original','Ranking','IsolatedCount','WholeRegression') -and $env:KG_I2_AUTHORIZED -ne '8c6cfcb3-ba49-11f1-bf64-005056c00001'){throw 'Explicit original I2 instance authorization missing'}
if($env:KG_I2_ADMIN_PASSWORD -or $env:KG_I2_LOADER_PASSWORD -or $env:MYSQL_PWD){throw 'Write credential must not enter test process'}
Set-Location -LiteralPath (Join-Path $root 'backend-java')
& .\mvnw.cmd -o "-Dtest=$classes" "-Dsurefire.reportNameSuffix=$ReportSuffix" test
exit $LASTEXITCODE
