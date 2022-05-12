@Library('corda-shared-build-pipeline-steps@NOTICK/ronanb/test-adding-osgi-test-data') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: true,
    publishOSGiImage: false,
    publishPreTestImage: false,
    publishHelmChart: false,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false
    )
