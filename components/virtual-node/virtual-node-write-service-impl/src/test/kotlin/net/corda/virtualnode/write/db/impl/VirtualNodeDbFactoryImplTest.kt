package net.corda.virtualnode.write.db.impl

import com.typesafe.config.ConfigValueFactory
import net.corda.crypto.core.ShortHash
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.DatabaseConfig
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactoryImpl
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class VirtualNodeDbFactoryImplTest {
    private val smartConfigFactory = mock<SmartConfigFactory> {
        on { makeSecret(any(), any()) } doAnswer {
            mock<SmartConfig> {
                on { atPath(any()) } doReturn mock
                on { withValue(any(), any()) } doReturn mock
            }

        }
    }
    private val clusterConfiguration = mock<SmartConfig> {
        on { factory } doReturn smartConfigFactory
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { clusterConfig } doReturn clusterConfiguration
    }
    private val virtualNodesDbAdmin = mock<VirtualNodesDbAdmin> {
        on { createJdbcUrl(any()) } doReturn "jdbc:url"
    }
    private val schemaMigrator = mock<LiquibaseSchemaMigrator>()
    private val impl = VirtualNodeDbFactoryImpl(
        dbConnectionManager,
        virtualNodesDbAdmin,
        schemaMigrator,
    )

    @Test
    fun `createVNodeDbs create a database with the correct pool size`() {
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

        val vaultDmlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DML)?.config
        verify(vaultDmlConfig!!).withValue(DatabaseConfig.DB_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(10))
        verify(vaultDmlConfig).withValue(DatabaseConfig.DB_POOL_MIN_SIZE, ConfigValueFactory.fromAnyRef(0))
        val vaultDdlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DDL)?.config
        verify(vaultDdlConfig!!).withValue(DatabaseConfig.DB_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(1))
        verify(vaultDdlConfig, never()).withValue(eq(DatabaseConfig.DB_POOL_MIN_SIZE), any())
    }
}