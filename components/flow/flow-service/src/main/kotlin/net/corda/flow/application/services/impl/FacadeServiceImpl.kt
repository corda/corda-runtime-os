package net.corda.flow.application.services.impl

import net.corda.flow.application.services.impl.interop.dispatch.buildDispatcher
import net.corda.flow.application.services.impl.interop.facade.FacadeRequestImpl
import net.corda.flow.application.services.impl.interop.facade.FacadeResponseImpl
import net.corda.flow.application.services.impl.interop.proxies.JacksonJsonMarshallerAdaptor
import net.corda.flow.application.services.impl.interop.proxies.getClientProxy
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.interop.facade.FacadeService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory

@Component(service = [FacadeService::class, UsedByFlow::class], scope = PROTOTYPE)
class FacadeServiceImpl @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = FlowMessaging::class)
    private val flowMessaging: FlowMessaging
) : FacadeService, UsedByFlow, SingletonSerializeAsToken {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun <T : Any?> getClientProxy(facade: Facade?, expectedType: Class<T>?, alias: MemberX500Name?, interopGroup: String?): T {
        logger.info("$facade, $expectedType, $alias, $interopGroup")
        val marshaller = JacksonJsonMarshallerAdaptor(jsonMarshallingService)
        val transportLayer = MessagingDispatcher(flowMessaging, jsonMarshallingService, alias!!, interopGroup!!)
        val client = facade!!.getClientProxy(marshaller, expectedType!!, transportLayer)
        logger.info("$facade, $expectedType, $alias, $interopGroup -> $client")
        return client
    }

    override fun dispatch(facade: Facade?, target: Any?, request: String?): String {
        logger.info("$facade, $target, $request")
        val marshaller = JacksonJsonMarshallerAdaptor(jsonMarshallingService)
        val dispatcher = target!!.buildDispatcher(facade!!, marshaller)
        val facadeRequest = jsonMarshallingService.parse(request!!, FacadeRequestImpl::class.java)
        val facadeResponse = dispatcher.invoke(facadeRequest)
        logger.info("$facade, $target, $request -> $facadeResponse")
        return jsonMarshallingService.format(facadeResponse)
    }
}

class MessagingDispatcher(private var flowMessaging: FlowMessaging, private val jsonMarshallingService: JsonMarshallingService,
    private val alias: MemberX500Name, val aliasGroupId: String) : (FacadeRequest) -> FacadeResponse {
    override fun invoke(request: FacadeRequest): FacadeResponse {
        val payload = jsonMarshallingService.format(request)
        val response = flowMessaging.callFacade(alias, request.facadeId.toString(), request.methodName, payload)
        return jsonMarshallingService.parse(response, FacadeResponseImpl::class.java)
    }
}
