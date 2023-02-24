package net.corda.p2p.linkmanager.common

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.DataMessagePayload
import net.corda.data.p2p.HeartbeatMessage
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutHeader
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.MessageAck
import net.corda.data.p2p.NetworkType
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.DecryptionFailedError
import net.corda.p2p.crypto.protocol.api.InvalidMac
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.common.AvroSealedClasses.DataMessage
import net.corda.p2p.linkmanager.common.AvroSealedClasses.SessionAndMessage
import net.corda.p2p.linkmanager.grouppolicy.networkType
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.apache.avro.AvroRuntimeException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer

/**
 * This class contains code which can be used to convert between [LinkOutMessage]/[LinkInMessage] and
 * [FlowMessage] and vice-versa. It is also used to wrap session negotiation messages into [LinkOutMessage].
 */
class MessageConverter {

    companion object {

        private val logger = LoggerFactory.getLogger(this::class.java.name)

        internal fun createLinkOutMessage(
            payload: Any,
            source: HoldingIdentity,
            dest: MemberInfo,
            networkType: NetworkType
        ): LinkOutMessage {
            val header = generateLinkOutHeaderFromPeer(source, dest, networkType)
            return LinkOutMessage(header, payload)
        }

        private fun generateLinkOutHeaderFromPeer(
            source: HoldingIdentity,
            peer: MemberInfo,
            networkType: NetworkType
        ): LinkOutHeader {
            val endPoint = peer.endpoints
                .filter {
                    it.protocolVersion == ProtocolConstants.PROTOCOL_VERSION
                }.shuffled().firstOrNull()
                ?.url
            return LinkOutHeader(
                peer.holdingIdentity.toAvro(),
                source.toAvro(),
                networkType,
                endPoint,
            )
        }

        private fun <T> deserializeHandleAvroErrors(
            deserialize: (ByteBuffer) -> T,
            data: ByteBuffer,
            sessionId: String
        ): T? {
            return try {
                deserialize(data)
            } catch (exception: IOException) {
                logger.warn("Could not deserialize message for session $sessionId. The message was discarded.")
                null
            } catch (exception: AvroRuntimeException) {
                logger.error(
                    "Could not deserialize message for session $sessionId. Error: $exception.message." +
                        " The message was discarded."
                )
                null
            }
        }

        @Suppress("LongParameterList")
        fun linkOutMessageFromAck(
            message: MessageAck,
            source: HoldingIdentity,
            destination: HoldingIdentity,
            session: Session,
            groupPolicyProvider: GroupPolicyProvider,
            membershipGroupReaderProvider: MembershipGroupReaderProvider,
        ): LinkOutMessage? {
            val serializedMessage = try {
                message.toByteBuffer()
            } catch (exception: IOException) {
                logger.error("Could not serialize message type ${message::class.java.simpleName}. The message was discarded.")
                return null
            }
            return createLinkOutMessageFromPayload(
                serializedMessage,
                source,
                destination,
                session,
                groupPolicyProvider,
                membershipGroupReaderProvider,
                MembershipStatusFilter.ACTIVE_IF_PRESENT_OR_PENDING,
            )
        }

        fun linkOutMessageFromAuthenticatedMessageAndKey(
            message: AuthenticatedMessageAndKey,
            session: Session,
            groupPolicyProvider: GroupPolicyProvider,
            membershipGroupReaderProvider: MembershipGroupReaderProvider,
            serial: Long,
        ): LinkOutMessage? {
            val serializedMessage = try {
                DataMessagePayload(message).toByteBuffer()
            } catch (exception: IOException) {
                logger.error("Could not serialize message type ${message::class.java.simpleName}. The message was discarded.")
                return null
            }
            return createLinkOutMessageFromPayload(
                serializedMessage,
                message.message.header.source.toCorda(),
                message.message.header.destination.toCorda(),
                session,
                groupPolicyProvider,
                membershipGroupReaderProvider,
                message.message.header.statusFilter,
                serial
            )
        }

        @Suppress("LongParameterList")
        fun linkOutMessageFromHeartbeat(
            source: HoldingIdentity,
            destination: HoldingIdentity,
            message: HeartbeatMessage,
            session: Session,
            groupPolicyProvider: GroupPolicyProvider,
            membershipGroupReaderProvider: MembershipGroupReaderProvider,
            filter: MembershipStatusFilter,
            serial: Long,
        ): LinkOutMessage? {
            val serializedMessage = try {
                DataMessagePayload(message).toByteBuffer()
            } catch (exception: IOException) {
                logger.error("Could not serialize message type ${message::class.java.simpleName}. The message was discarded.")
                return null
            }
            return createLinkOutMessageFromPayload(
                serializedMessage,
                source,
                destination,
                session,
                groupPolicyProvider,
                membershipGroupReaderProvider,
                filter,
                serial
            )
        }

        fun linkOutFromUnauthenticatedMessage(
            message: UnauthenticatedMessage,
            destMemberInfo: MemberInfo,
            groupPolicy: GroupPolicy,
        ): LinkOutMessage {
            val source = message.header.source.toCorda()

            return createLinkOutMessage(
                message,
                source,
                destMemberInfo,
                groupPolicy.networkType,
                )
        }

        @Suppress("LongParameterList")
        private fun createLinkOutMessageFromPayload(
            serializedPayload: ByteBuffer,
            source: HoldingIdentity,
            destination: HoldingIdentity,
            session: Session,
            groupPolicyProvider: GroupPolicyProvider,
            membershipGroupReaderProvider: MembershipGroupReaderProvider,
            filter: MembershipStatusFilter,
            serial: Long? = null,
        ): LinkOutMessage? {
            val result = when (session) {
                is AuthenticatedSession -> {
                    val result = session.createMac(serializedPayload.array())
                    AuthenticatedDataMessage(result.header, serializedPayload, ByteBuffer.wrap(result.mac))
                }
                is AuthenticatedEncryptionSession -> {
                    val result = session.encryptData(serializedPayload.array())
                    AuthenticatedEncryptedDataMessage(
                        result.header,
                        ByteBuffer.wrap(result.encryptedPayload),
                        ByteBuffer.wrap(result.authTag)
                    )
                }
                else -> {
                    logger.warn(
                        "Invalid Session type ${session::class.java.simpleName}.Session must be either " +
                            "${AuthenticatedSession::class.java.simpleName} or ${AuthenticatedEncryptionSession::class.java.simpleName}." +
                            " The message was discarded."
                    )
                    return null
                }
            }

            val destMemberInfo = membershipGroupReaderProvider.lookup(source, destination, filter)
            if (destMemberInfo == null) {
                logger.warn("Attempted to send message to peer $destination with filter $filter " +
                        "which is not in the network map. The message was discarded.")
                return null
            }
            if(serial != null && destMemberInfo.serial != serial) {
                logger.warn("Attempted to send message to peer $destination with serial $serial " +
                        "which is not in the network map. The message was discarded.")
                return null
            }
            val groupPolicy = groupPolicyProvider.getGroupPolicy(source)
            if (groupPolicy == null) {
                logger.warn(
                    "Could not find the group info in the " +
                        "GroupPolicyProvider for our identity = $source. The message was discarded."
                )
                return null
            }

            return createLinkOutMessage(
                result,
                source,
                destMemberInfo,
                groupPolicy.networkType,
            )
        }

        fun <T> extractPayload(session: Session, sessionId: String, message: DataMessage, deserialize: (ByteBuffer) -> T): T? {
            val sessionAndMessage = SessionAndMessage.create(session, sessionId, message) ?: return null
            return when (sessionAndMessage) {
                is SessionAndMessage.Authenticated -> extractPayloadFromAuthenticatedMessage(sessionAndMessage, deserialize)
                is SessionAndMessage.AuthenticatedEncrypted ->
                    extractPayloadFromAuthenticatedEncryptedMessage(sessionAndMessage, deserialize)
            }
        }

        fun <T> extractPayloadFromAuthenticatedEncryptedMessage(
            sessionAndMessage: SessionAndMessage.AuthenticatedEncrypted,
            deserialize: (ByteBuffer) -> T
        ): T? {
            val message = sessionAndMessage.message
            val session = sessionAndMessage.session
            val decryptedData = try {
                session.decryptData(message.header, message.encryptedPayload.array(), message.authTag.array())
            } catch (exception: DecryptionFailedError) {
                logger.warn(
                    "Decryption failed for message for session ${message.header.sessionId}. Reason: ${exception.message} " +
                        "The message was discarded."
                )
                return null
            }
            return deserializeHandleAvroErrors(deserialize, ByteBuffer.wrap(decryptedData), message.header.sessionId)
        }

        fun <T> extractPayloadFromAuthenticatedMessage(
            sessionAndMessage: SessionAndMessage.Authenticated,
            deserialize: (ByteBuffer) -> T
        ): T? {
            val message = sessionAndMessage.message
            val session = sessionAndMessage.session
            try {
                session.validateMac(message.header, message.payload.array(), message.authTag.array())
            } catch (exception: InvalidMac) {
                logger.warn("MAC check failed for message for session ${message.header.sessionId}. The message was discarded.")
                return null
            }
            return deserializeHandleAvroErrors(deserialize, message.payload, message.header.sessionId)
        }
    }
}
