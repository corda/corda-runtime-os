package net.corda.p2p.linkmanager.sessions

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.CORDA_4
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.STANDARD
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.STANDARD_EV3
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionInitiationKeys
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.CertificateCheckMode
import net.corda.p2p.crypto.protocol.api.HandshakeIdentityData
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.crypto.protocol.api.InvalidPeerCertificate
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.common.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.common.PublicKeyReader.Companion.getSignatureSpec
import net.corda.p2p.linkmanager.common.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.p2p.linkmanager.grouppolicy.networkType
import net.corda.p2p.linkmanager.grouppolicy.protocolModes
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.membership.lookupByKey
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionCounterparties
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.couldNotFindGroupInfo
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.ourHashNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.ourIdNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.peerHashNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.peerNotInTheMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.validationFailedWarning
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.VisibleForTesting
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.p2p.crypto.protocol.api.InvalidSelectedModeError
import net.corda.p2p.crypto.protocol.api.NoCommonModeError
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.badGroupPolicy

@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
internal class SessionManagerImpl(
    private val groupPolicyProvider: GroupPolicyProvider,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val cryptoOpsClient: CryptoOpsClient,
    private val pendingOutboundSessionMessageQueues: PendingSessionMessageQueues,
    publisherFactory: PublisherFactory,
    private val configurationReaderService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig,
    private val linkManagerHostingMap: LinkManagerHostingMap,
    private val protocolFactory: ProtocolFactory = CryptoProtocolFactory(),
) : LifecycleWithDominoTile {

    private companion object {
        private const val SESSION_MANAGER_CLIENT_ID = "session-manager"
    }

    private val pendingInboundSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeInboundSessions = ConcurrentHashMap<String, Pair<SessionManager.Counterparties, Session>>()

    private val logger = LoggerFactory.getLogger(this::class.java)

    // This default needs to be removed and the lifecycle dependency graph adjusted to ensure the inbound subscription starts only after
    // the configuration has been received and the session manager has started (see CORE-6730).
    private val config = AtomicReference(
        SessionManagerConfig(
            1000000,
            RevocationCheckMode.OFF,
        )
    )

    internal val publisher = PublisherWithDominoLogic(
        publisherFactory,
        coordinatorFactory,
        PublisherConfig(SESSION_MANAGER_CLIENT_ID, false),
        messagingConfiguration
    )

    internal val revocationCheckerClient = RevocationCheckerClient(publisherFactory, coordinatorFactory, messagingConfiguration)

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        dependentChildren = setOf(
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
            pendingOutboundSessionMessageQueues.dominoTile.coordinatorName, publisher.dominoTile.coordinatorName,
            linkManagerHostingMap.dominoTile.coordinatorName,
            revocationCheckerClient.dominoTile.coordinatorName,
        ),
        managedChildren = setOf(
            publisher.dominoTile.toNamedLifecycle(), revocationCheckerClient.dominoTile.toNamedLifecycle()),
        configurationChangeHandler = SessionManagerConfigChangeHandler()
    )

    @VisibleForTesting
    internal data class SessionManagerConfig(
        val maxMessageSize: Int,
        val revocationConfigMode: RevocationCheckMode,
    )

    internal inner class SessionManagerConfigChangeHandler : ConfigurationChangeHandler<SessionManagerConfig>(
        configurationReaderService,
        ConfigKeys.P2P_LINK_MANAGER_CONFIG,
        ::fromConfig
    ) {
        override fun applyNewConfiguration(
            newConfiguration: SessionManagerConfig,
            oldConfiguration: SessionManagerConfig?,
            resources: ResourcesHolder,
        ): CompletableFuture<Unit> {
            val configUpdateResult = CompletableFuture<Unit>()
            dominoTile.withLifecycleWriteLock {
                config.set(newConfiguration)
                if (oldConfiguration != null) {
                    logger.info("The Session Manager got new config. All sessions will be cleaned up.")
                    activeInboundSessions.clear()
                    pendingInboundSessions.clear()
                    // This is suboptimal we could instead restart session negotiation
                    pendingOutboundSessionMessageQueues.destroyAllQueues()
                }
            }
            configUpdateResult.complete(Unit)
            return configUpdateResult
        }
    }

    private fun fromConfig(config: Config): SessionManagerConfig {
        return SessionManagerConfig(
            config.getInt(LinkManagerConfiguration.MAX_MESSAGE_SIZE_KEY),
            config.getEnum(RevocationCheckMode::class.java, LinkManagerConfiguration.REVOCATION_CHECK_KEY),
        )
    }

    internal fun genSessionInitMessages(
        counterparties: SessionCounterparties,
        multiplicity: Int
    ): List<Pair<AuthenticationProtocolInitiator, InitiatorHelloMessage>> {

        val p2pParams = try {
            groupPolicyProvider.getP2PParameters(counterparties.ourId)
        } catch (except: BadGroupPolicyException) {
            logger.warn("The group policy data is unavailable or cannot be parsed for ${counterparties.ourId}. Error: ${except.message}. " +
                "The sessionInit message was not sent.")
            return emptyList()
        }
        if (p2pParams == null) {
            logger.warn(
                "Could not find the p2p parameters in the GroupPolicyProvider for ${counterparties.ourId}." +
                    " The sessionInit message was not sent."
            )
            return emptyList()
        }

        val ourIdentityInfo = linkManagerHostingMap.getInfo(counterparties.ourId)
        if (ourIdentityInfo == null) {
            logger.warn(
                "Attempted to start session negotiation with peer ${counterparties.counterpartyId} but our identity " +
                    "${counterparties.ourId} is not in the members map. The sessionInit message was not sent."
            )
            return emptyList()
        }

        val sessionManagerConfig = config.get()
        val messagesAndProtocol = mutableListOf<Pair<AuthenticationProtocolInitiator, InitiatorHelloMessage>>()
        val pkiMode = pkiMode(p2pParams, sessionManagerConfig) ?: return emptyList()
        (1..multiplicity).map {
            val sessionId = UUID.randomUUID().toString()
            val session = protocolFactory.createInitiator(
                sessionId,
                p2pParams.protocolModes,
                sessionManagerConfig.maxMessageSize,
                ourIdentityInfo.preferredSessionKeyAndCertificates.sessionPublicKey,
                ourIdentityInfo.holdingIdentity.groupId,
                pkiMode
            )
            messagesAndProtocol.add(Pair(session, session.generateInitiatorHello()))
        }
        return messagesAndProtocol
    }

    private fun pkiMode(
        p2pParameters: GroupPolicy.P2PParameters,
        sessionManagerConfig: SessionManagerConfig
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

    internal fun linkOutMessagesFromSessionInitMessages(
        sessionCounterparties: SessionCounterparties,
        messages: List<Pair<AuthenticationProtocolInitiator, InitiatorHelloMessage>>,
        filter: MembershipStatusFilter,
    ): List<Pair<String, LinkOutMessage>>? {
        val sessionIds = messages.map { it.first.sessionId }
        logger.info(
            "Local identity (${sessionCounterparties.ourId}) initiating new sessions with Ids $sessionIds with remote identity " +
                "${sessionCounterparties.counterpartyId}"
        )

        val responderMemberInfo = membershipGroupReaderProvider.lookup(
            sessionCounterparties.ourId,
            sessionCounterparties.counterpartyId,
            filter
        )
        if (responderMemberInfo != null && responderMemberInfo.serial > sessionCounterparties.serial) {
            logger.warn(
                "Attempted to start session negotiation with peer ${sessionCounterparties.counterpartyId} which is " +
                        "in ${sessionCounterparties.ourId}'s members map but the serial number has progressed beyond " +
                        "the requested serial number. The sessionInit message was not sent and will not be retried."
            )
            return null
        }

        if (responderMemberInfo == null) {
            logger.warn(
                "Attempted to start session negotiation with peer ${sessionCounterparties.counterpartyId} which is " +
                        "not in ${sessionCounterparties.ourId}'s members map. Filter was $filter. The sessionInit " +
                        "message was not sent. Message will be retried."
            )
            return null
        } else if (responderMemberInfo.serial < sessionCounterparties.serial) {
            logger.warn(
                "Attempted to start session negotiation with peer ${sessionCounterparties.counterpartyId} which is " +
                        "not in ${sessionCounterparties.ourId}'s members map with serial " +
                        "${sessionCounterparties.serial}. Filter was $filter and serial found was " +
                        "${responderMemberInfo.serial}. The sessionInit message was not sent. Message will be retried."
            )
            return null
        }

        val p2pParams = try {
            groupPolicyProvider.getP2PParameters(sessionCounterparties.ourId)
        } catch (except: BadGroupPolicyException) {
            logger.warn("The group policy data is unavailable or cannot be parsed for ${sessionCounterparties.ourId}. Error: " +
                "${except.message}. The sessionInit message was not sent.")
            return emptyList()
        }
        if (p2pParams == null) {
            logger.warn(
                "Could not find the group information in the GroupPolicyProvider for ${sessionCounterparties.ourId}." +
                    " The sessionInit message was not sent."
            )
            return emptyList()
        }
        return messages.mapNotNull { message ->
            createLinkOutMessage(
                message.second,
                sessionCounterparties.ourId,
                responderMemberInfo,
                p2pParams.networkType
            )?.let {
                message.first.sessionId to it
            }
        }
    }

    private fun ByteArray.toBase64(): String {
        return Base64.getEncoder().encodeToString(this)
    }

    @Suppress("ComplexMethod")
    internal fun processResponderHello(
        sessionInfo: SessionCounterparties,
        session: AuthenticationProtocolInitiator,
        message: ResponderHelloMessage,
    ): Pair<LinkOutMessage?, AuthenticationProtocolInitiator>? {

        session.receiveResponderHello(message)
        session.generateHandshakeSecrets()

        val ourIdentityInfo = linkManagerHostingMap.getInfo(sessionInfo.ourId)
        if (ourIdentityInfo == null) {
            logger.ourIdNotInMembersMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.ourId)
            return null
        }

        val responderMemberInfo = membershipGroupReaderProvider
            .lookup(sessionInfo.ourId, sessionInfo.counterpartyId, sessionInfo.status)
        if (responderMemberInfo == null) {
            logger.peerNotInTheMembersMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.counterpartyId)
            return null
        }

        val tenantId = ourIdentityInfo.holdingIdentity.shortHash.value
        val ourIdentitySessionKey = ourIdentityInfo.preferredSessionKeyAndCertificates

        val signWithOurGroupId = { data: ByteArray ->
            cryptoOpsClient.sign(
                tenantId,
                ourIdentitySessionKey.sessionPublicKey,
                ourIdentitySessionKey.sessionPublicKey.toKeyAlgorithm().getSignatureSpec(),
                data
            ).bytes
        }
        val payload = try {
            session.generateOurHandshakeMessage(
                responderMemberInfo.sessionInitiationKeys.first(),
                ourIdentitySessionKey.sessionCertificateChain,
                signWithOurGroupId
            )
        } catch (exception: Exception) {
            logger.warn(
                "${exception.message}. The ${message::class.java.simpleName} with sessionId ${message.header.sessionId}" +
                    " was discarded."
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

        return createLinkOutMessage(payload, sessionInfo.ourId, responderMemberInfo, p2pParams.networkType) to session
    }

    internal fun processResponderHandshake(
        message: ResponderHandshakeMessage,
        sessionCounterparties: SessionCounterparties,
        session: AuthenticationProtocolInitiator,
    ): Session? {
        val memberInfo = membershipGroupReaderProvider.lookup(
            sessionCounterparties.ourId, sessionCounterparties.counterpartyId, sessionCounterparties.status
        )
        if (memberInfo == null) {
            logger.peerNotInTheMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                sessionCounterparties.counterpartyId
            )
            return null
        }

        if (!session.validatePeerHandshakeMessageHandleError(message, memberInfo, sessionCounterparties)) {
            return null
        }
        return session.getSession()
    }

    //Only use by Stateful Session Manager
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
        return createLinkOutMessage(
            responderHello,
            hostedIdentityInSameGroup,
            peerMemberInfo,
            p2pParams.networkType,
        )?.let {
            it to session
        }
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

        return createLinkOutMessage(response, ourIdentityInfo.holdingIdentity, peer, p2pParams.networkType)
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

    private fun AuthenticationProtocolInitiator.validatePeerHandshakeMessageHandleError(
        message: ResponderHandshakeMessage,
        memberInfo: MemberInfo,
        sessionCounterparties: SessionCounterparties,
    ): Boolean {
        try {
            this.validatePeerHandshakeMessage(
                message,
                sessionCounterparties.counterpartyId.x500Name,
                memberInfo.sessionInitiationKeys.map {
                    it to it.toKeyAlgorithm().getSignatureSpec()
                },
            )
            return true
        } catch (exception: InvalidHandshakeResponderKeyHash) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
        } catch (exception: InvalidPeerCertificate) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
        } catch (exception: InvalidSelectedModeError) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
        }
        return false
    }
}
