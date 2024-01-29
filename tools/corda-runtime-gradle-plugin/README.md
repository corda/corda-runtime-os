# Corda-Runtime-Gradle-Plugin

A Gradle plugin that wraps a subset of the SDK functions to facilitate their use in developer and CI scenarios.  
This supersedes the CSDE Gradle plugin.

Add the following extension properties

```groovy
    cordaRuntimeGradlePlugin {
        combinedWorkerVersion = "5.2.0.0"
        postgresJdbcVersion = "42.6.0"
        // Only need to supply these if you want to use an unpublished version
        artifactoryUsername = findProperty('cordaArtifactoryUsername') ?: System.getenv('CORDA_ARTIFACTORY_USERNAME')
        artifactoryPassword = findProperty('cordaArtifactoryPassword') ?: System.getenv('CORDA_ARTIFACTORY_PASSWORD')
    }
```