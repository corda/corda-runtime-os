package net.corda.crypto.softhsm.impl.infra

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import java.util.concurrent.ConcurrentHashMap

// This is open that it can be subclassed with overrides which count invocations.
open class TestWrappingKeyStore(
    coordinatorFactory: LifecycleCoordinatorFactory,
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap()
) : WrappingKeyStore {
    val findCounter = mutableMapOf<String, Int>()
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

    override fun findWrappingKey(alias: String): WrappingKeyInfo? {
        findCounter[alias] = findCounter[alias]?.plus(1) ?: 1
        return keys[alias]
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}

