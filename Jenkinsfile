@Library('corda-shared-build-pipeline-steps@feature/CORE-9806') _

cordaPipeline(
    dailyBuildCron: 'H H/6 * * *',
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: false,
    createPostgresDb: false,
    publishOSGiImage: false,
    publishPreTestImage: false,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false,
    combinedWorkere2eTests: false,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: true,
    // allow publishing an installer to a download site
    publishToDownloadSiteTask: ':tools:plugins:publish',
    runResiliencyTests: true,
    enableNotifications: false,
    slackChannel: '#corda-platform-flowworker',
)
