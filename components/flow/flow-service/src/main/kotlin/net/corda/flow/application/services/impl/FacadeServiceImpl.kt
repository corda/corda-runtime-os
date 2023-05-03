package net.corda.flow.application.services.impl

import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.services.impl.interop.dispatch.buildDispatcher
import net.corda.flow.application.services.impl.interop.facade.FacadeRequestImpl
import net.corda.flow.application.services.impl.interop.facade.FacadeResponseImpl
import net.corda.flow.application.services.impl.interop.parameters.RawParameterType
import net.corda.flow.application.services.impl.interop.parameters.TypedParameterImpl
import net.corda.flow.application.services.impl.interop.parameters.TypedParameterValueImpl
import net.corda.flow.application.services.impl.interop.proxies.JacksonJsonMarshallerAdaptor
import net.corda.flow.application.services.impl.interop.proxies.getClientProxy
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.interop.facade.FacadeService
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name

@Component(service = [FacadeService::class, UsedByFlow::class], scope = PROTOTYPE)
class FacadeServiceImpl @Activate constructor(
    @Reference(service = SerializationServiceInternal::class)
    private val serializationService: SerializationServiceInternal,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = FlowMessaging::class)
    private val  flowMessaging: FlowMessaging
) : FacadeService, UsedByFlow, SingletonSerializeAsToken {

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getClientProxy(
        facade: Facade?,
        expectedType: Class<T>?,
        alias: MemberX500Name?,
        interopGroup: String?
    ): T {
        val x = JacksonJsonMarshallerAdaptor(jsonMarshallingService)
        val m = MessagingDispatcher(flowMessaging, jsonMarshallingService, alias!!, interopGroup!!)
        val client = facade!!.getClientProxy(x, expectedType!!, m)
        return client
    }

    override fun dispatch(facade: Facade?, target: Any?, request: String?): FacadeResponse {
        val x = JacksonJsonMarshallerAdaptor(jsonMarshallingService)
        val dispatcher = target!!.buildDispatcher(facade!!, x)

         val facadeRequest  = jsonMarshallingService.parse(request!!, FacadeRequestImpl::class.java)

//        val facadeRequest = FacadeRequest(
//            FacadeId("", mutableListOf("com", "r3", "tokens", "sample").joinToString("/"), "v1.0"),
//            "hello", listOf(TypedParameterValue(TypedParameter("greeting", ParameterType.StringType), request)))

        val facadeResponse = dispatcher.invoke(facadeRequest)
        return facadeResponse

    }
}


class MessagingDispatcher(private var flowMessaging: FlowMessaging, private val jsonMarshallingService: JsonMarshallingService,
                          private val alias: MemberX500Name, val aliasGroupId: String)
    : (FacadeRequest) -> FacadeResponse {
    override fun invoke(p1: FacadeRequest): FacadeResponse {
        val facade = p1.facadeId
        val method = p1.methodName
        //val payload = p1.inParameters.toString()
        //val om = ObjectMapper()
        //om.registerSubtypes(FacadeRequest::class.java)
        //val payload : String = om.writeValueAsString(p1)
        val payload  = jsonMarshallingService.format(p1)
        val response = flowMessaging.callFacade(alias, facade.toString(), method, payload)
        return response

       // FacadeResponseImpl(p1.facadeId, method,
       //     listOf(TypedParameterValueImpl(TypedParameterImpl("greeting", RawParameterType(ParameterTypeLabel.STRING)), response)))

    }

}