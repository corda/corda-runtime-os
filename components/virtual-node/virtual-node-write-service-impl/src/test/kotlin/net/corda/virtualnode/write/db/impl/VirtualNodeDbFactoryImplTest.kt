package net.corda.virtualnode.write.db.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactoryImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

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

    private val JDBC_URL = "jdbc:url"
    private val virtualNodesDbAdmin = mock<VirtualNodesDbAdmin> {
        on { createJdbcUrl(any()) } doReturn JDBC_URL
    }
    private val schemaMigrator = mock<LiquibaseSchemaMigrator>()
    private val impl = VirtualNodeDbFactoryImpl(
        dbConnectionManager,
        virtualNodesDbAdmin,
        schemaMigrator,
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

        val vaultDmlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DML)?.datasourceOverrides
        assertNull(vaultDmlConfig!!.jdbcDriver)
        assertEquals(JDBC_URL, vaultDmlConfig.jdbcUrl)
//        assertEquals(10, vaultDmlConfig.)

        val vaultDdlConfig = dbs[VirtualNodeDbType.VAULT]?.dbConnections?.get(DbPrivilege.DDL)?.datasourceOverrides
        assertNull(vaultDdlConfig!!.jdbcDriver)
        assertEquals(JDBC_URL, vaultDdlConfig.jdbcUrl)
    }
}