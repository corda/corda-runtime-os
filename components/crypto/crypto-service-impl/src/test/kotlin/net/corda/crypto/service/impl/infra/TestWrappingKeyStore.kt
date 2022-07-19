package net.corda.crypto.service.impl.infra

import net.corda.crypto.persistence.wrapping.WrappingKeyStore
import net.corda.crypto.persistence.soft.SoftCryptoKeyStoreActions
import net.corda.crypto.persistence.soft.SoftCryptoKeyStoreProvider
import net.corda.crypto.core.aes.WrappingKey
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import java.util.concurrent.ConcurrentHashMap

class TestSoftCryptoKeyStoreProvider(
    coordinatorFactory: LifecycleCoordinatorFactory
) : SoftCryptoKeyStoreProvider {
    private val instance = TestWrappingKeyStore()

    val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<SoftCryptoKeyStoreProvider>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun getInstance(): WrappingKeyStore {
        check(isRunning) {
            "The provider is in invalid state."
        }
        return instance
    }
}

class TestWrappingKeyStore : WrappingKeyStore {
    val keys = ConcurrentHashMap<String, WrappingKey>()
    override fun act(): SoftCryptoKeyStoreActions = TestSoftCryptoKeyStoreActions(keys)
    override fun close() = Unit
}

class TestSoftCryptoKeyStoreActions(
    private val keys: MutableMap<String, WrappingKey>
) : SoftCryptoKeyStoreActions {
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
