package net.corda.flow.application.services.impl

import net.corda.flow.application.services.impl.interop.dispatch.buildDispatcher
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.impl.interop.facade.FacadeRequestImpl
import net.corda.flow.application.services.impl.interop.facade.FacadeResponseImpl
import net.corda.flow.application.services.impl.interop.proxies.JacksonJsonMarshallerAdaptor
import net.corda.flow.application.services.impl.interop.proxies.getClientProxy
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InterOpIdentityInfo
import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import net.corda.v5.application.interop.facade.FacadeId

@Component(service = [FacadeService::class, UsedByFlow::class], scope = PROTOTYPE)
class FacadeServiceImpl @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = FlowMessaging::class)
    private val flowMessaging: FlowMessaging,
) : FacadeService, UsedByFlow, SingletonSerializeAsToken {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun <T : Any?> getProxy(facadeId: FacadeId, expectedType: Class<T>?, interOpIdentity: InterOpIdentityInfo?): T {
        logger.info("Creating Proxy for: facadeId=$facadeId," +
                " expectedType=$expectedType, interOpIdentity=${interOpIdentity?.applicationName}, " +
                "interopGroup=${interOpIdentity?.groupId}") //TODO lower level to debug
        requireNotNull(expectedType) { "Required value for expectedType was null." }
        requireNotNull(interOpIdentity) { "Required value for interOpIdentity was null." }
        val facade = facadeLookup(facadeId)
        val x500Name = MemberX500Name.parse(interOpIdentity.x500Name)
        val groupId = interOpIdentity.groupId
        val marshaller = JacksonJsonMarshallerAdaptor(jsonMarshallingService)
        val transportLayer = MessagingDispatcher(flowMessaging, jsonMarshallingService, x500Name , groupId)
        return facade.getClientProxy(marshaller, expectedType, transportLayer)
    }

    @Suspendable
    override fun dispatchFacadeRequest(target: Any?, request: String?): String {
        logger.info("Dispatching: target=$target, request=$request") //TODO lower level to debug
        require(target != null)
        require(request != null)
        val facadeRequest = jsonMarshallingService.parse(request, FacadeRequestImpl::class.java)
        val facade = facadeLookup(facadeRequest.facadeId)
        val marshaller = JacksonJsonMarshallerAdaptor(jsonMarshallingService)
        val dispatcher = target.buildDispatcher(facade, marshaller) //TODO return dispatcher which can be reused
        val facadeResponse = dispatcher.invoke(facadeRequest)
        return jsonMarshallingService.format(facadeResponse)
    }

    @Suspendable
    private fun facadeLookup(facadeId: FacadeId): Facade = hardcodedFacadesSpec[facadeId]
        ?: throw IllegalArgumentException("Facade $facadeId not found. Available facades: ${hardcodedFacadesSpec.keys}.")

    private val hardcodedFacadesSpec: Map<FacadeId, Facade> by lazy {
        mapOf(
            FacadeId("org.corda.interop", listOf("platform", "tokens"), "v1.0") to "/tokens-v1.0.json",
            FacadeId("org.corda.interop", listOf("platform", "tokens"), "v2.0") to "/tokens-v2.0.json",
            FacadeId("org.corda.interop", listOf("platform", "tokens"), "v3.0") to "/tokens-v3.0.json",
            FacadeId("org.corda.interop", listOf("platform", "lock"), "v1.0") to "/locking-facade.json"
        ).mapValues { (_, value) -> this::class.java.getResource(value)?.readText().toString().trimIndent() }
            .mapValues { (_, value) ->
                try {
                    @Suppress("deprecation", "removal")
                    java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                        FacadeReaders.JSON.read(value)
                    })
                } catch (e: PrivilegedActionException) {
                    throw e.exception
                }
            }
    }
}

private class MessagingDispatcher(private var flowMessaging: FlowMessaging, private val jsonMarshallingService: JsonMarshallingService,
    private val alias: MemberX500Name, private val aliasGroupId: String) : (FacadeRequest) -> FacadeResponse {
    override fun invoke(request: FacadeRequest): FacadeResponse {
        val payload = jsonMarshallingService.format(request)
        val response = flowMessaging.callFacade(alias, aliasGroupId, request.facadeId.toString(), request.methodName, payload)
        return jsonMarshallingService.parse(response, FacadeResponseImpl::class.java)
    }
}
