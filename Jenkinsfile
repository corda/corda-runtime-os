@Library('corda-shared-build-pipeline-steps@ronanb/CORE-4383/gradle-Experiment4') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: true,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false,
    cleanBuild: true,
    gradleAdditionalArgs: ' --build-cache'
    )
