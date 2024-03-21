@Library('corda-shared-build-pipeline-steps@connelm/ES-2123/move-generate-PR-comments-utils') _

cordaPipelineKubernetesAgent(
    dailyBuildCron: 'H H/6 * * *',
    runIntegrationTests: false,
    runUnitTests: false,
    createPostgresDb: false,
    publishOSGiImage: false,
    publishPreTestImage: false,
    publishHelmChart: false,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false,
    combinedWorkere2eTests: false,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: false,
    // allow publishing an installer to a download site
    publishToDownloadSiteTask: ':tools:plugins:publish',
    // TODO - remove this when J17 is the default in the pipeline
    javaVersion: '17',
)
