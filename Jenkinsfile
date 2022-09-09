@Library('corda-shared-build-pipeline-steps@DP2') _

cordaPipeline(
    //nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: true,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: true,
    combinedWorkere2eTests: true,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: true,
    )
