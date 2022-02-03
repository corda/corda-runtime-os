package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.getAndCheckEnv

object MyUserName {
    val userName by lazy {
        getAndCheckEnv("CORDA_ARTIFACTORY_USERNAME").substringBefore('@')
    }
}
