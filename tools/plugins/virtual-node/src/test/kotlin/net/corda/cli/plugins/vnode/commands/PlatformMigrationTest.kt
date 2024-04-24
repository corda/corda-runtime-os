package net.corda.cli.plugins.vnode.commands

import liquibase.Liquibase
import liquibase.command.CommandArgumentDefinition
import liquibase.command.CommandScope
import liquibase.database.Database
import net.corda.db.admin.impl.LiquibaseSchemaUpdaterImpl
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import java.sql.Connection

class PlatformMigrationTest {

    private val mockFileWriter = mock<FileWriter>()

    private val mockLiquibase: Liquibase = mock()
    private val mockLiquibaseFactory = mock<(String, Database) -> Liquibase>().apply {
        whenever(invoke(any(), any())).thenReturn(mockLiquibase)
    }

    private val mockConnection: Connection = mock()
    private val mockConnectionFactory = mock<(String?, String?, String?) -> Connection>().apply {
        whenever(invoke(any(), any(), any())).thenReturn(mockConnection)
    }
    private val mockDatabase: Database = mock()
    private val mockDatabaseFactory = mock<(Connection) -> Database>().apply {
        whenever(invoke(any())).thenReturn(mockDatabase)
    }
    val mockWriterFactory = mock<(String) -> FileWriter>()
    val mockLineReader = mock<(String, (String) -> Unit) -> Unit>()

    private val commandScope = mock<(CommandScope)> { cs ->
        on { addArgumentValue(ArgumentMatchers.anyString(), any()) } doReturn cs
        on { addArgumentValue(any<CommandArgumentDefinition<Any>>(), anyOrNull()) } doReturn cs
        on { execute() } doReturn mock()
    }
    private val commandScopeFactory = mock<(commandNames: Array<String>) -> CommandScope> {
        on { invoke(any()) } doReturn (commandScope)
    }

    private val liquibaseSchemaUpdater = LiquibaseSchemaUpdaterImpl(commandScopeFactory)

    val pmConfig = PlatformMigration.PlatformMigrationConfig( // TODO move the test to SDK
        lineReader = mockLineReader,
        writerFactory = mockWriterFactory,
        liquibaseFactory = mockLiquibaseFactory,
        jdbcConnectionFactory = mockConnectionFactory,
        jdbcDatabaseFactory = mockDatabaseFactory
    )

    private companion object {
        const val NUMBER_OF_SUPPORTED_SCHEMAS = 3

        const val JDBC_URL = "url"
        const val USER = "user"
        const val PASSWORD = "password"

        val validHoldingIds = listOf("30f232111e9a", "25ab40d125a6", "ebf080aaeb79")
    }

//    private fun createPlatformMigration() = PlatformMigration(pmConfig, liquibaseSchemaUpdater).apply {
    private fun createPlatformMigration() = PlatformMigration().apply {
        jdbcUrl = JDBC_URL
        user = USER
        password = PASSWORD
    }

    @Test
    @Disabled
    fun `invalid holding id`() {
        whenever(mockLineReader.invoke(any(), any())).thenAnswer {
            it.getArgument<(String) -> Unit>(1)!!.let { block ->
                block("invalid holding id")
            }
        }

        assertThrows<IllegalArgumentException> { createPlatformMigration().call() }
    }

    @Test
    @Disabled
    fun `default filenames`() {
        whenever(mockWriterFactory.invoke("./vnodes.sql")).thenReturn(mockFileWriter)
        whenever(mockLineReader.invoke(eq("./holdingIds"), any())).thenAnswer {
            it.getArgument<(String) -> Unit>(1)!!.let { block ->
                validHoldingIds.forEach { holdingId -> block(holdingId) }
            }
        }

        createPlatformMigration().call()
        verifyFactoryCalls()
    }

    @Test
    @Disabled
    fun `pass filenames`() {
        val sqlFilename = "my-sql-file"
        val holdingIdFilename = "my-holding-ids-file"

        whenever(mockWriterFactory.invoke(sqlFilename)).thenReturn(mockFileWriter)
        whenever(mockLineReader.invoke(eq(holdingIdFilename), any())).thenAnswer {
            it.getArgument<(String) -> Unit>(1)!!.let { block ->
                validHoldingIds.forEach { holdingId -> block(holdingId) }
            }
        }

        val pm = createPlatformMigration()

        pm.outputFilename = sqlFilename
        pm.holdingIdFilename = holdingIdFilename

        pm.call()
        verifyFactoryCalls()
    }

    private fun verifyFactoryCalls() {
        verify(mockWriterFactory, times(1)).invoke(any())
        verify(mockLineReader, times(1)).invoke(any(), any())

        verify(mockConnectionFactory, times(NUMBER_OF_SUPPORTED_SCHEMAS * validHoldingIds.size)).invoke(
            JDBC_URL,
            USER,
            PASSWORD
        )
        verify(mockDatabaseFactory, times(NUMBER_OF_SUPPORTED_SCHEMAS * validHoldingIds.size)).invoke(mockConnection)

        verify(mockLiquibaseFactory, times(validHoldingIds.size)).invoke(
            eq("net/corda/db/schema/vnode-crypto/db.changelog-master.xml"),
            any()
        )
        verify(mockLiquibaseFactory, times(validHoldingIds.size)).invoke(
            eq("net/corda/db/schema/vnode-uniqueness/db.changelog-master.xml"),
            any()
        )
        verify(mockLiquibaseFactory, times(validHoldingIds.size)).invoke(
            eq("net/corda/db/schema/vnode-vault/db.changelog-master.xml"),
            any()
        )

        verify(commandScope, times(NUMBER_OF_SUPPORTED_SCHEMAS * validHoldingIds.size)).execute()

        verify(mockFileWriter, times(1)).close()
    }
}
