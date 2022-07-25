import net.corda.libs.packaging.internal.v2.CpiLoaderV2
import net.corda.libs.packaging.testutils.TestUtils.ALICE
import net.corda.libs.packaging.testutils.cpi.TestCpiV2Builder
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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

        val cpi = CpiLoaderV2().loadCpi(inMemoryCpi.inputStream(), tmp, "in-memory", false)

        assertAll(
            { assertEquals("testCpiV2.cpi", cpi.metadata.cpiId.name) },
            { assertEquals("1.0.0.0", cpi.metadata.cpiId.version) },
            { assertEquals("{\"groupId\":\"test\"}", cpi.metadata.groupPolicy) },
            { assertEquals(2, cpi.cpks.size) },
        )
    }
}

