package net.corda.crypto.persistence.inmemory

import net.corda.crypto.component.persistence.KeyValueMutator
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.SigningKeysPersistenceProvider
import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SigningKeysPersistenceProvider::class])
class InMemorySigningKeysPersistenceProvider @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory
) : SigningKeysPersistenceProvider {
    companion object {
        private val logger = contextLogger()
    }

    private val coordinator =
        coordinatorFactory.createCoordinator<SigningKeysPersistenceProvider> { e, _ -> eventHandler(e) }

    private val instances =
        ConcurrentHashMap<String, InMemoryKeyValuePersistence<SigningKeysRecord, SigningKeysRecord>>()

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
    ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord> =
        instances.computeIfAbsent(tenantId) {
            InMemoryKeyValuePersistence(
                mutator = mutator,
                data = ConcurrentHashMap<String, SigningKeysRecord>()
            )
        }

    override val isRunning: Boolean get() = coordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        coordinator.start()
        coordinator.postEvent(StartEvent())
    }

    override fun stop() {
        logger.info("Stopping...")
        coordinator.postEvent(StopEvent())
        coordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent) {
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> {
                logger.info("Setting status UP.")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                instances.clear()
            }
            else -> {
                logger.error("Unexpected event $event!")
            }
        }
    }
}