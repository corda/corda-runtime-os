# Corda5 plugins

These plugins are versioned alongside the corda version due to them requiring code from either the runtime repo, or
corda-api. 

## What's here
* db-config: Generates the initial db schema(s) for the cluster to later be applied to the cluster
* initial-config: Generates the initial values to be inserted into the config schema after it's been set up to be 
  applied to the cluster
* secret-config: Generates encrypted secrets for use in the configs we set up for the cluster
* Corda CLI plugins: Plugins for Corda CLI Plugin Host e.g. package, network.

## Plugin Smoke Tests
Smoke tests in individual Corda CLI plugin directories under `pluginSmokeTest` are run against the Combined Worker, intended to be triggered manually during development. There is also a nightly Jenkins job that runs these tests on the release branch. In the future, it may be included as a PR-gate.
