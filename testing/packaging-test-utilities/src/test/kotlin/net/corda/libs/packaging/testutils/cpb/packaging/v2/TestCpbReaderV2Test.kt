package net.corda.libs.packaging.testutils.cpb.packaging.v2

import java.nio.file.Path
import net.corda.libs.packaging.testutils.TestUtils.ALICE
import net.corda.libs.packaging.testutils.cpb.TestCpbV2Builder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TestCpbReaderV2Test {

    @Test
    fun `reads cpb file V2 into Cpi V2 but without cpi fields`(@TempDir tmp: Path) {
        val cpbStream = TestCpbV2Builder()
            .signers(ALICE)
            .build()
            .inputStream()


        val cpi = TestCpbReaderV2.readCpi(
            cpbStream,
            tmp,
            ""
        )

        Assertions.assertAll(
            { Assertions.assertEquals("testCpbV2.cpb", cpi.metadata.cpiId.name) },
            { Assertions.assertEquals("1.0.0.0", cpi.metadata.cpiId.version) },
            { Assertions.assertEquals(cpi.metadata.groupPolicy, "{}") },
            { Assertions.assertEquals(2, cpi.cpks.size) },
        )

    }
}