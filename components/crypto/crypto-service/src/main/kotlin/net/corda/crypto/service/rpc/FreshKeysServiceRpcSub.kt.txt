package net.corda.crypto.service.rpc

import net.corda.crypto.component.config.rpc
import net.corda.crypto.service.CryptoFactory
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoRpcSub::class])
class FreshKeysServiceRpcSub @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = CryptoFactory::class)
    private val cryptoFactory: CryptoFactory
) : AbstractCryptoRpcSub<WireFreshKeysRequest, WireFreshKeysResponse>(), CryptoRpcSub {

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