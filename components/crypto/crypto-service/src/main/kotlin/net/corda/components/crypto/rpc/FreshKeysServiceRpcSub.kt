package net.corda.components.crypto.rpc

import net.corda.components.crypto.CryptoFactory
import net.corda.crypto.impl.lifecycle.CryptoServiceLifecycleEventHandler
import net.corda.crypto.impl.config.CryptoLibraryConfig
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
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
            rpcConfig = libraryConfig.rpc.freshKeysRpcConfig,
            responderProcessor = processor
        )
    }
}