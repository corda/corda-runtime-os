import net.corda.db.connection.manager.CordaDb
import net.corda.db.connection.manager.impl.DBConfigurationException
import net.corda.db.connection.manager.impl.DbConnectionsRepository
import net.corda.db.connection.manager.impl.EntityManagerFactoryCache
import net.corda.orm.EntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import javax.sql.DataSource

class EntityManagerFactoryCacheTest {

    class ExampleEntity1
    class ExampleEntity2
    class ExampleEntity3

    private val otherDBUUID = UUID.randomUUID()
    private val clusterDatasource = mock<DataSource>()
    private val rbacDatasource = mock<DataSource>()
    private val otherDataSource = mock<DataSource>()
    private val dbConnectionsRepository = mock<DbConnectionsRepository>() {
        on { this.clusterDataSource }.doReturn(clusterDatasource)
        on { isInitialised }.doReturn(true)
    }
    private val entityManagerFactoryFactory = mock<EntityManagerFactoryFactory>()
    private val allEntities = listOf(
        mock<EntitiesSet> {
            on { name }.doReturn(CordaDb.CordaCluster.persistenceUnitName)
            on { content }.doReturn(setOf(ExampleEntity1::class.java))
        },
        mock {
            on { name }.doReturn(CordaDb.RBAC.persistenceUnitName)
            on { content }.doReturn(setOf(ExampleEntity2::class.java, ExampleEntity3::class.java))
        },
        mock {
            on { name }.doReturn(CordaDb.Crypto.persistenceUnitName)
            on { content }.doReturn(setOf(ExampleEntity3::class.java))
        }
    )
    private val otherEntitiesSet = mock<EntitiesSet>() {
        on { name }.doReturn("another-db")
        on { content }.doReturn(setOf(ExampleEntity1::class.java, ExampleEntity3::class.java))
    }

    @Test
    fun `when clusterDbEntityManagerFactory create factory only once`() {
        val cache = EntityManagerFactoryCache(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        cache.clusterDbEntityManagerFactory
        cache.clusterDbEntityManagerFactory

        verify(entityManagerFactoryFactory, times(1)).create(
            eq(CordaDb.CordaCluster.persistenceUnitName),
            argThat { this.equals(listOf(ExampleEntity1::class.java)) },
            argThat { this.dataSource == clusterDatasource })
    }

    @Test
    @Disabled
    fun `when getOrCreate from CordaDb create factory only once`() {
        val cache = EntityManagerFactoryCache(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        cache.getOrCreate(CordaDb.RBAC)

        verify(entityManagerFactoryFactory, times(1)).create(
            eq(CordaDb.RBAC.persistenceUnitName),
            argThat { this.equals(listOf(ExampleEntity2::class.java, ExampleEntity3::class.java)) },
            argThat { this.dataSource == rbacDatasource })
    }

    @Test
    fun `when getOrCreate and entitiesSet not defined, throw`() {
        val cache = EntityManagerFactoryCache(dbConnectionsRepository, entityManagerFactoryFactory, emptyList())

        assertThrows<DBConfigurationException> {
            cache.getOrCreate(CordaDb.RBAC)
        }
    }

    @Test
    fun `when getOrCreate and no ID, throw`() {
        val cache = EntityManagerFactoryCache(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        assertThrows<DBConfigurationException> {
            cache.getOrCreate(CordaDb.Crypto)
        }
    }

    @Test
    @Disabled
    fun `when getOrCreate from UUID and EnititiesSet create factory only once`() {
        val cache = EntityManagerFactoryCache(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        cache.getOrCreate(otherDBUUID, otherEntitiesSet)

        verify(entityManagerFactoryFactory, times(1)).create(
            eq("another-db"),
            argThat { this.equals(listOf(ExampleEntity1::class.java, ExampleEntity3::class.java)) },
            argThat { this.dataSource == otherDataSource })
    }

    @Test
    fun `when clusterDbEntityManagerFactory and not initialised throw`() {
        val cache = EntityManagerFactoryCache(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        whenever(dbConnectionsRepository.isInitialised).doReturn(false)

        assertThrows<DBConfigurationException> {
            cache.clusterDbEntityManagerFactory
        }
    }
}