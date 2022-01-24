import net.corda.db.connection.manager.CordaDb
import net.corda.db.connection.manager.impl.BootstrapConfigProvided
import net.corda.db.connection.manager.impl.DbConnectionManagerImpl
import net.corda.db.connection.manager.impl.DbConnectionsRepository
import net.corda.db.connection.manager.impl.EntityManagerFactoryCache
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.orm.EntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import javax.persistence.EntityManagerFactory

class DbConnectionManagerImplTest {
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }
    private val entityManagerFactoryFactory = mock<EntityManagerFactoryFactory>()
    private val dbConnectionsRepository = mock< DbConnectionsRepository>()
    private val entityManagerFactoryCache = mock< EntityManagerFactoryCache>()

    private val config = mock<SmartConfig>()

    @Test
    fun `when bootstrap post event`() {
        val mgr = DbConnectionManagerImpl(lifecycleCoordinatorFactory, entityManagerFactoryFactory)
        mgr.bootstrap(config)
        verify(lifecycleCoordinator).postEvent(BootstrapConfigProvided(config))
    }

    @Test
    fun `when start post start event()`() {
        val mgr = DbConnectionManagerImpl(lifecycleCoordinatorFactory, entityManagerFactoryFactory)
        mgr.start()
        verify(lifecycleCoordinator).start()
    }

    @Test
    fun `when stop post stop event()`() {
        val mgr = DbConnectionManagerImpl(lifecycleCoordinatorFactory, entityManagerFactoryFactory)
        mgr.stop()
        verify(lifecycleCoordinator).stop()
    }

    @Test
    fun `when close post close event()`() {
        val mgr = DbConnectionManagerImpl(lifecycleCoordinatorFactory, entityManagerFactoryFactory)
        mgr.close()
        verify(lifecycleCoordinator).close()
    }

    @Test
    fun `when isrunning return from coordinator()`() {
        val mgr = DbConnectionManagerImpl(lifecycleCoordinatorFactory, entityManagerFactoryFactory)
        mgr.isRunning
        verify(lifecycleCoordinator).isRunning
    }

    @Test
    fun `when get clusterDbEntityManagerFactory fetch from cache`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache,
        )

        val emf = mock<EntityManagerFactory>()
        whenever(entityManagerFactoryCache.clusterDbEntityManagerFactory).doReturn(emf)

        assertThat(mgr.clusterDbEntityManagerFactory).isSameAs(emf)
    }

    @Test
    fun `when getOrCreateEntityManagerFactory fetch from cache`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache,
        )

        val emf = mock<EntityManagerFactory>()
        whenever(entityManagerFactoryCache.getOrCreate(CordaDb.RBAC)).doReturn(emf)

        assertThat(mgr.getOrCreateEntityManagerFactory(CordaDb.RBAC)).isSameAs(emf)
    }

    @Test
    fun `when getOrCreateEntityManagerFactory with UUID fetch from cache`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache,
        )

        val entitiesSet = mock<EntitiesSet>()

        val emf = mock<EntityManagerFactory>()
        val id = UUID.randomUUID()
        whenever(entityManagerFactoryCache.getOrCreate(id, entitiesSet)).doReturn(emf)

        assertThat(mgr.getOrCreateEntityManagerFactory(id, entitiesSet)).isSameAs(emf)
    }

    @Test
    fun `when putConnection put on DbConnectionsRepo`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache,
        )

        val config = mock<SmartConfig>()
        val id = UUID.randomUUID()
        mgr.putConnection(id, config)

        verify(dbConnectionsRepository).put(id, config)
    }

    @Test
    @Disabled
    fun `when put persist connection`() {
        TODO()
    }
}