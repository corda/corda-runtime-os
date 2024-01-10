package net.corda.cli.plugins.vnode.commands

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
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

    val pmConfig = PlatformMigration.PlatformMigrationConfig(
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

    private fun createPlatformMigration() = PlatformMigration(pmConfig).apply {
        jdbcUrl = JDBC_URL
        user = USER
        password = PASSWORD
    }

    @Test
    fun `invalid holding id`() {
        whenever(mockLineReader.invoke(any(), any())).thenAnswer {
            it.getArgument<(String) -> Unit>(1)!!.let { block ->
                block("invalid holding id")
            }
        }

        assertThrows<IllegalArgumentException> { createPlatformMigration().run() }
    }

    @Test
    fun `default filenames`() {
        whenever(mockWriterFactory.invoke("./vnodes.sql")).thenReturn(mockFileWriter)
        whenever(mockLineReader.invoke(eq("./holdingIds"), any())).thenAnswer {
            it.getArgument<(String) -> Unit>(1)!!.let { block ->
                validHoldingIds.forEach { holdingId -> block(holdingId) }
            }
        }

        createPlatformMigration().run()
        verifyFactoryCalls()
    }

    @Test
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

        pm.run()
        verifyFactoryCalls()
    }


    private fun verifyFactoryCalls() {
        verify(mockWriterFactory, times(1)).invoke(any())
        verify(mockLineReader, times(1)).invoke(any(), any())

        verify(mockConnectionFactory, times(NUMBER_OF_SUPPORTED_SCHEMAS * validHoldingIds.size)).invoke(
            JDBC_URL, USER, PASSWORD
        )
        verify(mockDatabaseFactory, times(NUMBER_OF_SUPPORTED_SCHEMAS * validHoldingIds.size)).invoke(mockConnection)

        verify(mockLiquibaseFactory, times(validHoldingIds.size)).invoke(
            eq("net/corda/db/schema/vnode-crypto/db.changelog-master.xml"), any()
        )
        verify(mockLiquibaseFactory, times(validHoldingIds.size)).invoke(
            eq("net/corda/db/schema/vnode-uniqueness/db.changelog-master.xml"), any()
        )
        verify(mockLiquibaseFactory, times(validHoldingIds.size)).invoke(
            eq("net/corda/db/schema/vnode-vault/db.changelog-master.xml"), any()
        )

        verify(mockLiquibase, times(NUMBER_OF_SUPPORTED_SCHEMAS * validHoldingIds.size)).update(
            any<Contexts>(), eq(mockFileWriter)
        )

        verify(mockFileWriter, times(1)).close()
    }
}
