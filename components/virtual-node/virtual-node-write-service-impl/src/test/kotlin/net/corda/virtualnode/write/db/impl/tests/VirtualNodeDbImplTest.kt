package net.corda.virtualnode.write.db.impl.tests

import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.write.db.impl.VirtualNodesDbAdmin
import net.corda.virtualnode.write.db.impl.writer.DbConnection
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Connection

class VirtualNodeDbImplTest {

    private val password = "password"
    private val ddlUuser = "ddluser"
    private val dmlUuser = "dmluser"
    private val ddlConfig = mock<SmartConfig>()
    private val holdingIdShortHash = ShortHash.of("AAAAAAAAAAAA")
    private val dbType = VirtualNodeDbType.VAULT
    private val schema = dbType.getSchemaName(holdingIdShortHash)
    private val dbAdmin = mock<VirtualNodesDbAdmin>().apply {
        whenever(userExists(any())).thenReturn(false)
    }
    private val dbConnectionManager = mock<DbConnectionManager>()
    private val schemaMigrator = mock<LiquibaseSchemaMigrator>()
    private val ddlConnection = mock<DbConnection>().apply {
        whenever(privilege).thenReturn(DbPrivilege.DDL)
        whenever(getUser()).thenReturn(ddlUuser)
        whenever(getPassword()).thenReturn(password)
        whenever(config).thenReturn(ddlConfig)
    }
    private val dmlConnection = mock<DbConnection>().apply {
        whenever(privilege).thenReturn(DbPrivilege.DML)
        whenever(getUser()).thenReturn(dmlUuser)
        whenever(getPassword()).thenReturn(password)
    }

    @Test
    fun `create schema and users does nothing for non platform managed DBs`() {
        val target = createVirtualNodeDb(isPlatformManagedDb = false)

        target.createSchemasAndUsers()

        verify(dbAdmin, times(0)).createDbAndUser(any(), any(), any(), any(), any())
    }

    @Test
    fun `create schema and users for platform managed DBs - throws if DDL user is missing`() {
        val target = createVirtualNodeDb(isPlatformManagedDb = true)

        whenever(ddlConnection.getUser()).thenReturn(null)

        assertThrows<DBConfigurationException> { target.createSchemasAndUsers() }
    }

    @Test
    fun `create schema and users for platform managed DBs - throws if DML user is missing`() {
        val target = createVirtualNodeDb(isPlatformManagedDb = true)

        whenever(dmlConnection.getUser()).thenReturn(null)

        assertThrows<DBConfigurationException> { target.createSchemasAndUsers() }
    }

    @Test
    fun `create schema and users for platform managed DBs - throws if DDL password is missing`() {
        val target = createVirtualNodeDb(isPlatformManagedDb = true)

        whenever(ddlConnection.getPassword()).thenReturn(null)

        assertThrows<DBConfigurationException> { target.createSchemasAndUsers() }
    }

    @Test
    fun `create schema and users for platform managed DBs - throws if DML password is missing`() {
        val target = createVirtualNodeDb(isPlatformManagedDb = true)

        whenever(dmlConnection.getPassword()).thenReturn(null)

        assertThrows<DBConfigurationException> { target.createSchemasAndUsers() }
    }

    @Test
    fun `create schema and users for platform managed DBs - delete existing user and schema for DDL connection`() {
        val target = createVirtualNodeDb(isPlatformManagedDb = true)
        whenever(dbAdmin.userExists(ddlUuser)).thenReturn(true)

        target.createSchemasAndUsers()

        verify(dbAdmin).deleteSchema(schema)
        verify(dbAdmin).deleteUser(ddlUuser)
    }

    @Test
    fun `create schema and users for platform managed DBs - delete existing user DML connection`() {
        val target = createVirtualNodeDb(isPlatformManagedDb = true)
        whenever(dbAdmin.userExists(dmlUuser)).thenReturn(true)

        target.createSchemasAndUsers()

        verify(dbAdmin, times(0)).deleteSchema(schema)
        verify(dbAdmin).deleteUser(dmlUuser)
    }

