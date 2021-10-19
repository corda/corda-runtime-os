package net.corda.p2p.gateway

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class GatewayFactoryImplTest {
    @Test
    fun `factory creates gateway implementation`() {
        val factory = GatewayFactoryImpl(
            mock(),
            mock(),
            mock(),
            mock(),
        )

        val gateway = factory.createGateway(mock())

        assertThat(gateway).isInstanceOf(GatewayImpl::class.java)
    }
}
