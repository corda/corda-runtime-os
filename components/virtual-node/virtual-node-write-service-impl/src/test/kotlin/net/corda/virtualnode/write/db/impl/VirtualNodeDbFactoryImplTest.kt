package net.corda.virtualnode.write.db.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.crypto.core.ShortHash
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.DatabaseConfig
import net.corda.schema.configuration.VirtualNodeDatasourceConfig
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactoryImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VirtualNodeDbFactoryImplTest {

    private val smartConfigFactory = mock<SmartConfigFactory> {
        on { makeSecret(any(), any()) } doAnswer {
            mock<SmartConfig> {
                on { atPath(any()) } doReturn mock
                on { withValue(any(), any()) } doReturn mock
            }
        }

        whenever(it.create(any())).doAnswer {
            SmartConfigImpl(it.arguments[0] as Config, mock(), mock())
        }
    }
    private val clusterConfiguration = mock<SmartConfig> {
        on { factory } doReturn smartConfigFactory
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { clusterConfig } doReturn clusterConfiguration
    }

    private val JDBC_URL = "jdbc:url"
    private val virtualNodesDbAdmin = mock<VirtualNodesDbAdmin> {
        on { createJdbcUrl(any()) } doReturn JDBC_URL
    }
    private val schemaMigrator = mock<LiquibaseSchemaMigrator>()


    private val configFactory = SmartConfigFactory.createWithoutSecurityServices()
    private val virtualNodesDdlPoolConfig = configFactory.create(
        ConfigFactory.empty()
            .withValue(VirtualNodeDatasourceConfig.VNODE_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(1))
            .withValue(VirtualNodeDatasourceConfig.VNODE_POOL_IDLE_TIMEOUT_SECONDS, ConfigValueFactory.fromAnyRef(120))
            .withValue(VirtualNodeDatasourceConfig.VNODE_POOL_MAX_LIFETIME_SECONDS, ConfigValueFactory.fromAnyRef(1800))
            .withValue(VirtualNodeDatasourceConfig.VNODE_POOL_KEEPALIVE_TIME_SECONDS, ConfigValueFactory.fromAnyRef(0))
            .withValue(VirtualNodeDatasourceConfig.VNODE_VALIDATION_TIMEOUT_SECONDS, ConfigValueFactory.fromAnyRef(5))
    )

    private val virtualNodesDmlPoolConfig = configFactory.create(
        ConfigFactory.empty()
            .withValue(VirtualNodeDatasourceConfig.VNODE_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(10))
            .withValue(VirtualNodeDatasourceConfig.VNODE_POOL_MIN_SIZE, ConfigValueFactory.fromAnyRef(0))
            .withValue(VirtualNodeDatasourceConfig.VNODE_POOL_IDLE_TIMEOUT_SECONDS, ConfigValueFactory.fromAnyRef(120))
            .withValue(VirtualNodeDatasourceConfig.VNODE_POOL_MAX_LIFETIME_SECONDS, ConfigValueFactory.fromAnyRef(1800))
            .withValue(VirtualNodeDatasourceConfig.VNODE_POOL_KEEPALIVE_TIME_SECONDS, ConfigValueFactory.fromAnyRef(0))
            .withValue(VirtualNodeDatasourceConfig.VNODE_VALIDATION_TIMEOUT_SECONDS, ConfigValueFactory.fromAnyRef(5))
    )

    private val impl = VirtualNodeDbFactoryImpl(
        dbConnectionManager,
        virtualNodesDbAdmin,
        schemaMigrator,
        virtualNodesDdlPoolConfig,
        virtualNodesDmlPoolConfig
    )

    @Test
    fun `createVNodeDbs creates expected VNode datasource configuration`() {
        val request = mock<VirtualNodeCreateRequest> {
            on { vaultDdlConnection } doReturn ""
            on { vaultDmlConnection } doReturn ""
            on { cryptoDdlConnection } doReturn ""
            on { cryptoDmlConnection } doReturn ""
            on { uniquenessDdlConnection } doReturn ""
            on { uniquenessDmlConnection } doReturn ""
        }

        val dbs = impl.createVNodeDbs(
            ShortHash.of("1234123412341234"),
            request,
        )


        val vaultDdlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DDL)?.config!!
        verify(vaultDdlConfig, never()).withValue(eq(DatabaseConfig.JDBC_DRIVER), any())
        verify(vaultDdlConfig).withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(JDBC_URL))
        verify(vaultDdlConfig).withValue(DatabaseConfig.DB_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(1))
        verify(vaultDdlConfig, never()).withValue(eq(DatabaseConfig.DB_POOL_MIN_SIZE), any())

        val vaultDmlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DML)?.config!!
        verify(vaultDmlConfig, never()).withValue(eq(DatabaseConfig.JDBC_DRIVER), any())
        verify(vaultDmlConfig).withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(JDBC_URL))
        verify(vaultDmlConfig).withValue(DatabaseConfig.DB_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(10))
        // VNode DML min pool size needs to be 0, because VNodes connections can pile up and exhaust the DB
        verify(vaultDmlConfig).withValue(DatabaseConfig.DB_POOL_MIN_SIZE, ConfigValueFactory.fromAnyRef(0))

        val uniquenessDmlConfig = dbs[VirtualNodeDbType.UNIQUENESS]?.dbConnections?.get(DbPrivilege.DML)?.config!!
        verify(uniquenessDmlConfig, never()).withValue(eq(DatabaseConfig.JDBC_DRIVER), any())

    }

    @Test
    fun `createVNodeDbs creates expected VNode datasource configuration with no uniqueness db`() {
        val request = mock<VirtualNodeCreateRequest> {
            on { vaultDdlConnection } doReturn ""
            on { vaultDmlConnection } doReturn ""
            on { cryptoDdlConnection } doReturn ""
            on { cryptoDmlConnection } doReturn ""
            on { uniquenessDdlConnection } doReturn "none"
            on { uniquenessDmlConnection } doReturn "none"
        }

        val dbs = impl.createVNodeDbs(
            ShortHash.of("1234123412341234"),
            request,
        )


        val vaultDdlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DDL)?.config!!
        verify(vaultDdlConfig, never()).withValue(eq(DatabaseConfig.JDBC_DRIVER), any())
        verify(vaultDdlConfig).withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(JDBC_URL))
        verify(vaultDdlConfig).withValue(DatabaseConfig.DB_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(1))
        verify(vaultDdlConfig, never()).withValue(eq(DatabaseConfig.DB_POOL_MIN_SIZE), any())

        val vaultDmlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DML)?.config!!
        verify(vaultDmlConfig, never()).withValue(eq(DatabaseConfig.JDBC_DRIVER), any())
        verify(vaultDmlConfig).withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(JDBC_URL))
        verify(vaultDmlConfig).withValue(DatabaseConfig.DB_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(10))
        // VNode DML min pool size needs to be 0, because VNodes connections can pile up and exhaust the DB
        verify(vaultDmlConfig).withValue(DatabaseConfig.DB_POOL_MIN_SIZE, ConfigValueFactory.fromAnyRef(0))

        val uniquenessDmlConfig = dbs[VirtualNodeDbType.UNIQUENESS]?.dbConnections?.get(DbPrivilege.DML)?.config
        assertNull(uniquenessDmlConfig)

        val uniquenessDdlConfig = dbs[VirtualNodeDbType.UNIQUENESS]?.dbConnections?.get(DbPrivilege.DDL)?.config
        assertNull(uniquenessDdlConfig)
    }

    @Test
    fun `createVNodeDbs sets ddlConnectionProvided to false and isPlatformManagedDb to true when using the cluster DB`() {
        val request = VirtualNodeCreateRequest(
            /* holdingId = */ mock(),
            /* cpiFileChecksum = */ "",
            /* vaultDdlConnection = */ "",
            /* vaultDmlConnection = */ "",
            /* cryptoDdlConnection = */ "",
            /* cryptoDmlConnection = */ "",
            /* uniquenessDdlConnection = */ "",
            /* uniquenessDmlConnection = */ "",
            /* updateActor = */ ""
        )

        val dbs = impl.createVNodeDbs(ShortHash.of("1234123412341234"), request)

        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.ddlConnectionProvided, dbType.name) } })
        assertAll(dbs.map { (dbType, db) -> { assertTrue(db.isPlatformManagedDb, dbType.name) } })
    }

    @Test
    fun `createVNodeDbs sets ddlConnectionProvided to true and isPlatformManagedDb to false when provided with DML and DDL connection`() {
        val request = VirtualNodeCreateRequest(
            /* holdingId = */ mock(),
            /* cpiFileChecksum = */ "",
            /* vaultDdlConnection = */ "{}",
            /* vaultDmlConnection = */ "{}",
            /* cryptoDdlConnection = */ "{}",
            /* cryptoDmlConnection = */ "{}",
            /* uniquenessDdlConnection = */ "{}",
            /* uniquenessDmlConnection = */ "{}",
            /* updateActor = */ ""
        )

        val dbs = impl.createVNodeDbs(ShortHash.of("1234123412341234"), request)

        assertAll(dbs.map { (dbType, db) -> { assertTrue(db.ddlConnectionProvided, dbType.name) } })
        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.isPlatformManagedDb, dbType.name) } })
    }

    @Test
    @Suppress("MaxLineLength")
    fun `createVNodeDbs sets ddlConnectionProvided and isPlatformManaged to false when provided with DML connection but no DDL connection`() {
        val request = VirtualNodeCreateRequest(
            /* holdingId = */ mock(),
            /* cpiFileChecksum = */ "",
            /* vaultDdlConnection = */ "",
            /* vaultDmlConnection = */ "{}",
            /* cryptoDdlConnection = */ "",
            /* cryptoDmlConnection = */ "{}",
            /* uniquenessDdlConnection = */ "",
            /* uniquenessDmlConnection = */ "{}",
            /* updateActor = */ ""
        )

        val dbs = impl.createVNodeDbs(ShortHash.of("1234123412341234"), request)

        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.ddlConnectionProvided, dbType.name) } })
        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.isPlatformManagedDb, dbType.name) } })
    }

    @Test
    @Suppress("MaxLineLength")
    fun `createVNodeDbs sets ddlConnectionProvided to false and isPlatformManagedDb to true when provided with DDL no DML connection - uses cluster DB, DDL ignored`() {
        val request = VirtualNodeCreateRequest(
            /* holdingId = */ mock(),
            /* cpiFileChecksum = */ "",
            /* vaultDdlConnection = */ "{}",
            /* vaultDmlConnection = */ "",
            /* cryptoDdlConnection = */ "{}",
            /* cryptoDmlConnection = */ "",
            /* uniquenessDdlConnection = */ "{}",
            /* uniquenessDmlConnection = */ "",
            /* updateActor = */ ""
        )

        val dbs = impl.createVNodeDbs(ShortHash.of("1234123412341234"), request)

        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.ddlConnectionProvided, dbType.name) } })
        assertAll(dbs.map { (dbType, db) -> { assertTrue(db.isPlatformManagedDb, dbType.name) } })
    }

    @Test
    fun `createVNodeDbs sets ddlConnectionProvided and isPlatformManagedDb to false for uniqueness when uniqueness is none`() {
        val request = VirtualNodeCreateRequest(
            /* holdingId = */ mock(),
            /* cpiFileChecksum = */ "",
            /* vaultDdlConnection = */ "{}",
            /* vaultDmlConnection = */ "{}",
            /* cryptoDdlConnection = */ "{}",
            /* cryptoDmlConnection = */ "{}",
            /* uniquenessDdlConnection = */ "none",
            /* uniquenessDmlConnection = */ "none",
            /* updateActor = */ ""
        )

        val dbs = impl.createVNodeDbs(ShortHash.of("1234123412341234"), request)

        // Uniqueness is set to false
        assertAll(dbs.filter { (dbType, _) -> dbType == VirtualNodeDbType.UNIQUENESS }
            .map { (dbType, db) -> { assertFalse(db.ddlConnectionProvided, dbType.name) } })
        // Other types are set to true
        assertAll(dbs.filter { (dbType, _) -> dbType != VirtualNodeDbType.UNIQUENESS }
            .map { (dbType, db) -> { assertTrue(db.ddlConnectionProvided, dbType.name) } })
        // isPlatformManagedDb is set to false
        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.isPlatformManagedDb, dbType.name) } })
    }

}