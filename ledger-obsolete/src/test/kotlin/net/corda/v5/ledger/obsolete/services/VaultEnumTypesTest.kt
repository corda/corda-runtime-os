package net.corda.v5.ledger.obsolete.services

import net.corda.v5.ledger.obsolete.services.vault.StateStatus
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class VaultEnumTypesTest {
    @Test
	fun vaultStatusReflectsOrdinalValues() {
        /**
         * Warning!!! Do not change the order of this Enum as ordinal values are stored in the database
         */
        val vaultStateStatusUnconsumed = StateStatus.UNCONSUMED
        Assertions.assertThat(vaultStateStatusUnconsumed.ordinal).isEqualTo(0)
        val vaultStateStatusConsumed = StateStatus.CONSUMED
        Assertions.assertThat(vaultStateStatusConsumed.ordinal).isEqualTo(1)
    }
}