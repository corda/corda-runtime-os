package net.corda.chunking.db.impl.cpi

import net.corda.chunking.db.impl.cpi.liquibase.LiquibaseScriptExtractor
import net.corda.db.admin.LiquibaseXmlConstants
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.testutils.cpb.packaging.v2.TestCpbReaderV2
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
        val cpi: Cpi = getInputStream(EXTENDABLE_CPB).use { TestCpbReaderV2.readCpi(it, testDir) }
        val obj = LiquibaseScriptExtractor()
        assertThat(obj.extract(cpi).isNotEmpty()).isTrue

        val expectedLiquibaseFileCount = 5
        assertThat(obj.extract(cpi).size).isEqualTo(expectedLiquibaseFileCount)
    }

    @Test
    fun `check content`() {
        val cpi: Cpi = getInputStream(EXTENDABLE_CPB).use { TestCpbReaderV2.readCpi(it, testDir) }
        val obj = LiquibaseScriptExtractor()
        val entities = obj.extract(cpi)
        assertThat(entities.isNotEmpty()).isTrue

        val expectedLiquibaseFileCount = 5
        assertThat(entities.size).isEqualTo(expectedLiquibaseFileCount)

        entities.forEach {
            assertThat(it.id.cpkFileChecksum.isNotEmpty()).isTrue
            assertThat(it.id.filePath.isNotEmpty()).isTrue
            assertThat(it.content.isNotEmpty()).isTrue

            //  Cursory check of XML -
            assertThat(it.content).contains("<?xml")
            assertThat(it.content).contains(LiquibaseXmlConstants.DB_CHANGE_LOG_ROOT_ELEMENT)
            assertThat(it.content).contains("xmlns")
        }
    }
}
