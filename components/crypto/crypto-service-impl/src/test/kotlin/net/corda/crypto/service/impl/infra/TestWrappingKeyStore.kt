package net.corda.crypto.service.impl.infra

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import java.util.concurrent.ConcurrentHashMap

class TestWrappingKeyStore(
    coordinatorFactory: LifecycleCoordinatorFactory,
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap()
) : WrappingKeyStore {
    val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<WrappingKeyStore>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
        keys[alias] = key
    }

    override fun findWrappingKey(alias: String): WrappingKeyInfo? = keys[alias]

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}

class TestWrappingKeyStore(
    coordinatorFactory: LifecycleCoordinatorFactory,
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap()
) : WrappingKeyStore {
    val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<WrappingKeyStore>()
    ) { e, c ->
        if (e is StartEvent) {
            c.updateStatus(LifecycleStatus.UP)
        }
    }

    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
        keys[alias] = key
    }

    override fun findWrappingKey(alias: String): WrappingKeyInfo? = keys[alias]

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}