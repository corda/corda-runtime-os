package net.corda.crypto.manager.impl

import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.flow.factory.CryptoFlowOpsTransformerFactory
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoFlowOpsTransformer::class])
class CryptoFlowOpsTransformerService @Activate constructor(
    @Reference(service = CryptoFlowOpsTransformerFactory::class)
    cryptoFlowOpsTransformerFactory: CryptoFlowOpsTransformerFactory,
) : CryptoFlowOpsTransformer by cryptoFlowOpsTransformerFactory.create(
    requestingComponent = "Flow worker",
    responseTopic = Schemas.Flow.FLOW_EVENT_TOPIC,
    requestValidityWindowSeconds = 300
)