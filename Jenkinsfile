@Library('corda-shared-build-pipeline-steps@pavel/es-1843/remove-dependency-of-pf4j-in-corda-cli') _

cordaPipelineKubernetesAgent(
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
    publishToDownloadSiteTask: ':tools:corda-cli:commands:publish',
    // TODO - remove this when J17 is the default in the pipeline
    javaVersion: '17',
)
