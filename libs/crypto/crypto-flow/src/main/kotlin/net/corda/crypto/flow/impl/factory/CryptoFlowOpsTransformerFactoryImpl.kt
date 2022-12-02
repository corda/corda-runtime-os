package net.corda.crypto.flow.impl.factory

import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.flow.factory.CryptoFlowOpsTransformerFactory
import net.corda.crypto.flow.impl.CryptoFlowOpsTransformerImpl
import net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.v5.cipher.suite.KeyEncodingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoFlowOpsTransformerFactory::class])
class CryptoFlowOpsTransformerFactoryImpl @Activate constructor(
    @Reference(service = net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService::class)
    private val serializer: net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : CryptoFlowOpsTransformerFactory {

    override fun create(requestingComponent: String, responseTopic: String, requestValidityWindowSeconds: Long): CryptoFlowOpsTransformer {
        return CryptoFlowOpsTransformerImpl(
            serializer,
            requestingComponent,
            responseTopic,
            keyEncodingService,
            requestValidityWindowSeconds
        )
    }
}