package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.utxo.flow.impl.flows.backchain.base.TransactionBackchainRequestBase
import net.corda.v5.base.annotations.CordaSerializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionBackchainRequestBaseTest {
    @Test
    fun testSerializable() {
        assertThat(TransactionBackchainRequestBase::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(TransactionBackchainRequestBase.Get::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(TransactionBackchainRequestBase.GetSignedGroupParameters::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(TransactionBackchainRequestBase.Stop::class.java).hasAnnotation(CordaSerializable::class.java)
    }
}