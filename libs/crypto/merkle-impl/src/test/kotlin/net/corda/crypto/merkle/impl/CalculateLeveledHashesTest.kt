package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.lang.IllegalArgumentException

class CalculateLeveledHashesTest {
    @Test
    fun `leaf indices outside tree are rejected`() {
        val leaf1 = mock <IndexedMerkleLeaf> {
            on { index } doReturn 42
        }
        val m = mock<MerkleProof> {
            on { leaves } doReturn listOf(leaf1)
            on { treeSize } doReturn  1
        }
        val e = assertThrows(IllegalArgumentException::class.java) { calculateLeveledHashes(m, mock()) }
        assertThat(e).hasMessageContaining("cannot point outside of the original tree")
    }

    @Test
    fun `proofs with no leaves are rejected`() {
        val m = mock<MerkleProof> {
            on { leaves } doReturn emptyList()
            on { treeSize } doReturn  1
        }
        assertThrows(IllegalArgumentException::class.java) { calculateLeveledHashes(m, mock()) }
    }

    @Test
    fun `proofs with duplicate leaf indices are rejected`() {
        val leaf1 = mock <IndexedMerkleLeaf> {
            on { index } doReturn 2
        }
        val m = mock<MerkleProof> {
            on { leaves } doReturn listOf(leaf1, leaf1)
            on { treeSize } doReturn 4
        }
        val e = assertThrows(IllegalArgumentException::class.java) { calculateLeveledHashes(m, mock()) }
        assertThat(e).hasMessageContaining("cannot have duplications")
    }
}