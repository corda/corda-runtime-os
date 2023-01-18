package net.corda.membership.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.RegistrationStatusDetails
import net.corda.data.membership.rpc.request.MGMGroupPolicyRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.request.RegistrationRpcRequest
import net.corda.data.membership.rpc.request.RegistrationStatusRpcRequest
import net.corda.data.membership.rpc.request.RegistrationStatusSpecificRpcRequest
import net.corda.data.membership.rpc.response.MGMGroupPolicyResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.data.membership.rpc.response.RegistrationRpcResponse
import net.corda.data.membership.rpc.response.RegistrationRpcStatus
import net.corda.data.membership.rpc.response.RegistrationStatusResponse
import net.corda.data.membership.rpc.response.RegistrationsStatusResponse
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.exceptions.RegistrationProtocolSelectionException
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.MGM_CLIENT_CERTIFICATE_SUBJECT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.PROTOCOL_MODE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TYPE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.SESSION_KEY_POLICY
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.CIPHER_SUITE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.MGM_INFO
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.P2P_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.REGISTRATION_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PropertyKeys
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.lib.toMap
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.GroupPolicyGenerationException
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.membership.registration.RegistrationProxy
import net.corda.membership.registration.RegistrationStatusQueryException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseList
import net.corda.v5.base.util.parseOrNull
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Suppress("LongParameterList")
class MemberOpsServiceProcessor(
    private val registrationProxy: RegistrationProxy,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val membershipQueryClient: MembershipQueryClient,
    private val clock: Clock = UTCClock(),
) : RPCResponderProcessor<MembershipRpcRequest, MembershipRpcResponse> {

    interface RpcHandler<REQUEST> {
        fun handle(context: MembershipRpcRequestContext, request: REQUEST): Any
    }

    companion object {
        private val logger: Logger = contextLogger()

        private val handlers = mapOf<Class<*>, (MemberOpsServiceProcessor) -> RpcHandler<*>>(
            RegistrationRpcRequest::class.java to { it.RegistrationRequestHandler() },
            MGMGroupPolicyRequest::class.java to { it.MGMGroupPolicyRequestHandler() },
            RegistrationStatusRpcRequest::class.java to { it.RegistrationStatusRequestHandler() },
            RegistrationStatusSpecificRpcRequest::class.java to { it.RegistrationStatusSpecificRpcRequestHandler() },
        )

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
                "Failed to handle ${request.request::class.java} for request ID ${request.requestContext.requestId}: ${e.message}"
            logger.error(message, e)
            respFuture.completeExceptionally(MembershipRegistrationException(message, e))
        }
    }

    private fun createResponseContext(request: MembershipRpcRequest) = MembershipRpcResponseContext(
        request.requestContext.requestId,
        request.requestContext.requestTimestamp,
        clock.instant()
    )

    private fun getHandler(request: MembershipRpcRequest): RpcHandler<Any> {
        val factory = handlers[request.request::class.java] ?: throw IllegalArgumentException(
            "Unknown request type ${request.request::class.java.name}"
        )
        @Suppress("UNCHECKED_CAST")
        return factory.invoke(this) as RpcHandler<Any>
    }

    private inner class RegistrationRequestHandler : RpcHandler<RegistrationRpcRequest> {
        override fun handle(context: MembershipRpcRequestContext, request: RegistrationRpcRequest): Any {
            val holdingIdentityShortHash = ShortHash.of(request.holdingIdentityId)
            val holdingIdentity =
                virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)?.holdingIdentity
                    ?: throw MembershipRegistrationException(
                        "Could not find holding identity associated with ${request.holdingIdentityId}"
                    )
            val registrationId = UUID.fromString(context.requestId)
            val result = try {
                registrationProxy.register(registrationId, holdingIdentity, request.context.toMap())
            } catch (e: RegistrationProtocolSelectionException) {
                logger.warn("Could not select registration protocol.", e)
                null
            } catch (e: IllegalStateException) {
                logger.warn("Could not submit registration request.", e)
                null
            }
            val registrationStatus = result?.outcome?.let {
                RegistrationRpcStatus.valueOf(it.toString())
            } ?: RegistrationRpcStatus.NOT_SUBMITTED
            return RegistrationRpcResponse(
                registrationId.toString(),
                context.requestTimestamp,
                registrationStatus,
                result?.message,
                REGISTRATION_PROTOCOL_VERSION,
                KeyValuePairList(emptyList()),
                KeyValuePairList(emptyList())
            )
        }
    }

    private fun RegistrationRequestStatus.toAvro(): RegistrationStatusDetails {
        return RegistrationStatusDetails.newBuilder()
            .setRegistrationSent(this.registrationSent)
            .setRegistrationLastModified(this.registrationLastModified)
            .setRegistrationStatus(this.status)
            .setRegistrationId(this.registrationId)
            .setRegistrationProtocolVersion(this.protocolVersion)
            .setMemberProvidedContext(this.memberContext)
            .build()
    }

    private inner class RegistrationStatusSpecificRpcRequestHandler : RpcHandler<RegistrationStatusSpecificRpcRequest> {
        override fun handle(context: MembershipRpcRequestContext, request: RegistrationStatusSpecificRpcRequest): Any {
            val holdingIdentityShortHash = ShortHash.of(request.holdingIdentityId)
            val holdingIdentity = virtualNodeInfoReadService
                .getByHoldingIdentityShortHash(holdingIdentityShortHash)?.holdingIdentity
                ?: return RegistrationStatusResponse(null)
            val response = membershipQueryClient.queryRegistrationRequestStatus(
                viewOwningIdentity = holdingIdentity,
                registrationId = request.requestId
            ).getOrThrow()
            val details = response?.toAvro()
            return RegistrationStatusResponse(details)
        }
    }

    private inner class RegistrationStatusRequestHandler : RpcHandler<RegistrationStatusRpcRequest> {
        override fun handle(context: MembershipRpcRequestContext, request: RegistrationStatusRpcRequest): Any {
            val holdingIdentityShortHash = ShortHash.of(request.holdingIdentityId)
            val holdingIdentity = virtualNodeInfoReadService
                .getByHoldingIdentityShortHash(holdingIdentityShortHash)?.holdingIdentity
                ?: throw RegistrationStatusQueryException(
                    "Could not find holding identity associated with ${request.holdingIdentityId}"
                )
            val response = membershipQueryClient.queryRegistrationRequestsStatus(
                viewOwningIdentity = holdingIdentity,
            ).getOrThrow()

            return RegistrationsStatusResponse(
                response.map {
                    it.toAvro()
                }
            )
        }
    }

    @Suppress("MaxLineLength")
    private inner class MGMGroupPolicyRequestHandler : RpcHandler<MGMGroupPolicyRequest> {
        override fun handle(context: MembershipRpcRequestContext, request: MGMGroupPolicyRequest): Any {
            val holdingIdentityShortHash = ShortHash.of(request.holdingIdentityId)
            val holdingIdentity = virtualNodeInfoReadService
                .getByHoldingIdentityShortHash(holdingIdentityShortHash)?.holdingIdentity
                ?: throw GroupPolicyGenerationException(
                    "Could not find holding identity associated with ${request.holdingIdentityId}"
                )

            val mgm = membershipGroupReaderProvider
                .getGroupReader(holdingIdentity)
                .lookup(
                    holdingIdentity.x500Name
                )?.also {
                    if (!it.isMgm) {
                        throw GroupPolicyGenerationException(
                            "${request.holdingIdentityId} is not the holding identity of an MGM " +
                                "and so this holding identity cannot generate a group policy file."
                        )
                    }
                } ?: throw GroupPolicyGenerationException(
                "Could not find holding identity associated with ${request.holdingIdentityId}"
            )

            val persistedGroupPolicyProperties = membershipQueryClient
                .queryGroupPolicy(holdingIdentity)
                .getOrThrow()

            val registrationProtocol: String = persistedGroupPolicyProperties.parse(PropertyKeys.REGISTRATION_PROTOCOL)
            val syncProtocol: String = persistedGroupPolicyProperties.parse(PropertyKeys.SYNC_PROTOCOL)
            val p2pMode: String = persistedGroupPolicyProperties.parse(PropertyKeys.P2P_PROTOCOL_MODE)
            val sessionKeyPolicy: String = persistedGroupPolicyProperties.parse(PropertyKeys.SESSION_KEY_POLICY)
            val sessionPkiMode: String = persistedGroupPolicyProperties.parse(PropertyKeys.SESSION_PKI_MODE)
            val tlsPkiMode: String = persistedGroupPolicyProperties.parse(PropertyKeys.TLS_PKI_MODE)
            val tlsVersion: String = persistedGroupPolicyProperties.parse(PropertyKeys.TLS_VERSION)
            val tlsType = TlsType.fromString(
                persistedGroupPolicyProperties.parseOrNull(PropertyKeys.TLS_TYPE, String::class.java)
            ) ?: TlsType.ONE_WAY
            val mgmCertificateSubject = if (tlsType == TlsType.MUTUAL) {
                val subject = persistedGroupPolicyProperties.parse<String>(
                    PropertyKeys.MGM_CLIENT_CERTIFICATE_SUBJECT
                )
                mapOf(MGM_CLIENT_CERTIFICATE_SUBJECT to subject)
            } else {
                emptyMap()
            }

            val isNoSessionPkiMode = GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI ==
                GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.fromString(sessionPkiMode)

            val tlsTrustroots: List<String> = persistedGroupPolicyProperties.parseList(PropertyKeys.TLS_TRUST_ROOTS)
            val sessionTrustroots = if (isNoSessionPkiMode) {
                emptyMap()
            } else {
                mapOf(
                    SESSION_TRUST_ROOTS to
                            persistedGroupPolicyProperties.parseList<String>(PropertyKeys.SESSION_TRUST_ROOTS)
                )
            }
            val p2pParameters = mapOf(
                TLS_TRUST_ROOTS to tlsTrustroots,
                SESSION_PKI to sessionPkiMode,
                TLS_PKI to tlsPkiMode,
                TLS_VERSION to tlsVersion,
                PROTOCOL_MODE to p2pMode,
                TLS_TYPE to tlsType.groupPolicyName,
            ) + sessionTrustroots + mgmCertificateSubject

            val groupPolicy = mapOf(
                FILE_FORMAT_VERSION to 1,
                GROUP_ID to mgm.groupId,
                REGISTRATION_PROTOCOL to registrationProtocol,
                SYNC_PROTOCOL to syncProtocol,
                PROTOCOL_PARAMETERS to mapOf(
                    SESSION_KEY_POLICY to sessionKeyPolicy
                ),
                P2P_PARAMETERS to p2pParameters,
                MGM_INFO to mgm.memberProvidedContext.entries.associate { it.key to it.value },
                CIPHER_SUITE to emptyMap<String, String>()
            )
            return MGMGroupPolicyResponse(ObjectMapper().writeValueAsString(groupPolicy))
        }
    }
}
