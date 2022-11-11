package net.corda.reconciliation.impl

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import org.slf4j.LoggerFactory

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

    constructor(
        dbReader: ReconcilerReader<K, V>,
        kafkaReader: ReconcilerReader<K, V>,
        writer: ReconcilerWriter<K, V>,
        keyClass: Class<K>,
        valueClass: Class<V>,
        coordinatorFactory: LifecycleCoordinatorFactory,
        reconciliationIntervalMs: Long
    ) : this(
        listOf(dbReader),
        kafkaReader,
        writer,
        keyClass,
        valueClass,
        coordinatorFactory,
        reconciliationIntervalMs
    )

    val name = "${ReconcilerImpl::class.java.name}<${keyClass.name}, ${valueClass.name}>"

    // Including the generic arguments to logger to differentiate between `ReconcilerImpl` parameterized classes
    private val logger = LoggerFactory.getLogger(name)

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
        logger.info("Starting")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping")
        coordinator.stop()
        coordinator.close()
    }
}
