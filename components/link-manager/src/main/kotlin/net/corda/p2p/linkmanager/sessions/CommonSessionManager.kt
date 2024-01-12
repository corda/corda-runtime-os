package net.corda.p2p.linkmanager.sessions

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import java.util.Base64
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionInitiationKeys
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.CORDA_4
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.STANDARD
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.STANDARD_EV3
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.CertificateCheckMode
import net.corda.p2p.crypto.protocol.api.HandshakeIdentityData
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.InvalidPeerCertificate
import net.corda.p2p.crypto.protocol.api.NoCommonModeError
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.p2p.linkmanager.common.PublicKeyReader.Companion.getSignatureSpec
import net.corda.p2p.linkmanager.common.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.p2p.linkmanager.grouppolicy.networkType
import net.corda.p2p.linkmanager.grouppolicy.protocolModes
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.membership.lookupByKey
import net.corda.p2p.linkmanager.sessions.CommonSessionManager.Companion.toBase64
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.badGroupPolicy
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.couldNotFindGroupInfo
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.ourHashNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.peerHashNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.validationFailedWarning
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class CommonSessionManager(
    val groupPolicyProvider: GroupPolicyProvider,
    val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    val publisherFactory: PublisherFactory,
    val linkManagerHostingMap: LinkManagerHostingMap,
    val configurationReaderService: ConfigurationReadService,
    val cryptoOpsClient: CryptoOpsClient,
    coordinatorFactory: LifecycleCoordinatorFactory,
    val messagingConfiguration: SmartConfig,
    val protocolFactory: ProtocolFactory = CryptoProtocolFactory(),
): LifecycleWithDominoTile {
    companion object {
        private val logger = LoggerFactory.getLogger(CommonSessionManager::class.java)
        fun ByteArray.toBase64(): String {
            return Base64.getEncoder().encodeToString(this)
        }
    }
    internal val revocationCheckerClient = RevocationCheckerClient(
        publisherFactory,
        coordinatorFactory,
        messagingConfiguration,
    )

    val config = SessionManagerConfig(
        configurationReaderService,
    )

    internal fun processInitiatorHello(
        message: InitiatorHelloMessage,
        createSession: (
            sessionId: String,
            mexMessageSize: Int,
            peer: HoldingIdentity,
        ) -> AuthenticationProtocolResponder = { sessionId, maxMessageSize, _ ->
            val session = protocolFactory.createResponder(sessionId, maxMessageSize)
            session.receiveInitiatorHello(message)
            session
        }
    ): Pair<LinkOutMessage, AuthenticationProtocolResponder>? {
        logger.info("Processing ${message::class.java.simpleName} for session ${message.header.sessionId}.")
        //This will be adjusted so that we use the group policy coming from the CPI with the latest version deployed locally (CORE-5323).
        val hostedIdentitiesInSameGroup = linkManagerHostingMap.allLocallyHostedIdentities()
            .filter { it.groupId == message.source.groupId }
        if (hostedIdentitiesInSameGroup.isEmpty()) {
            logger.warn("There is no locally hosted identity in group ${message.source.groupId}. The initiator message was discarded.")
            return null
        }

        val sessionManagerConfig = config.get()
        val locallyHostedIdentityWithPeerMemberInfo = hostedIdentitiesInSameGroup.mapNotNull { localIdentity ->
            val peerMemberInfo = membershipGroupReaderProvider.lookupByKey(
                localIdentity,
                message.source.initiatorPublicKeyHash.array(),
                MembershipStatusFilter.ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING
            )
            if (peerMemberInfo == null) {
                null
            } else {
                localIdentity to peerMemberInfo
            }
        }.maxByOrNull { it.second.serial }

        if (locallyHostedIdentityWithPeerMemberInfo == null) {
            logger.peerHashNotInMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                message.source.initiatorPublicKeyHash.array().toBase64()
            )
            return null
        }

        val (hostedIdentityInSameGroup, peerMemberInfo) = locallyHostedIdentityWithPeerMemberInfo
        val p2pParams = try {
            groupPolicyProvider.getP2PParameters(hostedIdentityInSameGroup)
        } catch (except: BadGroupPolicyException) {
            logger.badGroupPolicy(message::class.java.simpleName, message.header.sessionId, hostedIdentityInSameGroup, except.message)
            return null
        }
        if (p2pParams == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, hostedIdentityInSameGroup)
            return null
        }

        val session = createSession(message.header.sessionId, sessionManagerConfig.maxMessageSize, peerMemberInfo.holdingIdentity)
        val responderHello = session.generateResponderHello()

        logger.info("Remote identity ${peerMemberInfo.holdingIdentity} initiated new session ${message.header.sessionId}.")
        return MessageConverter.createLinkOutMessage(
            responderHello,
            hostedIdentityInSameGroup,
            peerMemberInfo,
            p2pParams.networkType
        ) to session
    }
    fun processInitiatorHandshake(session: AuthenticationProtocolResponder, message: InitiatorHandshakeMessage): LinkOutMessage? {
        val initiatorIdentityData = session.getInitiatorIdentity()
        val hostedIdentitiesInSameGroup = linkManagerHostingMap.allLocallyHostedIdentities()
            .filter { it.groupId == initiatorIdentityData.groupId }
        if (hostedIdentitiesInSameGroup.isEmpty()) {
            logger.warn("There is no locally hosted identity in group ${initiatorIdentityData.groupId}. The initiator handshake message" +
                    " was discarded.")
            return null
        }

        val peer = hostedIdentitiesInSameGroup
            .firstNotNullOfOrNull { hostedIdentityInSameGroup ->
                membershipGroupReaderProvider
                    .lookupByKey(
                        hostedIdentityInSameGroup,
                        initiatorIdentityData.initiatorPublicKeyHash.array(),
                        MembershipStatusFilter.ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING,
                    )
            }
        if (peer == null) {
            logger.peerHashNotInMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                initiatorIdentityData.initiatorPublicKeyHash.array().toBase64()
            )
            return null
        }

        session.generateHandshakeSecrets()
        val ourIdentityData = session.validatePeerHandshakeMessageHandleError(message, peer) ?: return null
        // Find the correct Holding Identity to use (using the public key hash).
        val ourIdentityInfo = linkManagerHostingMap.getInfo(ourIdentityData.responderPublicKeyHash, ourIdentityData.groupId)
        if (ourIdentityInfo == null) {
            logger.ourHashNotInMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                ourIdentityData.responderPublicKeyHash.toBase64()
            )
            return null
        }

        val p2pParams = try {
            groupPolicyProvider.getP2PParameters(ourIdentityInfo.holdingIdentity)
        } catch (except: BadGroupPolicyException) {
            logger.badGroupPolicy(message::class.java.simpleName, message.header.sessionId, ourIdentityInfo.holdingIdentity, except.message)
            return null
        }
        if (p2pParams == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, ourIdentityInfo.holdingIdentity)
            return null
        }

        val sessionManagerConfig = config.get()
        val pkiMode = pkiMode(p2pParams, sessionManagerConfig) ?: return null
        try {
            session.validateEncryptedExtensions(pkiMode, p2pParams.protocolModes, peer.holdingIdentity.x500Name)
        } catch (exception: InvalidPeerCertificate) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        } catch (exception: NoCommonModeError) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        }

        val tenantId = ourIdentityInfo.holdingIdentity.shortHash.value
        val ourIdentitySessionKey = ourIdentityInfo.allSessionKeysAndCertificates.first {
            session.hash(it.sessionPublicKey).contentEquals(ourIdentityData.responderPublicKeyHash)
        }

        val response = try {
            val ourPublicKey = ourIdentitySessionKey.sessionPublicKey
            val signData = { data: ByteArray ->
                cryptoOpsClient.sign(
                    tenantId,
                    ourIdentitySessionKey.sessionPublicKey,
                    ourIdentitySessionKey.sessionPublicKey.toKeyAlgorithm().getSignatureSpec(),
                    data
                ).bytes
            }
            session.generateOurHandshakeMessage(ourPublicKey, ourIdentitySessionKey.sessionCertificateChain, signData)
        } catch (exception: Exception) {
            logger.warn(
                "Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                        " The message was discarded."
            )
            return null
        }

        return MessageConverter.createLinkOutMessage(
            response,
            ourIdentityInfo.holdingIdentity,
            peer,
            p2pParams.networkType
        )
    }

    private fun AuthenticationProtocolResponder.validatePeerHandshakeMessageHandleError(
        message: InitiatorHandshakeMessage,
        peer: MemberInfo
    ): HandshakeIdentityData? {
        return try {
            validatePeerHandshakeMessage(
                message,
                peer.sessionInitiationKeys.map { it to it.toKeyAlgorithm().getSignatureSpec() }
            )
        } catch (exception: WrongPublicKeyHashException) {
            logger.error("The message was discarded. ${exception.message}")
            null
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            null
        }
    }
    internal fun pkiMode(
        p2pParameters: GroupPolicy.P2PParameters,
        sessionManagerConfig: SessionManagerConfig.Configuration
    ): CertificateCheckMode? {
        return when (p2pParameters.sessionPki) {
            STANDARD -> {
                val trustedCertificates = p2pParameters.sessionTrustRoots?.toList()

                if (trustedCertificates == null) {
                    logger.error("Expected session trust stores to be in p2p parameters ${p2pParameters}.")
                    return null
                }
                CertificateCheckMode.CheckCertificate(
                    trustedCertificates,
                    sessionManagerConfig.revocationConfigMode,
                    revocationCheckerClient::checkRevocation
                )
            }
            STANDARD_EV3, CORDA_4 -> {
                logger.error("PkiMode ${p2pParameters.sessionPki} is unsupported by the link manager.")
                return null
            }
            NO_PKI -> CertificateCheckMode.NoCertificate
        }
    }

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        dependentChildren = setOf(
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
            revocationCheckerClient.dominoTile.coordinatorName,
            linkManagerHostingMap.dominoTile.coordinatorName,
        ),
        managedChildren = setOf(
            revocationCheckerClient.dominoTile.toNamedLifecycle(),
        ),
        configurationChangeHandler = config,
    )

}