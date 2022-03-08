@Library('corda-shared-build-pipeline-steps@5.0') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: false,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: false,
    publishOSGiImage: true,
    publishPreTestImage: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false,
    cleanBuild: true
    )
