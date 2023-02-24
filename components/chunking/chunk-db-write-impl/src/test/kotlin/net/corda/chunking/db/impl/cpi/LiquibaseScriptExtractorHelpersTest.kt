package net.corda.chunking.db.impl.cpi

import net.corda.chunking.db.impl.cpi.liquibase.LiquibaseScriptExtractor
import net.corda.chunking.db.impl.cpi.liquibase.LiquibaseScriptExtractorHelpers
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.testutils.cpb.packaging.v2.TestCpbReaderV2
import net.corda.test.util.InMemoryZipFile
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LiquibaseScriptExtractorHelpersTest {
    companion object {
        const val EXTENDABLE_CPB = "/META-INF/extendable-cpb.cpb"
        private fun getInputStream(resourceName: String): InputStream {
            return this::class.java.getResource(resourceName)?.openStream()
                ?: throw FileNotFoundException("No such resource: '$resourceName'")
        }

        @Suppress("MaxLineLength")
        private val liquibase = """
        <?xml version="1.1" encoding="UTF-8" standalone="no"?>
        <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.5.xsd">
            <include file="migration/dbms-example-migration-v1.0.xml"/>
        </databaseChangeLog>""".trimIndent()

        private val notLiquibase = """
                <?xml version="1.1" encoding="UTF-8" standalone="no"?>
                <hello></hello>""".trimIndent()

        private const val notXml = "this is not xml"
    }

    private lateinit var testDir: Path

    @BeforeAll
    fun setup(@TempDir tempDir: Path) {
        testDir = tempDir
    }

    private fun jarWithLiquibase() = InMemoryZipFile().also {
        it.addEntry("migration/anything.xml", liquibase.toByteArray())
    }

    private fun jarWithoutLiquibase() = InMemoryZipFile().also {
        it.addEntry("migration/anything.xml", notLiquibase.toByteArray())
    }

    private fun jarWithBrokenLiquibase() = InMemoryZipFile().also {
        it.addEntry("migration/anything.xml", notXml.toByteArray())
    }

    private fun jarWithOtherXmlResource() = InMemoryZipFile().also {
        it.addEntry("some/other/migration/anything.xml", notXml.toByteArray())
    }

    private fun cpkWithNestedJar() = InMemoryZipFile().also {
        it.addEntry("foo.jar", jarWithLiquibase().toByteArray())
    }

    private fun mockCpk(name: String, version: String, signerHash: SecureHash, fileChecksum: SecureHash): Cpk {
        val cpkId = CpkIdentifier(name, version, signerHash)
        val mockMetadata = mock<CpkMetadata>().also {
            whenever(it.cpkId).thenReturn(cpkId)
            whenever(it.fileChecksum).thenReturn(fileChecksum)
        }
        return mock<Cpk>().also { whenever(it.metadata).thenReturn(mockMetadata) }
    }

    @Test
    fun `liquibase extractor can persist liquibase script`() {
        val cpk = mockCpk(
            "Test",
            "1.0",
            SecureHash.parse("ALGO:1234567890"),
            SecureHash.parse("ALGO:0987654321")
        )

        val obj = LiquibaseScriptExtractorHelpers()
        val entities = jarWithLiquibase().inputStream().use { obj.readLiquibaseScripts(cpk, it) }

        assertThat(entities.size).isEqualTo(1)
    }

    @Test
    fun `liquibase extractor can persist liquibase script in nested jar`() {
        val cpk = mockCpk(
            "Test",
            "1.0",
            SecureHash.parse("ALGO:1234567890"),
            SecureHash.parse("ALGO:0987654321")
        )

        val obj = LiquibaseScriptExtractorHelpers()
        val entities = cpkWithNestedJar().inputStream().use { obj.readLiquibaseScripts(cpk, it) }

        assertThat(entities.size).isEqualTo(1)
    }

    @Test
    fun `liquibase extractor does not persist non liquibase script`() {
        val cpk = mockCpk(
            "Test",
            "1.1",
            SecureHash.parse("ALGO:1234567890"),
            SecureHash.parse("ALGO:0987654321")
        )

        val obj = LiquibaseScriptExtractorHelpers()
        val entities = jarWithoutLiquibase().inputStream().use { obj.readLiquibaseScripts(cpk, it) }

        assertThat(entities.size).isEqualTo(0)
    }

    @Test
    fun `liquibase extractor does not persist broken XML`() {
        val cpk = mockCpk(
            "Test",
            "1.2",
            SecureHash.parse("ALGO:1234567890"),
            SecureHash.parse("ALGO:0987654321")
        )

        val obj = LiquibaseScriptExtractorHelpers()
        val entities = jarWithBrokenLiquibase().inputStream().use { obj.readLiquibaseScripts(cpk, it) }

        assertThat(entities.size).isEqualTo(0)
    }

    @Test
    fun `liquibase extractor does not persist XML in other migration folder`() {
        val cpk = mockCpk(
            "Test",
            "1.2",
            SecureHash.parse("ALGO:1234567890"),
            SecureHash.parse("ALGO:0987654321")
        )


        val obj = LiquibaseScriptExtractorHelpers()
        val entities = jarWithOtherXmlResource().inputStream().use { obj.readLiquibaseScripts(cpk, it) }

        assertThat(entities.size).isEqualTo(0)
    }

    @Test
    fun `liquibase XML accepted`() {
        assertThat(LiquibaseScriptExtractorHelpers().isLiquibaseXml(liquibase)).isTrue
    }

    @Test
    fun `liquibase XML rejected`() {
        assertThat(LiquibaseScriptExtractorHelpers().isLiquibaseXml(notLiquibase)).isFalse
    }

    @Test
    fun `not XML rejected`() {
        assertThat(LiquibaseScriptExtractorHelpers().isLiquibaseXml(notXml)).isFalse
    }

    @Test
    fun `empty string that is not not XML rejected`() {
        assertThat(LiquibaseScriptExtractorHelpers().isLiquibaseXml("")).isFalse
    }

    @Test
    fun `test real cpb`() {
        val cpk = mockCpk(
            "Test",
            "1.2",
            SecureHash.parse("ALGO:1234567890"),
            SecureHash.parse("ALGO:0987654321")
        )

        val obj = LiquibaseScriptExtractorHelpers()

        // Note:  INCORRECT USAGE on purpose (we just want some CPKs to test)
        // We're testing a **CPI**, not a *CPK**, so we're persisting
        // the scripts using the "mock cpk" as the db key.

        val entities = getInputStream(EXTENDABLE_CPB).use { obj.readLiquibaseScripts(cpk, it) }

        // "extendable-cpb" contains cats.cpk (3) and dogs.cpk (2) liquibase files.
        val expectedLiquibaseFileCount = 5

        assertThat(entities.size).isEqualTo(expectedLiquibaseFileCount)
    }

    @Test
    fun `test real cpb and parse it`() {
        val obj = LiquibaseScriptExtractorHelpers()
        val cpi: Cpi = getInputStream(EXTENDABLE_CPB).use { TestCpbReaderV2.readCpi(it, testDir) }

        val entities = mutableListOf<CpkDbChangeLog>()
        cpi.cpks.forEach { cpk ->
            Files.newInputStream(cpk.path!!).use {
                entities += obj.readLiquibaseScripts(cpk, it)
            }
        }

        // "extendable-cpb" contains cats.cpk (3) and dogs.cpk (2) liquibase files.
        val expectedLiquibaseFileCount = 5
        assertThat(entities.size).isEqualTo(expectedLiquibaseFileCount)
    }

    @Test
    fun `test real cpb via validation function`() {
        val cpi: Cpi = getInputStream(EXTENDABLE_CPB).use { TestCpbReaderV2.readCpi(it, testDir) }

        val obj = LiquibaseScriptExtractor()
        assertThat(obj.extract(cpi).isNotEmpty()).isTrue

        val expectedLiquibaseFileCount = 5
        assertThat(obj.extract(cpi).size).isEqualTo(expectedLiquibaseFileCount)
    }
}
