package net.corda.p2p.deployment.commands

object MyUserName {
    val userName by lazy {
        System.getenv("CORDA_ARTIFACTORY_USERNAME")?.substringBefore('@')
    }
}