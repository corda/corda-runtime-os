package net.corda.rocks.db.api

import net.corda.libs.configuration.SmartConfig

interface StorageManagerFactory {

    /**
     * Get a [StorageManager] singleton.
     * This will create a [StorageManager] if one has not already been created, otherwise it will create one to be reused for future calls
     * @param config used to instantiate the [StorageManager]
     * @return Singleton instance of a [StorageManager]
     */
    fun getStorageManger(config: SmartConfig) : StorageManager
}
