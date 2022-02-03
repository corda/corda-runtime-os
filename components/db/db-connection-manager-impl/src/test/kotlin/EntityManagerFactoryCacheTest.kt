import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.impl.DbConnectionsRepositoryImpl
import net.corda.db.connection.manager.impl.EntityManagerFactoryCacheImpl
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesSet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import javax.sql.DataSource

class EntityManagerFactoryCacheTest {

    class ExampleEntity1
    class ExampleEntity2
    class ExampleEntity3

    private val otherDB = "another-db"
    private val clusterDatasource = mock<DataSource>()
    private val rbacDatasource = mock<DataSource>()
    private val otherDataSource = mock<DataSource>()
    private val dbConnectionsRepository = mock<DbConnectionsRepositoryImpl>() {
        on { this.clusterDataSource }.doReturn(clusterDatasource)
        on { get(CordaDb.RBAC.persistenceUnitName, DbPrivilege.DDL) }.doReturn(rbacDatasource)
        on { get(otherDB, DbPrivilege.DDL) }.doReturn(otherDataSource)
    }
    private val otherEntitiesSet = mock<JpaEntitiesSet>() {
        on { persistenceUnitName }.doReturn(otherDB)
        on { classes }.doReturn(setOf(ExampleEntity1::class.java, ExampleEntity3::class.java))
    }
    private val allEntities = listOf(
        mock {
            on { persistenceUnitName }.doReturn(CordaDb.CordaCluster.persistenceUnitName)
            on { classes }.doReturn(setOf(ExampleEntity1::class.java))
        },
        mock {
            on { persistenceUnitName }.doReturn(CordaDb.RBAC.persistenceUnitName)
            on { classes }.doReturn(setOf(ExampleEntity2::class.java, ExampleEntity3::class.java))
        },
        otherEntitiesSet,
    )
    private val entityManagerFactoryFactory = mock<EntityManagerFactoryFactory>() {
        on { create(any(), any(), any()) }.doReturn(mock())
    }

    @Test
    fun `when clusterDbEntityManagerFactory create factory only once`() {
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        cache.clusterDbEntityManagerFactory
        cache.clusterDbEntityManagerFactory

        verify(entityManagerFactoryFactory, times(1)).create(
            eq(CordaDb.CordaCluster.persistenceUnitName),
            argThat { this == listOf(ExampleEntity1::class.java) },
            argThat { this.dataSource == clusterDatasource })
    }

    @Test
    fun `when getOrCreate from CordaDb create factory only once`() {
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        cache.getOrCreate(CordaDb.RBAC, DbPrivilege.DDL)

        verify(entityManagerFactoryFactory, times(1)).create(
            eq(CordaDb.RBAC.persistenceUnitName),
            argThat { this == listOf(ExampleEntity2::class.java, ExampleEntity3::class.java) },
            argThat { this.dataSource == rbacDatasource })
    }

    @Test
    fun `when getOrCreate and entitiesSet not defined, throw`() {
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, emptyList())

        assertThrows<DBConfigurationException> {
            cache.getOrCreate(CordaDb.RBAC, DbPrivilege.DDL)
        }
    }

    @Test
    fun `when getOrCreate and no EnititiesSet, throw`() {
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        assertThrows<DBConfigurationException> {
            cache.getOrCreate(CordaDb.Crypto, DbPrivilege.DDL)
        }
    }

    @Test
    fun `when getOrCreate from name and EnititiesSet create factory only once`() {
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        cache.getOrCreate(otherDB, DbPrivilege.DDL, otherEntitiesSet)

        verify(entityManagerFactoryFactory, times(1)).create(
            eq(otherDB),
            argThat { this == listOf(ExampleEntity1::class.java, ExampleEntity3::class.java) },
            argThat { this.dataSource == otherDataSource })
    }

    @Test
    fun `when getOrCreate from name and EnititiesSet and connection missing throw`() {
        val cache = EntityManagerFactoryCacheImpl(dbConnectionsRepository, entityManagerFactoryFactory, allEntities)

        assertThrows<DBConfigurationException> {
            cache.getOrCreate(otherDB, DbPrivilege.DML, otherEntitiesSet)
        }
    }
}