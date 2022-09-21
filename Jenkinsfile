@Library('corda-shared-build-pipeline-steps@DP2') _

cordaPipeline(
    runIntegrationTests: false,
    publishOSGiImage: false, // do not merge back to release/os/5.0
    dailyBuildCron: 'H 03 * * *',
    publishRepoPrefix: 'engineering-tools-maven',
    nexusAppId: 'net.corda-cli-host-0.0.1',
    publishToMavenS3Repository: true
)
