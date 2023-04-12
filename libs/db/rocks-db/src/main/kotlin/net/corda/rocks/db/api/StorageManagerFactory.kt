package net.corda.rocks.db.api

import net.corda.libs.configuration.SmartConfig

interface StorageManagerFactory {
    fun getStorageManger(config: SmartConfig) : StorageManager
}