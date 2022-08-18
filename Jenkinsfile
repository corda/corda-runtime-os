@Library('corda-shared-build-pipeline-steps@jansz/CORE-4935/buildkit-POC') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: true,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: true,
    combinedWorkere2eTests: true
    )