    /**
     * These next two tests look a bit odd because the DBAdmin API requires
     * createDbAndUser to be called twice for once to create the schema and once to
     * create the grants
     */
    @Test
    fun `create schema and users for platform managed DBs - create schema DDL user`() {
        val target = createVirtualNodeDb(isPlatformManagedDb = true)
        whenever(dbAdmin.userExists(ddlUuser)).thenReturn(true)

        target.createSchemasAndUsers()

        verify(dbAdmin).createDbAndUser(schema, ddlUuser, password, DbPrivilege.DDL, null)
    }

    @Test
    fun `create schema and users for platform managed DBs - create schema DML user`() {
        val target = createVirtualNodeDb(isPlatformManagedDb = true)
        whenever(dbAdmin.userExists(dmlUuser)).thenReturn(true)

        target.createSchemasAndUsers()

        verify(dbAdmin).createDbAndUser(schema, dmlUuser, password, DbPrivilege.DML, ddlUuser)
    }

    @Test
    fun `run cpi migrations throws if no DDL connection present`() {
        val target = createVirtualNodeDb(
            isPlatformManagedDb = true,
            dbConnections = mapOf(
                DbPrivilege.DML to dmlConnection,
            )
        )

        assertThrows<VirtualNodeDbException> { target.runCpiMigrations(mock(), "tag") }
    }

    @Test
    fun `run cpi migrations applies changes`() {
        val dbChange = mock<DbChange>()
        val sqlConnection = mock<Connection>()
        val dataSource = mock<CloseableDataSource>().apply {
            whenever(connection).thenReturn(sqlConnection)
        }

        whenever(dbConnectionManager.getDataSource(ddlConfig)).thenReturn(dataSource)

        val target = createVirtualNodeDb(isPlatformManagedDb = true)
        target.runCpiMigrations(dbChange, "tag")
        verify(schemaMigrator).updateDb(sqlConnection, dbChange, tag = "tag")
    }

    @Test
    fun `run DB migration throws if no DDL connection set`() {
        val target = createVirtualNodeDb(
            isPlatformManagedDb = true,
            dbConnections = mapOf(
                DbPrivilege.DML to dmlConnection,
            )
        )

        assertThrows<VirtualNodeDbException> { target.runDbMigration("tag") }
    }

    @Test
    fun `run DB migration applies changes to schema for platform managed DB`() {
        val sqlConnection = mock<Connection>()
        val dataSource = mock<CloseableDataSource>().apply {
            whenever(connection).thenReturn(sqlConnection)
        }

        whenever(dbConnectionManager.getDataSource(ddlConfig)).thenReturn(dataSource)

        val target = createVirtualNodeDb(isPlatformManagedDb = true)
        target.runDbMigration("tag")
        verify(schemaMigrator).updateDb(eq(sqlConnection), any(), eq(schema), eq("tag"))
    }

    @Test
    fun `run DB migration applies changes to default schema for non platform managed DB`() {
        val sqlConnection = mock<Connection>()
        val dataSource = mock<CloseableDataSource>().apply {
            whenever(connection).thenReturn(sqlConnection)
        }

        whenever(dbConnectionManager.getDataSource(ddlConfig)).thenReturn(dataSource)

        val target = createVirtualNodeDb(isPlatformManagedDb = false)
        target.runDbMigration("tag")
        verify(schemaMigrator).updateDb(eq(sqlConnection), any(), tag = eq("tag"))
    }

    private fun createVirtualNodeDb(
        isPlatformManagedDb: Boolean,
        dbConnections: Map<DbPrivilege, DbConnection> = mapOf(
            DbPrivilege.DDL to ddlConnection,
            DbPrivilege.DML to dmlConnection,
        )
    ): VirtualNodeDbImpl {
        return VirtualNodeDbImpl(
            isPlatformManagedDb,
            dbConnections,
            dbType,
            holdingIdShortHash,
            dbAdmin,
            dbConnectionManager,
            schemaMigrator
        )
    }
}