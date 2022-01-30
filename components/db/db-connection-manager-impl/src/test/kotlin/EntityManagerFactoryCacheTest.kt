import net.corda.db.connection.manager.CordaDb
import net.corda.db.connection.manager.impl.DBConfigurationException
import net.corda.db.connection.manager.impl.DbConnectionsRepositoryImpl
import net.corda.db.connection.manager.impl.EntityManagerFactoryCacheImpl
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesSet
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
    private val dbConnectionsRepository = mock<DbConnectionsRepositoryImpl>() {
        on { this.clusterDataSource }.doReturn(clusterDatasource)
    }
    private val entityManagerFactoryFactory = mock<EntityManagerFactoryFactory>()
    private val allEntities = listOf(
        mock<JpaEntitiesSet> {
            on { persistenceUnitName }.doReturn(CordaDb.CordaCluster.persistenceUnitName)
            on { classes }.doReturn(setOf(ExampleEntity1::class.java))
        },
        mock {
            on { persistenceUnitName }.doReturn(CordaDb.RBAC.persistenceUnitName)
            on { classes }.doReturn(setOf(ExampleEntity2::class.java, ExampleEntity3::class.java))
        },
        mock {
            on { persistenceUnitName }.doReturn(CordaDb.Crypto.persistenceUnitName)
            on { classes }.doReturn(setOf(ExampleEntity3::class.java))
        }
    )
    private val otherEntitiesSet = mock<JpaEntitiesSet>() {
        on { persistenceUnitName }.doReturn("another-db")
        on { classes }.doReturn(setOf(ExampleEntity1::class.java, ExampleEntity3::class.java))
    }

    @Test
    fun `when clusterDbEntityManagerFactory create factory only once`() {
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

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
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        cache.getOrCreate(CordaDb.RBAC)

        verify(entityManagerFactoryFactory, times(1)).create(
            eq(CordaDb.RBAC.persistenceUnitName),
            argThat { this.equals(listOf(ExampleEntity2::class.java, ExampleEntity3::class.java)) },
            argThat { this.dataSource == rbacDatasource })
    }

    @Test
    fun `when getOrCreate and entitiesSet not defined, throw`() {
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, emptyList())

        assertThrows<DBConfigurationException> {
            cache.getOrCreate(CordaDb.RBAC)
        }
    }

    @Test
    fun `when getOrCreate and no ID, throw`() {
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        assertThrows<DBConfigurationException> {
            cache.getOrCreate(CordaDb.Crypto)
        }
    }

    @Test
    @Disabled
    fun `when getOrCreate from UUID and EnititiesSet create factory only once`() {
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        cache.getOrCreate(otherDBUUID, otherEntitiesSet)

        verify(entityManagerFactoryFactory, times(1)).create(
            eq("another-db"),
            argThat { this.equals(listOf(ExampleEntity1::class.java, ExampleEntity3::class.java)) },
            argThat { this.dataSource == otherDataSource })
    }
}