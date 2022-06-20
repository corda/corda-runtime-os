package net.corda.v5.ledger.obsolete

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UniqueIdentifierTests {

    @Test
	fun `unique identifier comparison`() {
        val ids = listOf(UniqueIdentifier.fromString("e363f00e-4759-494d-a7ca-0dc966a92494"),
                UniqueIdentifier.fromString("10ed0cc3-7bdf-4000-b610-595e36667d7d"),
                UniqueIdentifier("Test", UUID.fromString("10ed0cc3-7bdf-4000-b610-595e36667d7d"))
        )
        assertEquals(-1, ids[0].compareTo(ids[1]))
        assertEquals(1, ids[1].compareTo(ids[0]))
        assertEquals(0, ids[0].compareTo(ids[0]))
        // External ID is not taken into account
        assertEquals(0, ids[1].compareTo(ids[2]))
    }

    @Test
	fun `unique identifier equality`() {
        val ids = listOf(UniqueIdentifier.fromString("e363f00e-4759-494d-a7ca-0dc966a92494"),
                UniqueIdentifier.fromString("10ed0cc3-7bdf-4000-b610-595e36667d7d"),
                UniqueIdentifier("Test", UUID.fromString("10ed0cc3-7bdf-4000-b610-595e36667d7d"))
        )
        assertEquals(ids[0], ids[0])
        assertNotEquals(ids[0], ids[1])
        assertEquals(ids[0].hashCode(), ids[0].hashCode())
        assertNotEquals(ids[0].hashCode(), ids[1].hashCode())
        // External ID is not taken into account
        assertEquals(ids[1], ids[2])
        assertEquals(ids[1].hashCode(), ids[2].hashCode())
    }
}