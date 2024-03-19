package net.corda.db.admin.impl

import liquibase.Liquibase
import liquibase.changelog.DatabaseChangeLog
import liquibase.command.CommandArgumentDefinition
import liquibase.command.CommandScope
import liquibase.database.Database
import liquibase.database.DatabaseConnection
import liquibase.resource.ResourceAccessor
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.Writer
import java.sql.Connection

class LiquibaseSchemaMigratorTest {
    private val connection = mock<Connection>()
    private val dbChange = mock<DbChange>()
    private val mockDatabaseChangeLog = mock<DatabaseChangeLog>()
    private val lb = mock<Liquibase> {
        on { databaseChangeLog } doReturn (mockDatabaseChangeLog)
    }
    private val lbFactory = mock<(String, ResourceAccessor, Database) -> Liquibase> {
        on { invoke(any(), any(), any()) } doReturn (lb)
    }
    private val dbConnection = mock<DatabaseConnection>()
    private val db = mock<Database> {
        on { connection } doReturn (dbConnection)
    }
    private val dbFactory = mock<(connection: Connection) -> Database> {
        on { invoke(any()) } doReturn (db)
    }
    private val dbFactoryOffline = mock<(url: String, resourceAccessor: ResourceAccessor) -> Database> {
        on { invoke(any(), any()) } doReturn db
    }
    private val commandScope = mock<(CommandScope)> { cs ->
        on { addArgumentValue(ArgumentMatchers.anyString(), any()) } doReturn cs
        on { addArgumentValue(any<CommandArgumentDefinition<Any>>(), anyOrNull()) } doReturn cs
        on { execute() } doReturn mock()
    }
    private val commandScopeFactory = mock<(commandNames: Array<String>) -> CommandScope> {
        on { invoke(any()) } doReturn (commandScope)
    }
    private val liquibaseManager = LiquibaseManager(commandScopeFactory)
    private val writer = mock<Writer>()

    private val migrator: LiquibaseSchemaMigrator =
        LiquibaseSchemaMigratorImpl(lbFactory, dbFactory, dbFactoryOffline, liquibaseManager)

    @Test
    fun `when updateDb create LB object`() {
        migrator.updateDb(connection, dbChange)
        verify(lbFactory).invoke(
            check {
                assertThat(it).startsWith("master-changelog")
            },
            check {
                assertThat(it).isInstanceOf(StreamResourceAccessor::class.java)
            },
            eq(db)
        )
    }

    @Test
    fun `when updateDb call Liquibase API`() {
        migrator.updateDb(connection, dbChange)
        verify(commandScope).execute()
    }

    @Test
    fun `when createUpdateSql create LB object`() {
        migrator.createUpdateSql(connection, dbChange, writer)
        verify(lbFactory).invoke(
            check {
                assertThat(it).startsWith("master-changelog")
            },
            check {
                assertThat(it).isInstanceOf(StreamResourceAccessor::class.java)
            },
            eq(db)
        )
    }

    @Test
    fun `when createUpdateSql call Liquibase API`() {
        migrator.createUpdateSql(connection, dbChange, writer)
        verify(commandScope).execute()
    }

    @Test
    fun `when listUnrunChangeSets call liquibase API` (){
        migrator.listUnrunChangeSets(connection, dbChange)
        verify(dbFactory).invoke(connection)
    }
}
