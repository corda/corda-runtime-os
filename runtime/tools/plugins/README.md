# Corda5 Deployment plugins

These plugins are versioned alongside the corda version due to them requiring code from either the runtime repo, or
corda-api. 

## What's here
* db-config: Generates the initial db schema(s) for the cluster to later be applied to the cluster
* initial-config: Generates the initial values to be inserted into the config schema after it's been set up to be 
  applied to the cluster
* secret-config: Generates encrypted secrets for use in the configs we set up for the cluster