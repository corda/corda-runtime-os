@Library('corda-shared-build-pipeline-steps@CORE-6843-allow-cordaPipeline-groovy-to-publish-to-S3-bucket-corda-download-prod') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: false,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: false,
    publishOSGiImage: false, // dont merge back to release branch
    publishPreTestImage: false,
    publishHelmChart: false, // dont merge back to release branch
    javadocJar: false,
//    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false,
    combinedWorkere2eTests: false,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: false,
    )
