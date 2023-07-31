@Library('corda-shared-build-pipeline-steps@5.1') _

cordaPipeline(
    dailyBuildCron: 'H H/6 * * *',
    runIntegrationTests: true,
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: true,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: true,
    combinedWorkere2eTests: true,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: true,
    // allow publishing an installer to a download site
    publishToDownloadSiteTask: ':tools:plugins:publish',
    // TODO - remove this when J17 is the default in the pipeline
    workerBaseImageTag: '17.0.4.1-17.36.17',
    javaVersion: '17',
    snykDelta: false
    )
