@Library('corda-shared-build-pipeline-steps@mbrkic-r3/CORE-16243/persistence-worker') _
// TODO revert to 5.1 after branch above is merged to it
// @Library('corda-shared-build-pipeline-steps@5.1') _

// This build forces using the "very latest" version of the dependencies, regardless of which revision was chosen
//  This is useful as it gives early indication of a downstream change that may introduce a breaking change
//  It should not, however, be a PR gate.
cordaCompatibilityCheckPipeline(
    useLatestBeta: true,
)
