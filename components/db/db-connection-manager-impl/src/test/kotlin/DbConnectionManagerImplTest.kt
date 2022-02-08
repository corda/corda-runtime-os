import net.corda.db.connection.manager.impl.BootstrapConfigProvided
import net.corda.db.connection.manager.impl.DbConnectionManagerImpl
import net.corda.db.connection.manager.impl.DbConnectionsRepositoryImpl
import net.corda.db.connection.manager.impl.EntityManagerFactoryCacheImpl
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.orm.JpaEntitiesSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.persistence.EntityManagerFactory

class DbConnectionManagerImplTest {
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }
    private val dbConnectionsRepository = mock< DbConnectionsRepositoryImpl>()
    private val entityManagerFactoryCache = mock<EntityManagerFactoryCacheImpl>()

    private val config = mock<SmartConfig>()

    @Test
    fun `when bootstrap post event`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache)
        mgr.bootstrap(config)
        verify(lifecycleCoordinator).postEvent(BootstrapConfigProvided(config))
    }

    @Test
    fun `when start post start event()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache)
        mgr.start()
        verify(lifecycleCoordinator).start()
    }

    @Test
    fun `when stop post stop event()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache)
        mgr.stop()
        verify(lifecycleCoordinator).stop()
    }

    @Test
    fun `when close post close event()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache)
        mgr.close()
        verify(lifecycleCoordinator).close()
    }

    @Test
    fun `when isrunning return from coordinator()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache)
        mgr.isRunning
        verify(lifecycleCoordinator).isRunning
    }

    @Test
    fun `when get clusterDbEntityManagerFactory fetch from cache`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache)

        val emf = mock<EntityManagerFactory>()
        whenever(entityManagerFactoryCache.clusterDbEntityManagerFactory).doReturn(emf)

        assertThat(mgr.clusterDbEntityManagerFactory).isSameAs(emf)
    }

    @Test
    fun `when getOrCreateEntityManagerFactory fetch from cache`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache)

        val emf = mock<EntityManagerFactory>()
        whenever(entityManagerFactoryCache.getOrCreate(CordaDb.RBAC, DbPrivilege.DDL)).doReturn(emf)

        assertThat(mgr.getOrCreateEntityManagerFactory(CordaDb.RBAC, DbPrivilege.DDL)).isSameAs(emf)
    }

    @Test
    fun `when getOrCreateEntityManagerFactory with name fetch from cache`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache)

        val entitiesSet = mock<JpaEntitiesSet>()

        val emf = mock<EntityManagerFactory>()
        val name = "test config"
        val priv = DbPrivilege.DDL
        whenever(entityManagerFactoryCache.getOrCreate(name, priv, entitiesSet)).doReturn(emf)

        assertThat(mgr.getOrCreateEntityManagerFactory(name, priv, entitiesSet)).isSameAs(emf)
    }

    @Test
    fun `when putConnection put on DbConnectionsRepo`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            dbConnectionsRepository,
            entityManagerFactoryCache)

        val config = mock<SmartConfig>()
        val name = "test config"
        val priv = DbPrivilege.DDL
        val description = "A really awesome database"
        val actor = "Spiderman"
        mgr.putConnection(name, priv, config, description, actor)

        verify(dbConnectionsRepository).put(name, priv, config, description, actor)
    }
}