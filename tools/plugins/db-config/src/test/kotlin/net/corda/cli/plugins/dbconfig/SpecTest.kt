package net.corda.cli.plugins.dbconfig

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.core.PostgresDatabase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.FileWriter
import java.nio.file.Path

class SpecTest {
    private val mockPath = mock<Path>()
    private val mockLiquibase: Liquibase = mock()
    private val mockWriter: FileWriter = mock()
    private val mockWriterFactory: (String) -> FileWriter = { _ -> mockWriter }
    private val mockLiquibaseFactory: (String, PostgresDatabase) -> Liquibase = { _, _ -> mockLiquibase }

    @Test
    fun `Verify we run update and write the result to disk where no filter is specified`() {
        val spec = Spec(mockPath, mockWriterFactory, mockLiquibaseFactory)

        spec.run()

        verify(mockLiquibase, times(3)).update(any<Contexts>(), any<FileWriter>())
        verify(mockWriter, times(3)).flush()
        verify(mockWriter, times(3)).close()
    }

    @Test
    fun `Verify we run update and write the result to disk only once with a filter`() {
        val spec = Spec(mockPath, mockWriterFactory, mockLiquibaseFactory)

        spec.schemasToGenerate = listOf("config")

        spec.run()

        verify(mockLiquibase, times(1)).update(any<Contexts>(), any<FileWriter>())
        verify(mockWriter, times(1)).flush()
        verify(mockWriter, times(1)).close()
    }

    @Test
    fun `Verify we delete the changelog file if clear is specified`() {
        val mockDeleteFile: (Path) -> Unit = mock()
        val spec = Spec(mockPath, mockWriterFactory, mockLiquibaseFactory, mockDeleteFile)

        spec.clearChangeLog = true

        spec.run()

        verify(mockDeleteFile, times(1)).invoke(mockPath)
    }
}
