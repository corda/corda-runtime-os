@Library('corda-shared-build-pipeline-steps@ES-126/Jira-automation') _

cordaPipeline(
    dailyBuildCron: 'H H/6 * * *',
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: false,
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
    )
