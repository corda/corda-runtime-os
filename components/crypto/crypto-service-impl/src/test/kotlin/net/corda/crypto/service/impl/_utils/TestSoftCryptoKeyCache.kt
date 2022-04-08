package net.corda.crypto.service.impl._utils

import net.corda.crypto.persistence.SoftCryptoKeyCache
import net.corda.crypto.persistence.SoftCryptoKeyCacheActions
import net.corda.crypto.persistence.SoftCryptoKeyCacheProvider
import net.corda.crypto.persistence.WrappingKey
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import java.util.concurrent.ConcurrentHashMap

class TestSoftCryptoKeyCacheProvider(
    coordinatorFactory: LifecycleCoordinatorFactory
) : SoftCryptoKeyCacheProvider {
    private val instance = TestSoftCryptoKeyCache()

    val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<SoftCryptoKeyCacheProvider>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun getInstance(passphrase: String, salt: String): SoftCryptoKeyCache {
        check(isRunning) {
            "The provider is in invalid state."
        }
        return instance
    }
}

class TestSoftCryptoKeyCache : SoftCryptoKeyCache {
    private val keys = ConcurrentHashMap<String, WrappingKey>()
    override fun act(): SoftCryptoKeyCacheActions = TestSoftCryptoKeyCacheActions(keys)
}

class TestSoftCryptoKeyCacheActions(
    private val keys: MutableMap<String, WrappingKey>
) : SoftCryptoKeyCacheActions {
    override fun saveWrappingKey(alias: String, key: WrappingKey, failIfExists: Boolean) {
        if(keys.putIfAbsent(alias, key) != null) {
            if(failIfExists) {
                throw IllegalArgumentException("The key $alias already exists.")
            }
        }
    }
    override fun findWrappingKey(alias: String): WrappingKey? = keys[alias]
    override fun close() = Unit
}
