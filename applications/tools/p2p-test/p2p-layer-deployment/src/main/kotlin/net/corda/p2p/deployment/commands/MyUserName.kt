package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException

object MyUserName {
    val userName by lazy {
        System.getenv("CORDA_ARTIFACTORY_USERNAME")?.substringBefore('@')
            ?: throw DeploymentException("Please set the CORDA_ARTIFACTORY_USERNAME environment variable")
    }
}
