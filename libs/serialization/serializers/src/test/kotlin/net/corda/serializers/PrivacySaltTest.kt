package net.corda.serializers

import net.corda.v5.ledger.transactions.PrivacySalt
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

class PrivacySaltTest {
    @Test
	fun `all-zero PrivacySalt not allowed`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            PrivacySalt(ByteArray(32))
        }.withMessage("Privacy salt should not be all zeros.")
    }
}