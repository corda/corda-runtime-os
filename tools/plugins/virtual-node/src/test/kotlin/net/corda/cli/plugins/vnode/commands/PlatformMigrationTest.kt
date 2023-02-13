package net.corda.cli.plugins.vnode.commands

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
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

    private companion object {
        const val NUMBER_OF_SUPPORTED_SCHEMAS = 3

        const val JDBC_URL = "url"
        const val USER = "user"
        const val PASSWORD = "password"
    }

    @Test
    fun `invalid holding id`() {
        val pmConfig = PlatformMigration.PlatformMigrationConfig(lineReader = { _, block ->
            block("invalid holding id")
        })

        assertThrows<IllegalArgumentException> { PlatformMigration(pmConfig) }
    }

    @Test
    fun `default filenames`() {
        val writerFactory = { filename: String ->
            assertEquals("./vnodes.sql", filename)
            mockFileWriter
        }


        val pmConfig = PlatformMigration.PlatformMigrationConfig(
            lineReader = { filename, block ->
                assertEquals("./holdingIds", filename)
                block("30f232111e9a")
                block("25ab40d125a6")
                block("ebf080aaeb79")
            },
            writerFactory = writerFactory,
            liquibaseFactory = mockLiquibaseFactory,
            jdbcConnectionFactory = mockConnectionFactory,
            jdbcDatabaseFactory = mockDatabaseFactory
        )

        val pm = PlatformMigration(pmConfig)

        pm.jdbcUrl = JDBC_URL
        pm.user = USER
        pm.password = PASSWORD

        pm.run()

        verify(mockConnectionFactory, times(NUMBER_OF_SUPPORTED_SCHEMAS)).invoke(JDBC_URL, USER, PASSWORD)
        verify(mockDatabaseFactory, times(NUMBER_OF_SUPPORTED_SCHEMAS)).invoke(mockConnection)

        verify(mockLiquibaseFactory, times(1)).invoke("net/corda/db/schema/vnode-crypto/db.changelog-master.xml", any())
        verify(mockLiquibaseFactory, times(1)).invoke("net/corda/db/schema/vnode-uniqueness/db.changelog-master.xml", any())
        verify(mockLiquibaseFactory, times(1)).invoke("net/corda/db/schema/vnode-vault/db.changelog-master.xml", any())

        verify(mockLiquibase, times(NUMBER_OF_SUPPORTED_SCHEMAS)).update(any<Contexts>(), mockFileWriter)

        verify(mockFileWriter, times(1)).close()
    }

    @Test
    fun generate() {
        val pm = PlatformMigration()

        pm.jdbcUrl = "uri"
        pm.user = "user"

//        val pmConfig = PlatformMigration.PlatformMigrationConfig(lineReader = { filename, block ->
//            assertEquals(filename, "./vnodes.sql")
//            block("holdingId1")
//            block("holdingId2")
//            block("holdingId3")
//        })

        pm.run()

    }
}
