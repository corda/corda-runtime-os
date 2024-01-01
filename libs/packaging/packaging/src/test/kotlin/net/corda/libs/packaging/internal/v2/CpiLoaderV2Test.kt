package net.corda.libs.packaging.internal.v2

import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.testutils.TestUtils.ALICE
import net.corda.libs.packaging.testutils.cpb.TestCpbV2Builder
import net.corda.libs.packaging.testutils.cpi.TestCpiV2Builder
import net.corda.libs.packaging.testutils.cpk.TestCpkV2Builder
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CpiLoaderV2Test {

    @TempDir
    lateinit var tmp: Path

    @Test
    fun `Can load a v2 CPI`() {
        val inMemoryCpi = TestCpiV2Builder()
            .signers(ALICE)
            .build()

        val cpi = CpiLoaderV2(50200).loadCpi(inMemoryCpi.toByteArray(), tmp, "in-memory", false)

        assertAll(
            { assertEquals("testCpiV2.cpi", cpi.metadata.cpiId.name) },
            { assertEquals("1.0.0.0", cpi.metadata.cpiId.version) },
            { assertEquals("{\"groupId\":\"test\"}", cpi.metadata.groupPolicy) },
            { assertEquals(2, cpi.cpks.size) },
        )
    }

    @Test
    fun `CPI loading fails with duplicate CPKs`() {
        val cpkCordappName = "com.test.duplicate"

        val inMemoryCpk1 = TestCpkV2Builder()
            .name("test1.jar")
            .bundleName(cpkCordappName)

        val inMemoryCpk2 = TestCpkV2Builder()
            .name("test2.jar")
            .bundleName(cpkCordappName)

        val inMemoryCpb = TestCpbV2Builder()
            .cpks(inMemoryCpk1, inMemoryCpk2)

        val inMemoryCpi = TestCpiV2Builder()
            .signers(ALICE)
            .cpb(inMemoryCpb)
            .build()

        assertThrows<PackagingException> {
            CpiLoaderV2(50200).loadCpi(inMemoryCpi.toByteArray(), tmp, "in-memory", false)
        }
    }
    @Test
    fun `CPI loading fails with platform version below minimum platform version`() {
        val inMemoryCpi = TestCpiV2Builder()
            .signers(ALICE)
            .build()

        assertThrows<PackagingException> {
            CpiLoaderV2(0).loadCpi(inMemoryCpi.toByteArray(), tmp, "in-memory", false)
        }
    }
}
