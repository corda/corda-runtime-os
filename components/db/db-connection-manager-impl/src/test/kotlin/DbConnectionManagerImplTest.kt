import net.corda.db.connection.manager.impl.BootstrapConfigProvided
import net.corda.db.connection.manager.impl.DbConnectionManagerImpl
import net.corda.db.connection.manager.impl.DbConnectionsRepositoryImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DbConnectionManagerImplTest {
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }
    private val dbConnectionsRepository = mock< DbConnectionsRepositoryImpl>()
    private val entityManagerFactoryFactory = mock<EntityManagerFactoryFactory>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry>()

    private val config = mock<SmartConfig>()

    @Test
    fun `when bootstrap post event`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)
        mgr.bootstrap(config)
        verify(lifecycleCoordinator).postEvent(BootstrapConfigProvided(config))
    }

    @Test
    fun `when start post start event()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)
        mgr.start()
        verify(lifecycleCoordinator).start()
    }

    @Test
    fun `when stop post stop event()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)
        mgr.stop()
        verify(lifecycleCoordinator).stop()
    }

    @Test
    fun `when close post close event()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)
        mgr.close()
        verify(lifecycleCoordinator).close()
    }

    @Test
    fun `when isrunning return from coordinator()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)
        mgr.isRunning
        verify(lifecycleCoordinator).isRunning
    }
/*
    // TODO
    @Test
    fun `when get clusterDbEntityManagerFactory fetch from cache`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)

        val emf = mock<EntityManagerFactory>()
        whenever(mgr.getClusterEntityManagerFactory()).doReturn(emf)

        assertThat(mgr.getClusterEntityManagerFactory()).isSameAs(emf)
    }

    @Test
    fun `when putConnection put on DbConnectionsRepo`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)

        val em = mock<EntityManager>()

        val config = mock<SmartConfig>()
        val name = "test config"
        val priv = DbPrivilege.DDL
        val description = "A really awesome database"
        val actor = "Spiderman"
        mgr.putConnection(em, name, priv, config, description, actor)

        verify(dbConnectionsRepository).put(em, name, priv, config, description, actor)
    }
 */
}