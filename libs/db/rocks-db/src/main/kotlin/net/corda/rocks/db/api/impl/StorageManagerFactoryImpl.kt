package net.corda.rocks.db.api.impl

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import net.corda.libs.configuration.SmartConfig
import net.corda.rocks.db.api.StorageManager
import net.corda.rocks.db.api.StorageManagerFactory
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory

@Component(service = [StorageManagerFactory::class], scope = ServiceScope.SINGLETON)
class StorageManagerFactoryImpl : StorageManagerFactory {
    companion object {
        private val storageManagerLock = ReentrantLock()
        private var storageManager: StorageManager? = null
        private var counter: Int = 0
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getStorageManger(config: SmartConfig): StorageManager {
        return storageManagerLock.withLock {
            val currentStorageTmp = storageManager
            counter++
            if (currentStorageTmp != null) {
                currentStorageTmp
            } else {
                logger.debug { "Creating RocksDB storage manager and initialising..." }
                val storageManagerInitiated = StorageManagerImpl(config)
                storageManager = storageManagerInitiated
                storageManagerInitiated.start()
                logger.debug { "RocksDB Storage manager initialized" }
                storageManagerInitiated
            }
        }
    }
}