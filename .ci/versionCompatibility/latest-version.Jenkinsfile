//@Library('corda-shared-build-pipeline-steps@5.1') _
@Library('corda-shared-build-pipeline-steps@rfowler/CORE-12786-evm-worker') _

// This build forces using the "very latest" version of the dependencies, regardless of which revision was chosen
//  This is useful as it gives early indication of a downstream change that may introduce a breaking change
//  It should not, however, be a PR gate.
cordaCompatibilityCheckPipeline(
    useLatestBeta: true,
)
