package net.corda.ledger.utxo.flow.impl.flows.backchain.v1

import net.corda.v5.base.annotations.CordaSerializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionBackchainRequestV1Test {
    @Test
    fun testSerializable() {
        assertThat(TransactionBackchainRequestV1::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(TransactionBackchainRequestV1.Get::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(TransactionBackchainRequestV1.GetSignedGroupParameters::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(TransactionBackchainRequestV1.Stop::class.java).hasAnnotation(CordaSerializable::class.java)
    }
}
