package net.corda.p2p.gateway.messaging.internal

import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.messaging.api.constants.WorkerRPCPaths.P2P_LINK_MANAGER_PATH
import net.corda.messaging.api.publisher.HttpRpcClient
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.BootConfig.P2P_LINK_MANAGER_WORKER_REST_ENDPOINT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URI

class LinkManagerRpcClientTest {
    private val mockClient = mock<HttpRpcClient>()
    private val publisherFactory = mock<PublisherFactory> {
        on { createHttpRpcClient() } doReturn mockClient
    }
    private val platformInfoProvider = mock<PlatformInfoProvider> {
        on { localWorkerSoftwareShortVersion } doReturn "5.x"
    }
    private val bootConfig = mock<SmartConfig> {
        on { getString(P2P_LINK_MANAGER_WORKER_REST_ENDPOINT) } doReturn "link-manager:2001"
    }
    private val client = LinkManagerRpcClient(
        publisherFactory,
        platformInfoProvider,
        bootConfig,
    )

    @Test
    fun `send sends the message to the correct URL`() {
        val uri = argumentCaptor<URI>()
        whenever(
            mockClient.send(
                uri.capture(),
                any(),
                eq(LinkManagerResponse::class.java),
            ),
        ).doReturn(null)

        client.send(mock())

        assertThat(uri.firstValue.toString())
            .isEqualTo("http://link-manager:2001/api/5.x$P2P_LINK_MANAGER_PATH")
    }

    @Test
    fun `send sends the correct object`() {
        val sent = argumentCaptor<LinkInMessage>()
        whenever(
            mockClient.send(
                any(),
                sent.capture(),
                eq(LinkManagerResponse::class.java),
            ),
        ).doReturn(null)
        val message = LinkInMessage()

        client.send(message)

        assertThat(sent.firstValue)
            .isSameAs(message)
    }

    @Test
    fun `send return the correct response the correct object`() {
        val message = LinkManagerResponse()
        whenever(
            mockClient.send(
                any(),
                any(),
                eq(LinkManagerResponse::class.java),
            ),
        ).doReturn(message)

        assertThat(client.send(mock())).isSameAs(message)
    }
}
