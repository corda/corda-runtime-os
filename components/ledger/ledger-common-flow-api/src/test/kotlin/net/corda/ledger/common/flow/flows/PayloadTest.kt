package net.corda.ledger.common.flow.flows

import net.corda.v5.base.annotations.CordaSerializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PayloadTest {
    @Test
    fun testSerializable() {
        assertThat(Payload::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(Payload.Success::class.java).hasAnnotation(CordaSerializable::class.java)
        assertThat(Payload.Failure::class.java).hasAnnotation(CordaSerializable::class.java)
    }
}
