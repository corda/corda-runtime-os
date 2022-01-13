package net.corda.crypto.service.rpc.ops

import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.rpc.AbstractCryptoRpcSub
import net.corda.crypto.service.rpc.CryptoRpcSub
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoRpcSub::class])
class CryptoOpsRpcSub @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = SigningServiceFactory::class)
    private val signingFactory: SigningServiceFactory
) : AbstractCryptoRpcSub<RpcOpsRequest, RpcOpsResponse>(), CryptoRpcSub {
    private companion object {
        const val GROUP_NAME = "crypto.ops.rpc"
        const val CLIENT_NAME = "crypto.ops.rpc"
    }

    override fun createSubscription(
        libraryConfig: CryptoLibraryConfig
    ): RPCSubscription<RpcOpsRequest, RpcOpsResponse> {
        logger.info("Creating RPC subscription for '{}' topic", Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC)
        val processor = CryptoOpsRpcProcessor(signingFactory)
        return subscriptionFactory.createRPCSubscription(
            rpcConfig = RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_NAME,
                requestTopic = Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                requestType = RpcOpsRequest::class.java,
                responseType = RpcOpsResponse::class.java
            ),
            responderProcessor = processor
        )
    }
}