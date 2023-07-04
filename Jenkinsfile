@Library('corda-shared-build-pipeline-steps@5.1') _

cordaPipeline(
    dailyBuildCron: 'H H/6 * * *',
    runIntegrationTests: false,
    createPostgresDb: true,
    publishOSGiImage: false,
    publishPreTestImage: true,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false,
    combinedWorkere2eTests: true,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: false,
    // allow publishing an installer to a download site
    publishToDownloadSiteTask: ':tools:plugins:publish',
    )
