@Library('corda-shared-build-pipeline-steps@NOTICK/test-different-jdk') _

cordaPipeline(
    dailyBuildCron: 'H H/3 * * *',
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: true,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: true,
    combinedWorkere2eTests: true,
    javadocJar: true,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: true,
    // allow publishing an installer to a download site
    publishToDownloadSiteTask: ':tools:plugins:publish',
    )
