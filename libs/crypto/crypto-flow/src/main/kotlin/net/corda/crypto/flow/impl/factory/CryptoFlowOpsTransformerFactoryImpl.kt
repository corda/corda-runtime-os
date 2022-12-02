package net.corda.crypto.flow.impl.factory

import net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.flow.factory.CryptoFlowOpsTransformerFactory
import net.corda.crypto.flow.impl.CryptoFlowOpsTransformerImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoFlowOpsTransformerFactory::class])
class CryptoFlowOpsTransformerFactoryImpl @Activate constructor(
    @Reference(service = AlgorithmParameterSpecEncodingService::class)
    private val serializer: AlgorithmParameterSpecEncodingService,
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