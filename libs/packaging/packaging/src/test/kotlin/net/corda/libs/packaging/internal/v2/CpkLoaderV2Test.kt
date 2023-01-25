import net.corda.libs.packaging.PackagingConstants.CPK_FORMAT_VERSION2_MAINBUNDLE_PLACEHOLDER
import net.corda.libs.packaging.internal.v2.CpkLoaderV2
import net.corda.libs.packaging.testutils.TestUtils
import net.corda.libs.packaging.testutils.cpk.TestCpkV2Builder
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CpkLoaderV2Test {
    @TempDir
    lateinit var tmp: Path

    @Test
    fun `Can load a v2 CPK`() {
        val inMemoryCpk = TestCpkV2Builder()
            .signers(TestUtils.ALICE)
            .build()

        val cpk = CpkLoaderV2().loadCPK(
            inMemoryCpk.toByteArray(),
            tmp,
            "in-memory",
            false,
            "in-memory"
        )

        assertAll(
            { assertEquals("test.cpk", cpk.metadata.cpkId.name) },
            { assertEquals("1.0.0.0", cpk.metadata.cpkId.version) },
            {
                assertEquals(
                    listOf(
                        "META-INF/privatelib/library1.jar",
                        "META-INF/privatelib/library2.jar",
                        "META-INF/privatelib/library3.jar"
                    ), cpk.metadata.libraries
                )
            },
            { assertEquals(CPK_FORMAT_VERSION2_MAINBUNDLE_PLACEHOLDER, cpk.metadata.mainBundle) },
        )
    }
}
