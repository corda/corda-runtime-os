package net.corda.reconciliation.impl

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

@Component(service = [ReconcilerFactory::class])
class ReconcilerFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory
) : ReconcilerFactory {
    override fun <K : Any, V : Any> create(
        dbReader: ReconcilerReader<K, V>,
        kafkaReader: ReconcilerReader<K, V>,
        writer: ReconcilerWriter<V>,
        keyClass: Class<K>,
        valueClass: Class<V>,
        reconciliationIntervalMs: Long
    ) =
        ReconcilerImpl(dbReader, kafkaReader, writer, keyClass, valueClass, coordinatorFactory, reconciliationIntervalMs)
}