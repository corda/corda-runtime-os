package net.corda.libs.configuration.read.factory

import net.corda.libs.configuration.read.ConfigRepository

interface ConfigRepositoryFactory {

    fun createRepository(): ConfigRepository
}