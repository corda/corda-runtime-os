package net.corda.testing.sandboxes.impl.packaging.v2

import java.nio.file.Path
import net.corda.libs.packaging.testutils.TestUtils.ALICE
import net.corda.libs.packaging.testutils.cpb.TestCpbV2Builder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CpbReaderV2Test {

    @TempDir
    lateinit var tmp: Path

    @Test
    fun `reads Cpb V2 into Cpi V2 but without Cpi fields`() {
        val cpbStream = TestCpbV2Builder()
            .signers(ALICE)
            .build()
            .inputStream()


        val cpi = CpbReaderV2.readCpi(
            cpbStream,
            tmp,
            "",
            false
        )

        Assertions.assertAll(
            { Assertions.assertEquals("testCpbV2.cpb", cpi.metadata.cpiId.name) },
            { Assertions.assertEquals("1.0.0.0", cpi.metadata.cpiId.version) },
            { Assertions.assertNull(cpi.metadata.groupPolicy) },
            { Assertions.assertEquals(2, cpi.cpks.size) },
        )

    }
}