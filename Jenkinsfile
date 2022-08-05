
@Library('corda-shared-build-pipeline-steps@ronanb/test-list') _

cordaPipeline(
    runIntegrationTests: false,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: false,
    publishOSGiImage: true,
    publishPreTestImage: false,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false
    )
