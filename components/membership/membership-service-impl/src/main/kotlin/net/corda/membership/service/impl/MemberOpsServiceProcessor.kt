package net.corda.membership.service.impl

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.rpc.request.*
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.data.membership.rpc.response.RegistrationRpcResponse
import net.corda.data.membership.rpc.response.RegistrationRpcStatus
import net.corda.membership.lib.exceptions.RegistrationProtocolSelectionException
import net.corda.membership.lib.toMap
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.membership.registration.RegistrationProxy
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.Logger
import java.lang.reflect.Constructor
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import net.corda.data.membership.rpc.response.MGMGroupPolicyResponse
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.sessionKeyHash
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.registration.GroupPolicyGenerationException

class MemberOpsServiceProcessor(
    private val registrationProxy: RegistrationProxy,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) : RPCResponderProcessor<MembershipRpcRequest, MembershipRpcResponse> {

    interface RpcHandler<REQUEST> {
        fun handle(context: MembershipRpcRequestContext, request: REQUEST): Any
    }

    companion object {
        private val logger: Logger = contextLogger()

        private val handlers = mapOf<Class<*>, Class<out RpcHandler<out Any>>>(
            RegistrationRpcRequest::class.java to RegistrationRequestHandler::class.java,
            RegistrationStatusRpcRequest::class.java to RegistrationStatusRequestHandler::class.java,
            MGMGroupPolicyRequest::class.java to MGMGroupPolicyRequestHandler::class.java
        )

        private val constructors = ConcurrentHashMap<Class<*>, Constructor<*>>()

        /**
         * Temporarily hardcoded to 1.
         */
        private const val REGISTRATION_PROTOCOL_VERSION = 1

        private val clock = UTCClock()

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
        clock.instant()
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
                }.newInstance(registrationProxy, virtualNodeInfoReadService) as RpcHandler<Any>
            }
            MGMGroupPolicyRequestHandler::class.java -> {
                constructors.computeIfAbsent(request.request::class.java) {
                    type.constructors.first { it.parameterCount == 2 }
                }.newInstance(virtualNodeInfoReadService, membershipGroupReaderProvider) as RpcHandler<Any>
            }
            else -> {
                constructors.computeIfAbsent(request.request::class.java) {
                    type.constructors.first { it.parameterCount == 0 }
                }.newInstance() as RpcHandler<Any>
            }
        }
    }

    private class RegistrationRequestHandler(
        private val registrationProxy: RegistrationProxy,
        private val virtualNodeInfoReadService: VirtualNodeInfoReadService
    ) : RpcHandler<RegistrationRpcRequest> {
        override fun handle(context: MembershipRpcRequestContext, request: RegistrationRpcRequest): Any {
            val holdingIdentity = virtualNodeInfoReadService.getById(request.holdingIdentityId)?.holdingIdentity
                ?: throw MembershipRegistrationException("Could not find holding identity associated with ${request.holdingIdentityId}")
            val result = try {
                registrationProxy.register(holdingIdentity, request.context.toMap())
            } catch (e: RegistrationProtocolSelectionException) {
                logger.warn("Could not select registration protocol.")
                null
            }
            val registrationStatus = result?.outcome?.let {
                RegistrationRpcStatus.valueOf(it.toString())
            } ?: RegistrationRpcStatus.NOT_SUBMITTED
            return RegistrationRpcResponse(
                context.requestTimestamp,
                registrationStatus,
                REGISTRATION_PROTOCOL_VERSION,
                KeyValuePairList(emptyList()),
                KeyValuePairList(emptyList())
            )
        }
    }

    private class RegistrationStatusRequestHandler : RpcHandler<RegistrationStatusRpcRequest> {
        override fun handle(context: MembershipRpcRequestContext, request: RegistrationStatusRpcRequest): Any {
            TODO("Not yet implemented")
        }
    }

    private class MGMGroupPolicyRequestHandler(
        private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
        private val membershipGroupReaderProvider: MembershipGroupReaderProvider
    ) : RpcHandler<MGMGroupPolicyRequest> {
        override fun handle(context: MembershipRpcRequestContext, request: MGMGroupPolicyRequest): Any {

            val holdingIdentity = virtualNodeInfoReadService.getById(request.holdingIdentityId)?.holdingIdentity
                ?: throw GroupPolicyGenerationException("Could not find holding identity associated with ${request.holdingIdentityId}")

            val reader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)

            val filteredMembers =
                reader.lookup(MemberX500Name.parse(holdingIdentity.x500Name))
                    ?: throw CordaRuntimeException("Could not find holding identity associated with ${request.holdingIdentityId}")

            val registrationProtocol =
                filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.group.protocol.registration")  }[0].value.toString()

            val synchronisationProtocol =
                filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.group.protocol.synchronisation") }[0].value.toString()

            return MGMGroupPolicyResponse(
                1,
                filteredMembers.groupId,
                registrationProtocol,
                synchronisationProtocol,
                KeyValuePairList(listOf(
                    KeyValuePair("sessionKeyPolicy", "Combined")
                )),
                KeyValuePairList(listOf(
                    KeyValuePair("sessionTrustRoots", filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.group.truststore.session") }[0].value.toString()),
                    KeyValuePair("tlsTrustRoots", filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.group.truststore.tls") }[0].value.toString()),
                    KeyValuePair("sessionPki", filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.group.pki.session") }[0].value.toString()),
                    KeyValuePair("tlsPki", filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.group.pki.tls") }[0].value.toString()),
                    KeyValuePair("tlsVersion", "1.3"),
                    KeyValuePair("protocolMode", filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.group.protocol.p2p.mode") }[0].value.toString())
                )),
                KeyValuePairList(listOf(
                    KeyValuePair("corda.name", filteredMembers.name.toString()),
                    KeyValuePair("corda.session.key",filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.session.key") }[0].value.toString()),
                    KeyValuePair("corda.session.certificate.0", "corda.session.certificate.0"),
                    KeyValuePair("corda.session.certificate.1", "corda.session.certificate.1"),
                    KeyValuePair("corda.session.certificate.2", "corda.session.certificate.2"),
                    KeyValuePair("corda.ecdh.key", filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.ecdh.key") }[0].value.toString()),
                    KeyValuePair("corda.endpoints.0.connectionUrl",filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.endpoints.0.connectionURL") }[0].value.toString()),
                    KeyValuePair("corda.endpoints.0.protocolVersion",filteredMembers.memberProvidedContext.entries.filter { it.key.startsWith("corda.endpoints.0.protocolVersion") }[0].value.toString()),
                    KeyValuePair("corda.endpoints.1.connectionUrl","corda.endpoints.1.connectionUrl"),
                    KeyValuePair("corda.endpoints.1.protocolVersion","corda.endpoints.1.protocolVersion"),
                    KeyValuePair("corda.platformVersion",filteredMembers.platformVersion.toString()),
                    KeyValuePair("corda.softwareVersion",filteredMembers.softwareVersion),
                    KeyValuePair("corda.serial",filteredMembers.serial.toString())
                    )),
                KeyValuePairList(listOf(
                    KeyValuePair("corda.provider", "default"),
                    KeyValuePair("corda.signature.provider", "default"),
                    KeyValuePair("corda.signature.default", "ECDSA_SECP256K1_SHA256"),
                    KeyValuePair("corda.signature.FRESH_KEYS", "ECDSA_SECP256K1_SHA256"),
                    KeyValuePair("corda.digest.default", "SHA256"),
                    KeyValuePair("corda.cryptoservice.provider", "default")
                ))
            )
        }
    }
}
