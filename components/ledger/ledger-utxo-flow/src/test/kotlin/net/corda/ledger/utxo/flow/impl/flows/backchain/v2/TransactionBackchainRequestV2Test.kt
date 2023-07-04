package net.corda.ledger.utxo.flow.impl.flows.backchain.v2

import net.corda.v5.base.annotations.CordaSerializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionBackchainRequestV2Test {
    @Test
    fun testSerializable() {
        assertThat(TransactionBackchainRequestV2::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(TransactionBackchainRequestV2.Get::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(TransactionBackchainRequestV2.GetSignedGroupParameters::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(TransactionBackchainRequestV2.Stop::class.java).hasAnnotation(CordaSerializable::class.java)
    }
}
