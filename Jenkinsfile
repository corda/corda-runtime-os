@Library('corda-shared-build-pipeline-steps@5.0.1') _

cordaPipeline(
    dailyBuildCron: 'H H/6 * * *',
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: true,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: true,
    combinedWorkere2eTests: true,
    // allow publishing artifacts to S3 bucket
    publishToMavenS3Repository: true,
    // allow publishing an installer to a download site
    publishToDownloadSiteTask: ':tools:plugins:publish',
    )


//dummy data fake key purpusly triggering scan
-----BEGIN EC PRIVATE KEY-----
MHcCAQEEIHRdEddsafnecOusz8jAMmaLW2VAlUm97ldXJNa5HOt5l96NoAoGCCqGSM49
AwEHoUQDQgAEdasd6T7NTQnXOUqt/eEEeUhwEHl4PARqAXdrHh4Ae+OWV0VQtDfJD0pl
GtVshXsBhOBddsat4/fqJyNoxXDHi6rthUX3ww==
-----END EC PRIVATE KEY-----
