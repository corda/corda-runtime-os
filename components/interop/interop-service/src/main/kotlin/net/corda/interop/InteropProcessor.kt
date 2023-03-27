package net.corda.interop

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.interop.InteropMessage
import net.corda.interop.service.InteropFacadeToFlowMapperService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory

//Based on FlowP2PFilter
@Suppress("LongParameterList")
class InteropProcessor(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val config: SmartConfig,
    private val facadeToFlowMapperService: InteropFacadeToFlowMapperService
) : DurableProcessor<String, FlowMapperEvent> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
    }

    private val cordaAvroDeserializer: CordaAvroDeserializer<InteropMessage> =
        cordaAvroSerializationFactory.createAvroDeserializer({}, InteropMessage::class.java)
    private val cordaAvroSerializer: CordaAvroSerializer<InteropMessage> = cordaAvroSerializationFactory.createAvroSerializer {}

    override fun onNext(
        events: List<Record<String, FlowMapperEvent>>
    ): List<Record<*, *>> = events.mapNotNull { (_, _, _) ->
          null
    }

    override val keyClass = String::class.java
    override val valueClass = FlowMapperEvent::class.java

    private fun getRealHoldingIdentityFromAliasMapping(fakeHoldingIdentity: HoldingIdentity): String? {
        val groupReader = membershipGroupReaderProvider.getGroupReader(fakeHoldingIdentity)
        val memberInfo = groupReader.lookup(fakeHoldingIdentity.x500Name)
        return memberInfo?.memberProvidedContext?.get(MemberInfoExtension.INTEROP_ALIAS_MAPPING)
    }
}
