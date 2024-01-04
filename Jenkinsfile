@Library('corda-shared-build-pipeline-steps@5.2') _

cordaPipelineKubernetesAgent(
    dailyBuildCron: 'H H/6 * * *',
    runIntegrationTests: false,
    createPostgresDb: false,
    publishOSGiImage: false,
    publishPreTestImage: false,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false,
    combinedWorkere2eTests: false,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: false,
    // allow publishing an installer to a download site
    publishToDownloadSiteTask: ':tools:plugins:publish',
    // TODO - remove this when J17 is the default in the pipeline
    javaVersion: '17'
    )
