package net.corda.reconciliation.impl

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter

@Suppress("LongParameterList")
internal class ReconcilerImpl<K : Any, V : Any>(
    dbReaders: Collection<ReconcilerReader<K, V>>,
    kafkaReader: ReconcilerReader<K, V>,
    writer: ReconcilerWriter<K, V>,
    keyClass: Class<K>,
    valueClass: Class<V>,
    coordinatorFactory: LifecycleCoordinatorFactory,
    reconciliationIntervalMs: Long
) : Reconciler {

    val name = "${ReconcilerImpl::class.java.name}<${keyClass.name}, ${valueClass.name}>"

    private val coordinator =
        coordinatorFactory.createCoordinator(
            LifecycleCoordinatorName(name),
            ReconcilerEventHandler(
                dbReaders,
                kafkaReader,
                writer,
                keyClass,
                valueClass,
                reconciliationIntervalMs
            )
        )

    override fun updateInterval(intervalMs: Long) {
        coordinator.postEvent(ReconcilerEventHandler.UpdateIntervalEvent(intervalMs))
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
        coordinator.close()
    }
}
