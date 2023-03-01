package net.corda.membership.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.core.ShortHash
import net.corda.data.membership.rpc.request.MGMGroupPolicyRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.response.MGMGroupPolicyResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
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
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PropertyKeys
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.GroupPolicyGenerationException
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.utilities.parse
import net.corda.utilities.parseList
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class MemberOpsServiceProcessor(
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val membershipQueryClient: MembershipQueryClient,
    private val clock: Clock = UTCClock(),
) : RPCResponderProcessor<MembershipRpcRequest, MembershipRpcResponse> {

    interface RpcHandler<REQUEST> {
        fun handle(context: MembershipRpcRequestContext, request: REQUEST): Any
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private val handlers = mapOf<Class<*>, (MemberOpsServiceProcessor) -> RpcHandler<*>>(
            MGMGroupPolicyRequest::class.java to { it.MGMGroupPolicyRequestHandler() },
        )
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
                MGM_INFO to mgm.memberProvidedContext.entries.associate { it.key to it.value }
                    .plus(SERIAL to mgm.serial.toString()),
                CIPHER_SUITE to emptyMap<String, String>()
            )
            return MGMGroupPolicyResponse(ObjectMapper().writeValueAsString(groupPolicy))
        }
    }
}
