@Library('corda-shared-build-pipeline-steps@ronanb/CORE-3192/update-docker-tagging') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: false
    )
