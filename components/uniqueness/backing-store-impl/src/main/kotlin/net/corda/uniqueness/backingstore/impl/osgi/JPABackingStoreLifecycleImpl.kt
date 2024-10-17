package net.corda.uniqueness.backingstore.impl.osgi

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.ledger.libs.uniqueness.backingstore.BackingStore
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.uniqueness.backingstore.osgi.BackingStoreLifecycle
import net.corda.utilities.VisibleForTesting
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
/**
 * Corda lifecycle integration for the underlying [BackingStore] component.
 */
@Component(service = [BackingStoreLifecycle::class])
open class JPABackingStoreLifecycleImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = BackingStore::class)
    private val backingStore: BackingStore
) : BackingStoreLifecycle, BackingStore by backingStore {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val lifecycleCoordinator: LifecycleCoordinator = coordinatorFactory
        .createCoordinator<BackingStoreLifecycle>(::eventHandler)

    private val dependentComponents = DependentComponents.of(
        ::dbConnectionManager
    )

    private lateinit var jpaEntities: JpaEntitiesSet

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        log.info("Backing store starting")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Backing store stopping")
        lifecycleCoordinator.stop()
    }

    @VisibleForTesting
    fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("Backing store received event $event")
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            is RegistrationStatusChangeEvent -> {
                jpaEntities = jpaEntitiesRegistry.get(CordaDb.Uniqueness.persistenceUnitName)
                    ?: throw IllegalStateException(
                        "persistenceUnitName " +
                                "${CordaDb.Uniqueness.persistenceUnitName} is not registered."
                    )

                log.info("Backing store is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            else -> {
                log.warn("Unexpected event ${event}, ignoring")
            }
        }
    }
}
