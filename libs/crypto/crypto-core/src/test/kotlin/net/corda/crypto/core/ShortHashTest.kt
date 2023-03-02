package net.corda.crypto.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ShortHashTest {
    @Test
    fun `can create short hash`() {
        assertDoesNotThrow {
            ShortHash.of("123456789012")
        }
    }

    @Test
    fun `can create short hash from hex string in lower case`() {
        assertDoesNotThrow {
            ShortHash.of("1234567890ab")
        }
    }

    @Test
    fun `can create short hash from hex string in caps`() {
        assertDoesNotThrow {
            ShortHash.of("1234567890AB")
        }
    }

    @Test
    fun `cannot create short hash if too short`() {
        // 11 chars < 12 REQUIRED
        assertThrows<ShortHashException> {
            ShortHash.of("12345678901")
        }
    }

    @Test
    fun `cannot create short hash if too long`() {
        // 11 chars < 12 REQUIRED
        assertThrows<ShortHashException> {
            ShortHash.of("1234567890123")
        }
    }

    @Test
    fun `cannot create short hash if not a hex string`() {
        // 11 chars < 12 REQUIRED
        assertThrows<ShortHashException> {
            ShortHash.of("fishfishfishfish")
        }
    }

    @Test
    fun `cannot create short hash if not a hex string after char 12`() {
        // 11 chars < 12 REQUIRED
        assertThrows<ShortHashException> {
            ShortHash.of("123456789012xyz")
        }
    }

    @Test
    fun `can parse short hash`() {
        assertDoesNotThrow {
            ShortHash.parse("123456789012")
        }
    }

    @Test
    fun `can parse short hash from hex string in lower case`() {
        assertDoesNotThrow {
            ShortHash.parse("567890abcdef")
        }
    }

    @Test
    fun `can parse short hash from hex string in caps`() {
        assertDoesNotThrow {
            ShortHash.parse("567890ABCDEF")
        }
    }

    @Test
    fun `cannot parse short hash if too short`() {
        // 11 chars < 12 REQUIRED
        assertThrows<ShortHashException> {
            ShortHash.parse("12345678901")
        }
    }

    @Test
    fun `cannot parse short hash if too long`() {
        // 14 chars > 12 REQUIRED
        assertThrows<ShortHashException> {
            ShortHash.parse("12345678901d22")
        }
    }

    @Test
    fun `cannot parse short hash if not a hex string`() {
        assertThrows<ShortHashException> {
            ShortHash.parse("finishfinish")
        }
    }

    @Test
    fun `comparison`() {
        assertThat(ShortHash.of("1234567890ab")).isEqualTo(ShortHash.of("1234567890ab"))
        assertThat(ShortHash.of("1234567890ab")).isNotEqualTo(ShortHash.of("ab1234567890"))
    }

    @Test
    fun `hex strings are uppercase`() {
        assertThat(ShortHash.of("abcdefabcdef").value).isEqualTo("ABCDEFABCDEF")
        assertThat(ShortHash.of("ABCDEFABCDEF").value).isEqualTo("ABCDEFABCDEF")
    }
}
