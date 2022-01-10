@Library('corda-shared-build-pipeline-steps@driessamyn/no-tick/ensure-everything-is-compiled') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: false
    )
