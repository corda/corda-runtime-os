package net.corda.cli.plugins.dbconfig

import liquibase.Liquibase
import liquibase.command.CommandArgumentDefinition
import liquibase.command.CommandScope
import liquibase.database.Database
import net.corda.db.admin.impl.LiquibaseSchemaUpdaterImpl
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.FileWriter
import java.nio.file.Path
import java.sql.Connection

class SpecTest {
    private val mockLiquibase: Liquibase = mock()
    private val mockWriter: FileWriter = mock()
    private val mockWriterFactory = mock<(String) -> FileWriter>().apply {
        whenever(invoke(any())).thenReturn(mockWriter)
    }
    private val mockLiquibaseFactory = mock<(String, Database) -> Liquibase>().apply {
        whenever(invoke(any(), any())).thenReturn(mockLiquibase)
    }
    val mockDeleteFile: (Path) -> Unit = mock()
    private val mockConnection: Connection = mock()
    private val mockConnectionFactory = mock<(String?, String?, String?) -> Connection>().apply {
        whenever(invoke(any(), any(), any())).thenReturn(mockConnection)
    }
    private val mockDatabase: Database = mock()
    private val mockDatabaseFactory = mock<(Connection) -> Database>().apply {
        whenever(invoke(any())).thenReturn(mockDatabase)
    }

    private val commandScope = mock<(CommandScope)> { cs ->
        on { addArgumentValue(ArgumentMatchers.anyString(), any()) } doReturn cs
        on { addArgumentValue(any<CommandArgumentDefinition<Any>>(), anyOrNull()) } doReturn cs
        on { execute() } doReturn mock()
    }
    private val commandScopeFactory = mock<(commandNames: Array<String>) -> CommandScope> {
        on { invoke(any()) } doReturn (commandScope)
    }
    private val liquibaseSchemaUpdater = LiquibaseSchemaUpdaterImpl(commandScopeFactory)

    private val specConfig = Spec.SpecConfig(
        writerFactory = mockWriterFactory,
        liquibaseFactory = mockLiquibaseFactory,
        deleteFile = mockDeleteFile,
        jdbcConnectionFactory = mockConnectionFactory,
        jdbcDatabaseFactory = mockDatabaseFactory
    )

    private companion object {
        const val NUMBER_OF_DEFAULT_SCHEMAS = 3

        const val JDBC_URL = "url"
        const val USER = "user"
        const val PASSWORD = "password"

        const val DEFAULT_PATH = "./databasechangelog.csv"
        const val CUSTOM_PATH = "path"
    }

    @Test
    fun `Verify we run offline update and write the result to disk where no filter is specified`() {
        val spec = Spec(specConfig, liquibaseSchemaUpdater)

        spec.run()

        verify(mockConnectionFactory, times(0)).invoke(any(), any(), any())
        verify(mockDatabaseFactory, times(0)).invoke(any())

        verify(commandScope, times(NUMBER_OF_DEFAULT_SCHEMAS)).execute()
        verify(mockWriter, times(NUMBER_OF_DEFAULT_SCHEMAS)).close()
    }

    @Test
    fun `Verify we run offline update and write the result to disk only once with a filter`() {
        val spec = Spec(specConfig, liquibaseSchemaUpdater)

        spec.schemasToGenerate = listOf("messagebus")

        spec.run()

        verify(mockConnectionFactory, times(0)).invoke(any(), any(), any())
        verify(mockDatabaseFactory, times(0)).invoke(any())

        verify(commandScope, times(1)).execute()
        verify(mockWriter, times(1)).close()
    }

    @Test
    fun `Verify we delete the changelog file if clear is specified`() {
        val spec = Spec(specConfig, liquibaseSchemaUpdater)

        spec.clearChangeLog = true

        spec.run()

        verify(mockDeleteFile, times(1)).invoke(Path.of(DEFAULT_PATH))
    }

    @Test
    fun `Verify we delete the changelog file at a custom location if clear is specified`() {
        val spec = Spec(specConfig, liquibaseSchemaUpdater)

        spec.clearChangeLog = true
        spec.databaseChangeLogFile = Path.of(CUSTOM_PATH)

        spec.run()

        verify(mockDeleteFile, times(1)).invoke(Path.of(CUSTOM_PATH))
    }

    @Test
    fun `Verify specifying jdbc url attempts to connect to a live database`() {
        val spec = Spec(specConfig, liquibaseSchemaUpdater)

        spec.jdbcUrl = JDBC_URL
        spec.user = USER
        spec.password = PASSWORD

        spec.run()

        verify(mockConnectionFactory, times(NUMBER_OF_DEFAULT_SCHEMAS)).invoke(JDBC_URL, USER, PASSWORD)
        verify(mockDatabaseFactory, times(NUMBER_OF_DEFAULT_SCHEMAS)).invoke(mockConnection)
        verify(mockLiquibaseFactory, times(NUMBER_OF_DEFAULT_SCHEMAS)).invoke(any(), eq(mockDatabase))

        verify(commandScope, times(NUMBER_OF_DEFAULT_SCHEMAS)).execute()
        verify(mockWriter, times(NUMBER_OF_DEFAULT_SCHEMAS)).close()
    }

    @Test
    fun `Verify specifying statemanager schema will generate only statemanager sql`() {
        val spec = Spec(specConfig, liquibaseSchemaUpdater)

        spec.jdbcUrl = JDBC_URL
        spec.user = USER
        spec.password = PASSWORD
        spec.schemasToGenerate = listOf("statemanager")
        spec.generateSchemaSql = listOf("statemanager:STATE_MANAGER_SCHEMA")

        spec.run()

        verify(mockConnectionFactory, times(1)).invoke(JDBC_URL, USER, PASSWORD)
        verify(mockDatabaseFactory, times(1)).invoke(mockConnection)
        verify(mockLiquibaseFactory, times(1)).invoke(any(), eq(mockDatabase))

        verify(commandScope, times(1)).execute()
        verify(mockWriter, times(1)).close()
    }
}
