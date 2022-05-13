package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpk
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
        val id1 = Cpk.Identifier.newInstance("a", "1.0", null)
        val id2 = Cpk.Identifier.newInstance("a", "1.0", SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32)))
        val id3 = Cpk.Identifier.newInstance("a", "2.0", SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32)))
        var ids : NavigableSet<Cpk.Identifier> = Collections.emptyNavigableSet()
        Assertions.assertDoesNotThrow {
             ids = TreeSet<Cpk.Identifier>().apply {
                 add(id1)
                 add(id2)
                 add(id3)
            }
        }
        // Check the id with null signerSummaryHash comes first
        Assertions.assertEquals(setOf(id1, id2, id3), ids)
    }
}