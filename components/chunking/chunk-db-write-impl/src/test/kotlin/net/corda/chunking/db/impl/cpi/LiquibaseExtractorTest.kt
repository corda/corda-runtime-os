package net.corda.chunking.db.impl.cpi

import net.corda.chunking.db.impl.cpi.liquibase.LiquibaseExtractor
import net.corda.db.admin.LiquibaseXmlConstants
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.CpiReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LiquibaseExtractorTest {
    companion object {
        const val EXTENDABLE_CPB = "/META-INF/extendable-cpb.cpb"
        private fun getInputStream(resourceName: String): InputStream {
            return this::class.java.getResource(resourceName)?.openStream()
                ?: throw FileNotFoundException("No such resource: '$resourceName'")
        }
    }

    private lateinit var testDir: Path

    @BeforeAll
    fun setup(@TempDir tempDir: Path) {
        testDir = tempDir
    }

    @Test
    fun `test real cpb via validation function`() {
        val cpi: Cpi = getInputStream(EXTENDABLE_CPB).use { CpiReader.readCpi(it, testDir) }
        val obj = LiquibaseExtractor()
        assertThat(obj.extractLiquibaseEntitiesFromCpi(cpi).isNotEmpty()).isTrue

        val expectedLiquibaseFileCount = 5
        assertThat(obj.extractLiquibaseEntitiesFromCpi(cpi).size).isEqualTo(expectedLiquibaseFileCount)
    }

    @Test
    fun `check content`() {
        val cpi: Cpi = getInputStream(EXTENDABLE_CPB).use { CpiReader.readCpi(it, testDir) }
        val obj = LiquibaseExtractor()
        val entities = obj.extractLiquibaseEntitiesFromCpi(cpi)
        assertThat(entities.isNotEmpty()).isTrue

        val expectedLiquibaseFileCount = 5
        assertThat(entities.size).isEqualTo(expectedLiquibaseFileCount)

        entities.forEach {
            assertThat(it.id.cpkSignerSummaryHash.isNotEmpty()).isTrue
            assertThat(it.id.cpkName.isNotEmpty()).isTrue
            assertThat(it.id.cpkVersion.isNotEmpty()).isTrue
            assertThat(it.id.filePath.isNotEmpty()).isTrue
            assertThat(it.content.isNotEmpty()).isTrue
            assertThat(it.fileChecksum.isNotEmpty()).isTrue

            //  Cursory check of XML -
            assertThat(it.content).contains("<?xml")
            assertThat(it.content).contains(LiquibaseXmlConstants.DB_CHANGE_LOG_ROOT_ELEMENT)
            assertThat(it.content).contains("xmlns")
        }
    }
}
