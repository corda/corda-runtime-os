package net.corda.messaging.emulation.publisher.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.emulation.publisher.CordaPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CordaPublisherFactoryTest {
    private val messagingConfig = mock<SmartConfig> {
        on { withFallback(any()) } doReturn this.mock
        on { withValue(any(), any()) } doReturn this.mock
    }
    private val factory = CordaPublisherFactory(mock(), mock(), mock())

    @Test
    fun `createPublisher returns CordaPublisher`() {
        val publisher = factory.createPublisher(PublisherConfig("client"), messagingConfig)

        assertThat(publisher).isInstanceOf(CordaPublisher::class.java)
    }

    @Test
    fun `createRPCSender returns an instance of the RPCSender`() {
        val sender = factory.createRPCSender(RPCConfig("g1", "c1", "t1", String::class.java, String::class.java), messagingConfig)
        assertThat(sender).isInstanceOf(RPCSender::class.java)
    }
}
