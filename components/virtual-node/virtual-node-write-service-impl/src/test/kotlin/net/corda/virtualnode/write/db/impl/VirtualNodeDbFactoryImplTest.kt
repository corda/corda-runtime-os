package net.corda.virtualnode.write.db.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.crypto.core.ShortHash
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.DatabaseConfig
import net.corda.schema.configuration.VirtualNodeDatasourceConfig
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeConnectionStrings
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactoryImpl
import org.assertj.core.api.AssertionsForClassTypes.assertThat
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
        on { getJdbcUrl() } doReturn JDBC_URL
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
        val request = mock<VirtualNodeConnectionStrings> {
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
        val request = mock<VirtualNodeConnectionStrings> {
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
    @Suppress("MaxLineLength")
    fun `createVNodeDbs with custom pool config values in external connection strings creates VNode datasource configuration with custom pool values`() {
        val externalConnectionString = """
            {"database":{"jdbc":{"url":""},"pass":"","user":"",
            "pool":{
            "idleTimeoutSeconds":999,
            "keepaliveTimeSeconds":999,
            "maxLifetimeSeconds":999,
            "max_size":999,
            "min_size":999,
            "validationTimeoutSeconds":999
            }}}
        """.trimIndent()
        val request = VirtualNodeConnectionStrings(
            /* vaultDdlConnection = */
            externalConnectionString,
            /* vaultDmlConnection = */
            externalConnectionString,
            /* cryptoDdlConnection = */
            externalConnectionString,
            /* cryptoDmlConnection = */
            externalConnectionString,
            /* uniquenessDdlConnection = */
            externalConnectionString,
            /* uniquenessDmlConnection = */
            externalConnectionString,
        )

        val dbs = impl.createVNodeDbs(
            ShortHash.of("1234123412341234"),
            request,
        )

        val vaultDdlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DDL)?.config!!
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_IDLE_TIMEOUT_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_KEEPALIVE_TIME_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_MAX_LIFETIME_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_MAX_SIZE)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_MIN_SIZE)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_VALIDATION_TIMEOUT_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(999))

        val vaultDmlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DML)?.config!!
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_IDLE_TIMEOUT_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_KEEPALIVE_TIME_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_MAX_LIFETIME_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_MAX_SIZE)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_MIN_SIZE)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_VALIDATION_TIMEOUT_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(999))
    }

    @Test
    @Suppress("MaxLineLength")
    fun `createVNodeDbs with no pool config values in external connection strings creates VNode datasource configuration with default pool values`() {
        val externalConnectionString = """
            {"database":{"jdbc":{"url":""},"pass":"","user":""}}
        """.trimIndent()
        val request = VirtualNodeConnectionStrings(
            /* vaultDdlConnection = */
            externalConnectionString,
            /* vaultDmlConnection = */
            externalConnectionString,
            /* cryptoDdlConnection = */
            externalConnectionString,
            /* cryptoDmlConnection = */
            externalConnectionString,
            /* uniquenessDdlConnection = */
            externalConnectionString,
            /* uniquenessDmlConnection = */
            externalConnectionString,
        )

        val dbs = impl.createVNodeDbs(
            ShortHash.of("1234123412341234"),
            request,
        )

        val vaultDdlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DDL)?.config!!
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_IDLE_TIMEOUT_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(120))
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_KEEPALIVE_TIME_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(0))
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_MAX_LIFETIME_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(1800))
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_MAX_SIZE)).isEqualTo(ConfigValueFactory.fromAnyRef(1))
        assertFalse(vaultDdlConfig.hasPath(DatabaseConfig.DB_POOL_MIN_SIZE))
        assertThat(vaultDdlConfig.getValue(DatabaseConfig.DB_POOL_VALIDATION_TIMEOUT_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(5))

        val vaultDmlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DML)?.config!!
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_IDLE_TIMEOUT_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(120))
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_KEEPALIVE_TIME_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(0))
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_MAX_LIFETIME_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(1800))
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_MAX_SIZE)).isEqualTo(ConfigValueFactory.fromAnyRef(10))
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_MIN_SIZE)).isEqualTo(ConfigValueFactory.fromAnyRef(0))
        assertThat(vaultDmlConfig.getValue(DatabaseConfig.DB_POOL_VALIDATION_TIMEOUT_SECONDS)).isEqualTo(ConfigValueFactory.fromAnyRef(5))
    }

    @Test
    fun `createVNodeDbs sets ddlConnectionProvided to false and isPlatformManagedDb to true when using the cluster DB`() {
        val request = VirtualNodeConnectionStrings(
            /* vaultDdlConnection = */
            "",
            /* vaultDmlConnection = */
            "",
            /* cryptoDdlConnection = */
            "",
            /* cryptoDmlConnection = */
            "",
            /* uniquenessDdlConnection = */
            "",
            /* uniquenessDmlConnection = */
            "",
        )

        val dbs = impl.createVNodeDbs(ShortHash.of("1234123412341234"), request)

        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.ddlConnectionProvided, dbType.name) } })
        assertAll(dbs.map { (dbType, db) -> { assertTrue(db.isPlatformManagedDb, dbType.name) } })
    }

    @Test
    fun `createVNodeDbs sets ddlConnectionProvided to true and isPlatformManagedDb to false when provided with DML and DDL connection`() {
        val request = VirtualNodeConnectionStrings(
            /* vaultDdlConnection = */
            "{}",
            /* vaultDmlConnection = */
            "{}",
            /* cryptoDdlConnection = */
            "{}",
            /* cryptoDmlConnection = */
            "{}",
            /* uniquenessDdlConnection = */
            "{}",
            /* uniquenessDmlConnection = */
            "{}",
        )

        val dbs = impl.createVNodeDbs(ShortHash.of("1234123412341234"), request)

        assertAll(dbs.map { (dbType, db) -> { assertTrue(db.ddlConnectionProvided, dbType.name) } })
        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.isPlatformManagedDb, dbType.name) } })
    }

    @Test
    @Suppress("MaxLineLength")
    fun `createVNodeDbs sets ddlConnectionProvided and isPlatformManaged to false when provided with DML connection but no DDL connection`() {
        val request = VirtualNodeConnectionStrings(
            /* vaultDdlConnection = */
            "",
            /* vaultDmlConnection = */
            "{}",
            /* cryptoDdlConnection = */
            "",
            /* cryptoDmlConnection = */
            "{}",
            /* uniquenessDdlConnection = */
            "",
            /* uniquenessDmlConnection = */
            "{}",
        )

        val dbs = impl.createVNodeDbs(ShortHash.of("1234123412341234"), request)

        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.ddlConnectionProvided, dbType.name) } })
        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.isPlatformManagedDb, dbType.name) } })
    }

    @Test
    @Suppress("MaxLineLength")
    fun `createVNodeDbs sets ddlConnectionProvided to false and isPlatformManagedDb to true when provided with DDL no DML connection - uses cluster DB, DDL ignored`() {
        val request = VirtualNodeConnectionStrings(
            /* vaultDdlConnection = */
            "{}",
            /* vaultDmlConnection = */
            "",
            /* cryptoDdlConnection = */
            "{}",
            /* cryptoDmlConnection = */
            "",
            /* uniquenessDdlConnection = */
            "{}",
            /* uniquenessDmlConnection = */
            "",
        )

        val dbs = impl.createVNodeDbs(ShortHash.of("1234123412341234"), request)

        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.ddlConnectionProvided, dbType.name) } })
        assertAll(dbs.map { (dbType, db) -> { assertTrue(db.isPlatformManagedDb, dbType.name) } })
    }

    @Test
    fun `createVNodeDbs sets ddlConnectionProvided and isPlatformManagedDb to false for uniqueness when uniqueness is none`() {
        val request = VirtualNodeConnectionStrings(
            /* vaultDdlConnection = */
            "{}",
            /* vaultDmlConnection = */
            "{}",
            /* cryptoDdlConnection = */
            "{}",
            /* cryptoDmlConnection = */
            "{}",
            /* uniquenessDdlConnection = */
            "none",
            /* uniquenessDmlConnection = */
            "none",
        )

        val dbs = impl.createVNodeDbs(ShortHash.of("1234123412341234"), request)

        // Uniqueness is set to false
        assertAll(
            dbs.filter { (dbType, _) -> dbType == VirtualNodeDbType.UNIQUENESS }
                .map { (dbType, db) -> { assertFalse(db.ddlConnectionProvided, dbType.name) } }
        )
        // Other types are set to true
        assertAll(
            dbs.filter { (dbType, _) -> dbType != VirtualNodeDbType.UNIQUENESS }
                .map { (dbType, db) -> { assertTrue(db.ddlConnectionProvided, dbType.name) } }
        )
        // isPlatformManagedDb is set to false
        assertAll(dbs.map { (dbType, db) -> { assertFalse(db.isPlatformManagedDb, dbType.name) } })
    }
}
