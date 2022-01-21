@Library('corda-shared-build-pipeline-steps@ronanb/INFRA-1697/secure-wrapper-download') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: false
    )
