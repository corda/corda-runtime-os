@Library('corda-shared-build-pipeline-steps@ronanb/INFRA-1685/gralde-build-cache') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: true
    )
