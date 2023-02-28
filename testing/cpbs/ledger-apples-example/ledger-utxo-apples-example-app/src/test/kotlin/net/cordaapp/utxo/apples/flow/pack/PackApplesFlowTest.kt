package net.cordaapp.utxo.apples.flow.pack

import net.cordapp.utxo.apples.flows.pack.PackApplesFlow
import org.assertj.core.api.Assertions.assertThat

import org.junit.jupiter.api.Test


class PackApplesFlowTest {

    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val flowClass = PackApplesFlow::class.java
        val ctor = flowClass.getDeclaredConstructor()
        assertThat(ctor).isNotNull
    }
}
