package net.corda.libs.packaging.internal

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.NavigableSet
import java.util.TreeSet

class CpiImplTest {
    @Test
    fun `CPI identifiers without a signerSummaryHash and an identity compares correctly`() {
        val id1 = CpiIdentifier("a", "1.0", null)
        val id2 = CpiIdentifier("a", "1.0",
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32)))
        val id3 = CpiIdentifier(
            "a",
            "1.0",
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32)),
        )
        var ids : NavigableSet<CpiIdentifier> = Collections.emptyNavigableSet()
        Assertions.assertDoesNotThrow {
             ids = TreeSet<CpiIdentifier>().apply {
                 add(id1)
                 add(id2)
                 add(id3)
            }
        }
        // Check the id with null signerSummaryHash and null identity comes first
        Assertions.assertEquals(setOf(id1, id2, id3), ids)
    }
}