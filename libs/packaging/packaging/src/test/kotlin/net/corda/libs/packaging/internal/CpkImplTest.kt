package net.corda.libs.packaging.internal

import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.hash
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.NavigableSet
import java.util.TreeSet

class CpkImplTest {
    @Test
    fun `CPK identifiers without a signerSummaryHash compares correctly`() {
        val signerSummaryHash = SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32))
        val id1 = CpkIdentifier("a", "1.0", null, hash { it.update("a1.0".toByteArray()) })
        val id2 = CpkIdentifier("a", "1.0", signerSummaryHash, hash { it.update("a1.0".toByteArray()) })
        val id3 = CpkIdentifier("a", "2.0", signerSummaryHash, hash { it.update("a2.0".toByteArray()) })
        var ids : NavigableSet<CpkIdentifier> = Collections.emptyNavigableSet()
        Assertions.assertDoesNotThrow {
             ids = TreeSet<CpkIdentifier>().apply {
                 add(id1)
                 add(id2)
                 add(id3)
            }
        }
        // Check the id with null signerSummaryHash comes first
        Assertions.assertEquals(setOf(id1, id2, id3), ids)
    }
}