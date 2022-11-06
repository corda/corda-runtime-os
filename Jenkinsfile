@Library('corda-shared-build-pipeline-steps@CORE-7375/add-helm-docker-hub') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: false,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: false,
    publishOSGiImage: false,
    publishPreTestImage: false,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: false,
    combinedWorkere2eTests: false,
    javadocJar: false,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: true,
    // allow publishing an installer to a download site
    publishToDownloadSiteTask: ':tools:plugins:publish',
    )
