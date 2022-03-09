package net.corda.membership.service.impl

import net.corda.data.KeyValuePairList
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.request.RegistrationRequest
import net.corda.data.membership.rpc.request.RegistrationStatusRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.data.membership.rpc.response.RegistrationResponse
import net.corda.data.membership.rpc.response.RegistrationStatus
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.membership.registration.provider.RegistrationProvider
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.Logger
import java.lang.reflect.Constructor
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class MemberOpsServiceProcessor(
    private val registrationProvider: RegistrationProvider,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService
) : RPCResponderProcessor<MembershipRpcRequest, MembershipRpcResponse> {

    interface RpcHandler<REQUEST> {
        fun handle(context: MembershipRpcRequestContext, request: REQUEST): Any
    }

    companion object {
        private val logger: Logger = contextLogger()

        private val handlers = mapOf<Class<*>, Class<out RpcHandler<out Any>>>(
            RegistrationRequest::class.java to RegistrationRequestHandler::class.java,
            RegistrationStatusRequest::class.java to RegistrationStatusRequestHandler::class.java
        )

        private val constructors = ConcurrentHashMap<Class<*>, Constructor<*>>()

        /**
         * Temporarily hardcoded to 1.
         */
        private const val REGISTRATION_PROTOCOL_VERSION = 1
    }

    override fun onNext(request: MembershipRpcRequest, respFuture: CompletableFuture<MembershipRpcResponse>) {
        try {
            logger.info(
                "Handling {} for request ID {}",
                request.request::class.java.name,
                request.requestContext.requestId
            )
            val handler = getHandler(request)
            val response = handler.handle(request.requestContext, request.request)
            val result = MembershipRpcResponse(createResponseContext(request), response)
            logger.debug(
                "Handled {} for request ID {} with {}",
                request.request::class.java.name,
                request.requestContext.requestId,
                if (result.response != null) result.response::class.java.name else "null"
            )
            respFuture.complete(result)
        } catch (e: Throwable) {
            val message =
                "Failed to handle ${request.request::class.java} for request ID ${request.requestContext.requestId}"
            logger.error(message, e)
            respFuture.completeExceptionally(MembershipRegistrationException(message, e))
        }
    }

    private fun createResponseContext(request: MembershipRpcRequest) = MembershipRpcResponseContext(
        request.requestContext.requestId,
        request.requestContext.requestTimestamp,
        Instant.now()
    )

    @Suppress("UNCHECKED_CAST")
    private fun getHandler(request: MembershipRpcRequest): RpcHandler<Any> {
        val type = handlers[request.request::class.java] ?: throw IllegalArgumentException(
            "Unknown request type ${request.request::class.java.name}"
        )
        return when (type) {
            RegistrationRequestHandler::class.java -> {
                constructors.computeIfAbsent(request.request::class.java) {
                    type.constructors.first { it.parameterCount == 2 }
                }.newInstance(registrationProvider, virtualNodeInfoReadService) as RpcHandler<Any>
            }
            else -> {
                constructors.computeIfAbsent(request.request::class.java) {
                    type.constructors.first { it.parameterCount == 0 }
                }.newInstance() as RpcHandler<Any>
            }
        }
    }

    private class RegistrationRequestHandler(
        private val registrationProvider: RegistrationProvider,
        private val virtualNodeInfoReadService: VirtualNodeInfoReadService
    ) : RpcHandler<RegistrationRequest> {
        override fun handle(context: MembershipRpcRequestContext, request: RegistrationRequest): Any {
            val holdingIdentity = virtualNodeInfoReadService.getById(request.holdingIdentityId)?.holdingIdentity
                ?: throw MembershipRegistrationException("Could not find holding identity associated with ${request.holdingIdentityId}")
            val result = registrationProvider.get(holdingIdentity)?.register(holdingIdentity)
            val registrationStatus = result?.outcome?.let {
                RegistrationStatus.valueOf(it.toString())
            } ?: RegistrationStatus.NOT_SUBMITTED
            return RegistrationResponse(
                context.requestTimestamp,
                registrationStatus,
                REGISTRATION_PROTOCOL_VERSION,
                KeyValuePairList(emptyList()),
                KeyValuePairList(emptyList())
            )
        }
    }

    private class RegistrationStatusRequestHandler : RpcHandler<RegistrationStatusRequest> {
        override fun handle(context: MembershipRpcRequestContext, request: RegistrationStatusRequest): Any {
            TODO("Not yet implemented")
        }
    }
}
