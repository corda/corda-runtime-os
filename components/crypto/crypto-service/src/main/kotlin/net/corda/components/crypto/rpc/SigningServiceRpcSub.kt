package net.corda.components.crypto.rpc

import net.corda.components.crypto.CryptoFactory
import net.corda.crypto.impl.config.CryptoLibraryConfig
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoRpcSub::class])
class SigningServiceRpcSub @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = CryptoFactory::class)
    private val cryptoFactory: CryptoFactory
) : AbstractCryptoRpcSub<WireSigningRequest, WireSigningResponse>(), CryptoRpcSub {

    override fun createSubscription(
        libraryConfig: CryptoLibraryConfig
    ): RPCSubscription<WireSigningRequest, WireSigningResponse> {
        val processor = SigningServiceRpcProcessor(cryptoFactory)
        return subscriptionFactory.createRPCSubscription(
            rpcConfig = libraryConfig.rpc.signingRpcConfig,
            responderProcessor = processor
        )
    }
}