package net.corda.components.crypto.rpc

import net.corda.components.crypto.CryptoFactory
import net.corda.components.crypto.CryptoServiceLifecycleEventHandler
import net.corda.components.crypto.config.CryptoLibraryConfig
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoRpcSub::class])
class FreshKeysServiceRpcSub @Activate constructor(
    @Reference(service = CryptoServiceLifecycleEventHandler::class)
    private val cryptoServiceLifecycleEventHandler: CryptoServiceLifecycleEventHandler,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = CryptoFactory::class)
    private val cryptoFactory: CryptoFactory
) : CryptoRpcSubBase<WireFreshKeysRequest, WireFreshKeysResponse>(cryptoServiceLifecycleEventHandler), CryptoRpcSub {

    override fun createSubscription(
        libraryConfig: CryptoLibraryConfig
    ): RPCSubscription<WireFreshKeysRequest, WireFreshKeysResponse> {
        val processor = FreshKeysServiceRpcProcessor(cryptoFactory)
        return subscriptionFactory.createRPCSubscription(
            RPCConfig(
                groupName = libraryConfig.rpc.groupName,
                clientName = libraryConfig.rpc.clientName,
                requestTopic = libraryConfig.rpc.signingRequestTopic,
                requestType = WireFreshKeysRequest::class.java,
                responseType = WireFreshKeysResponse::class.java
            ),
            responderProcessor = processor
        )
    }
}