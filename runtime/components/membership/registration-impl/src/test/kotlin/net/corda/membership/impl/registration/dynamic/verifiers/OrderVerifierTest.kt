package net.corda.membership.impl.registration.dynamic.verifiers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OrderVerifierTest {
    private val orderVerifier = OrderVerifier()

    @Test
    fun `isOrdered return true if the keys are in order`() {
        val keys = (0..10).map {
            "key.name.$it.more"
        }

        assertThat(orderVerifier.isOrdered(keys, 2)).isTrue
    }

    @Test
    fun `isOrdered return false if the keys are in not order`() {
        val keys = listOf(
            "key.name.0.more",
            "key.name.1.more",
            "key.name.4.more",
            "key.name.5.more",
        )

        assertThat(orderVerifier.isOrdered(keys, 2)).isFalse
    }
}
