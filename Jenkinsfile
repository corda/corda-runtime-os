@Library('corda-shared-build-pipeline-steps@ES-522-Ensure-the-whole-location-of-a-Helm-chart-is-included-in-the-build-status-page') _

cordaPipeline(
    dailyBuildCron: 'H H/6 * * *',
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: false,
    createPostgresDb: false,
    publishOSGiImage: true,
    publishPreTestImage: false,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false,
    combinedWorkere2eTests: false,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: true,
    // allow publishing an installer to a download site
    publishToDownloadSiteTask: ':tools:plugins:publish',
    )
