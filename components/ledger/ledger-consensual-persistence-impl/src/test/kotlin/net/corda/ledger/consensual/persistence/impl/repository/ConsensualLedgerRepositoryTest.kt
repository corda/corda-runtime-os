package net.corda.ledger.consensual.persistence.impl.repository

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.sql.Timestamp

class ConsensualLedgerRepositoryTest {
    companion object {
        private const val id = "id1"
        private val privacySalt = ByteArray(32)
        private const val accountId = "accountId"
        private val createdTs = Timestamp(0)
    }

    @Test
    fun `throws if leaf component is missing`() {
        val groupIdx = 1
        val leafIdx = 0
        val rows = listOf<Any?>(
            arrayOf(id, privacySalt, accountId, createdTs, groupIdx, leafIdx, ByteArray(16), "hash"),
            arrayOf(id, privacySalt, accountId, createdTs, groupIdx, leafIdx + 2, ByteArray(16), "hash")
        )
        val exception = assertThrows<IllegalStateException> {
            ConsensualLedgerRepository(mock(), mock(), mock()).queryRowsToComponentGroupLists(rows)
        }
        assertEquals("Missing data for transaction with ID: id1, groupIdx: 1, leafIdx: 1", exception.message)
    }

    @Test
    fun `creates valid component group lists`() {
        val leafIdx = 0
        val groupIdx = 0
        val rows = listOf<Any?>(
            arrayOf(id, privacySalt, accountId, createdTs, groupIdx, leafIdx, "data00".toByteArray(), "hash00"),
            arrayOf(id, privacySalt, accountId, createdTs, groupIdx, leafIdx + 1, "data01".toByteArray(), "hash01"),
            arrayOf(id, privacySalt, accountId, createdTs, groupIdx + 2, leafIdx, "data20".toByteArray(), "hash20"),
            arrayOf(id, privacySalt, accountId, createdTs, groupIdx + 2, leafIdx + 1, "data21".toByteArray(), "hash21"),
            arrayOf(id, privacySalt, accountId, createdTs, groupIdx + 2, leafIdx + 2, "data22".toByteArray(), "hash22"),
        )

        val componentGroupLists = ConsensualLedgerRepository(mock(), mock(), mock()).queryRowsToComponentGroupLists(rows)

        val expectedLists = listOf(
            listOf("data00", "data01").toBytes(),
            emptyList(),
            listOf("data20", "data21", "data22").toBytes()
        )
        assertNotNull(componentGroupLists)
        assertEquals(expectedLists.size, componentGroupLists.size)
        for (i in expectedLists.indices) {
            assertEquals(expectedLists[i].size, componentGroupLists[i].size)
            for (j in expectedLists[i].indices) {
                assertArrayEquals(expectedLists[i][j], componentGroupLists[i][j])
            }
        }
    }

    private fun List<String>.toBytes() = this.map { it.toByteArray() }
}