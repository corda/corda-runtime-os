package net.corda.p2p.gateway.messaging.internal

import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.messaging.api.constants.WorkerRPCPaths.P2P_LINK_MANAGER_PATH
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.publisher.send
import net.corda.schema.configuration.BootConfig.P2P_LINK_MANAGER_WORKER_REST_ENDPOINT
import java.net.URI

internal class LinkManagerRpcClient(
    publisherFactory: PublisherFactory,
    platformInfoProvider: PlatformInfoProvider,
    bootConfig: SmartConfig,
) {
    private val client by lazy {
        publisherFactory.createHttpRpcClient()
    }

    private val endpoint =
        bootConfig.getString(P2P_LINK_MANAGER_WORKER_REST_ENDPOINT)

    private val url by lazy {
        val platformVersion = platformInfoProvider.localWorkerSoftwareShortVersion
        URI.create("http://$endpoint/api/$platformVersion$P2P_LINK_MANAGER_PATH")
    }

    fun send(message: LinkInMessage): LinkManagerResponse? {
        return client.send<LinkManagerResponse>(url, message)
    }
}
