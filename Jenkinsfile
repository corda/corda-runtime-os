@Library('corda-shared-build-pipeline-steps@corda5') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    runE2eTests: true,
    e2eTestName: 'flowworker-e2e-tests',
    dependentJobsNames: ['/Corda5/flow-worker-version-compatibility/driessamyn%2FINFRA-1539%2Fapi-versioning-change']
    )

