package net.corda.p2p.linkmanager.common

import net.corda.data.p2p.AuthenticatedMessageAck
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.MessageAck
import net.corda.data.p2p.NetworkType
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.p2p.crypto.protocol.api.DecryptionFailedError
import net.corda.p2p.crypto.protocol.api.InvalidMac
import net.corda.p2p.linkmanager.common.AvroSealedClasses.SessionAndMessage
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.mockGroups
import net.corda.p2p.linkmanager.utilities.mockMembers
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.ENDPOINTS
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.PROTOCOL_VERSION
import net.corda.p2p.linkmanager.common.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.mockMembersAndGroups
import net.corda.schema.Schemas
import net.corda.test.util.time.MockTimeFacilitiesProvider
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.junit.jupiter.api.Nested
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class MessageConverterTest {

    companion object {

        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    private val mockHeader = mock<CommonHeader> {
        on { sequenceNo } doReturn 1
        on { toByteBuffer() } doReturn ByteBuffer.wrap("HEADER".toByteArray())
    }

    @AfterEach
    fun resetLogging() {
        loggingInterceptor.reset()
    }

    @Test
    fun `convertAuthenticatedEncryptedMessageToFlowMessage returns null (with appropriate logging) if authentication fails`() {
        val session = mock<AuthenticatedEncryptionSession> {
            on { decryptData(any(), any(), any()) } doThrow DecryptionFailedError(
                "Decryption failed due to bad authentication tag.",
                Exception("")
            )
        }
        val mockMessage = mock<AuthenticatedEncryptedDataMessage> {
            on { authTag } doReturn ByteBuffer.wrap("AuthTag".toByteArray())
            on { encryptedPayload } doReturn ByteBuffer.wrap("PAYLOAD".toByteArray())
            on { header } doReturn mockHeader
        }
        assertThat(
            MessageConverter.extractPayloadFromAuthenticatedEncryptedMessage(
                SessionAndMessage.AuthenticatedEncrypted(session, mockMessage),
                AuthenticatedMessageAndKey::fromByteBuffer
            )
        ).isNull()
        loggingInterceptor.assertSingleWarning(
            "Decryption failed for message for session null. Reason: Decryption failed due " +
                "to bad authentication tag. The message was discarded."
        )
    }

    @Test
    fun `convertAuthenticatedMessageToFlowMessage returns null (with appropriate logging) if authentication fails`() {
        val session = mock<AuthenticatedSession> {
            on { validateMac(any(), any(), any()) } doThrow InvalidMac()
        }
        val mockMessage = mock<AuthenticatedDataMessage> {
            on { authTag } doReturn ByteBuffer.wrap("AuthTag".toByteArray())
            on { payload } doReturn ByteBuffer.wrap("PAYLOAD".toByteArray())
            on { header } doReturn mockHeader
        }

        assertThat(
            MessageConverter.extractPayloadFromAuthenticatedMessage(
                SessionAndMessage.Authenticated(session, mockMessage),
                AuthenticatedMessageAndKey::fromByteBuffer
            )
        ).isNull()
        loggingInterceptor.assertSingleWarning("MAC check failed for message for session null. The message was discarded.")
    }

    @Test
    fun `linkOutMessageFromAuthenticatedMessageAndKey returns null if the destination is not in the network map`() {
        val mac = mock<AuthenticationResult> {
            on { header } doReturn mockHeader
            on { mac } doReturn byteArrayOf()
        }
        val session = mock<AuthenticatedSession> {
            on { createMac(any()) } doReturn mac
        }
        val groupId = "group-1"
        val peer = createTestHoldingIdentity("CN=Impostor, O=Evil Corp, L=LDN, C=GB", groupId)
        val members = mockMembers(emptyList())
        val flowMessage = authenticatedMessageAndKey(
            createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId),
            peer,
            ByteBuffer.wrap("DATA".toByteArray())
        )
        val serial = 1L
        assertThat(
            MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(flowMessage, session, mock(), members, serial)
        ).isNull()
        loggingInterceptor.assertSingleWarning(
            "Attempted to send message to peer $peer with filter ACTIVE which is not in the network map. " +
                    "The message was discarded."
        )
    }

    @Test
    fun `linkOutMessageFromAuthenticatedMessageAndKey returns null if the destination does not have the expected serial`() {
        val mac = mock<AuthenticationResult> {
            on { header } doReturn mockHeader
            on { mac } doReturn byteArrayOf()
        }
        val session = mock<AuthenticatedSession> {
            on { createMac(any()) } doReturn mac
        }
        val groupId = "group-1"
        val peer = createTestHoldingIdentity("CN=Impostor, O=Evil Corp, L=LDN, C=GB", groupId)
        val members = mockMembers(listOf(peer))
        val flowMessage = authenticatedMessageAndKey(
            createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId),
            peer,
            ByteBuffer.wrap("DATA".toByteArray())
        )
        val serial = 2L
        assertThat(
            MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(flowMessage, session, mock(), members, serial)
        ).isNull()
        loggingInterceptor.assertSingleWarning(
            "Attempted to send message to peer $peer with serial $serial which is not in the network map." +
                    " The message was discarded."
        )
    }

    @Test
    fun `linkOutMessageFromAuthenticatedMessageAndKey returns null if our p2p params is not in the group policy provider`() {
        val mac = mock<AuthenticationResult> {
            on { header } doReturn mockHeader
            on { mac } doReturn byteArrayOf()
        }
        val session = mock<AuthenticatedSession> {
            on { createMac(any()) } doReturn mac
        }
        val groupId = "group-1"
        val peer = createTestHoldingIdentity("CN=Impostor, O=Evil Corp, L=LDN, C=GB", groupId)
        val us = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId)
        val members = mockMembers(listOf(us, peer))
        val groups = mockGroups(emptyList())
        val flowMessage = authenticatedMessageAndKey(us, peer, ByteBuffer.wrap("DATA".toByteArray()))
        assertThat(MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(flowMessage, session, groups, members, 1)).isNull()
        loggingInterceptor.assertSingleWarning(
            "Could not find the group info in the GroupPolicyProvider for our identity = $us." +
                " The message was discarded."
        )
    }

    @Test
    fun `linkOutMessageFromAuthenticatedMessageAndKey returns null, if BadGroupPolicy exception is thrown on group policy look up`() {
        val mac = mock<AuthenticationResult> {
            on { header } doReturn mockHeader
            on { mac } doReturn byteArrayOf()
        }
        val session = mock<AuthenticatedSession> {
            on { createMac(any()) } doReturn mac
        }
        val groupId = "group-1"
        val peer = createTestHoldingIdentity("CN=Impostor, O=Evil Corp, L=LDN, C=GB", groupId)
        val us = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId)
        val members = mockMembers(listOf(us, peer))
        val groups = mock<GroupPolicyProvider> {
            on { getP2PParameters(us) } doThrow BadGroupPolicyException("Bad group policy.")
        }
        val flowMessage = authenticatedMessageAndKey(us, peer, ByteBuffer.wrap("DATA".toByteArray()))
        assertThat(MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(flowMessage, session, groups, members, 1)).isNull()
        loggingInterceptor.assertSingleWarningContains("Bad group policy.")
    }

    @Test
    fun `createLinkOutMessage does not validate serial when it's not provided`(){
        val mac = mock<AuthenticationResult> {
            on { header } doReturn mockHeader
            on { mac } doReturn byteArrayOf()
        }
        val session = mock<AuthenticatedSession> {
            on { createMac(any()) } doReturn mac
        }
        val message = MessageAck(AuthenticatedMessageAck("messageId"))
        val groupId = "group-1"
        val peer = createTestHoldingIdentity("CN=Impostor, O=Evil Corp, L=LDN, C=GB", groupId)
        val us = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId)
        val members = mockMembers(listOf(us, peer))
        val groups = mockGroups(emptyList())
        assertThat(MessageConverter.linkOutMessageFromAck(message, us, peer, session, groups, members)).isNull()
        loggingInterceptor.assertSingleWarning(
            "Could not find the group info in the GroupPolicyProvider for our identity = $us." +
                    " The message was discarded."
        )
    }

    @Test
    fun `createLinkOutMessage will fail if the peer has no valid endpoints`(){
        val payload = mock<InitiatorHandshakeMessage>()
        val groupId = "groupId"
        val source = createTestHoldingIdentity("CN=Source, O=R3, L=LDN, C=GB", groupId)
        val endpointsList = (6.. 8).map {  version ->
            mock<EndpointInfo> {
                on { protocolVersion } doReturn version
            }
        }
        val memberContext = mock<MemberContext> {
            on { parseList(ENDPOINTS, EndpointInfo::class.java) } doReturn endpointsList
        }
        val destination = mock<MemberInfo> {
            on { memberProvidedContext } doReturn memberContext
        }
        val networkType = NetworkType.CORDA_5

        val message = createLinkOutMessage(
            payload,
            source,
            destination,
            networkType
        )

        assertThat(message).isNull()
    }

    @Test
    fun `createLinkOutMessage will choose the correct endpoint`(){
        val payload = mock<InitiatorHandshakeMessage>()
        val groupId = "groupId"
        val source = createTestHoldingIdentity("CN=Source, O=R3, L=LDN, C=GB", groupId)
        val endpointsList = ((PROTOCOL_VERSION - 3).. (PROTOCOL_VERSION + 3)).map {  version ->
            mock<EndpointInfo> {
                on { protocolVersion } doReturn version
                on { url } doReturn "https://www.r3.com:8080/$version"
            }
        }
        val memberContext = mock<MemberContext> {
            on { parseList(ENDPOINTS, EndpointInfo::class.java) } doReturn endpointsList
            on { parse(MemberInfoExtension.GROUP_ID, String::class.java) } doReturn groupId
        }
        val destination = mock<MemberInfo> {
            on { memberProvidedContext } doReturn memberContext
            on { name } doReturn MemberX500Name.parse("CN=Destination, O=R3, L=LDN, C=GB")
        }
        val networkType = NetworkType.CORDA_5

        val message = createLinkOutMessage(
            payload,
            source,
            destination,
            networkType
        )

        assertThat(message?.header?.address).isEqualTo("https://www.r3.com:8080/$PROTOCOL_VERSION")
    }

    @Nested
    inner class RecordsForSessionEstablishedTest {
        private val carol = createTestHoldingIdentity("CN=Carol, O=Corp, L=LDN, C=GB", "group-1")
        private val david = createTestHoldingIdentity("CN=Carol, O=Corp, L=LDN, C=GB", "group-1")
        private val membersAndGroups = mockMembersAndGroups(
            carol,
            david,
        )
        private val mac = mock<AuthenticationResult> {
            on { mac } doReturn "mac".toByteArray()
        }
        private val session = mock<AuthenticatedSession> {
            on { sessionId } doReturn "SessionId"
            on { createMac(any()) } doReturn mac
        }
        private val clock = MockTimeFacilitiesProvider().clock
        private val sessionManager = mock<SessionManager>()
        private val header = AuthenticatedMessageHeader(
            carol.toAvro(),
            david.toAvro(),
            null,
            "msg",
            "",
            "system-1",
            MembershipStatusFilter.ACTIVE
        )
        private val data = ByteBuffer.wrap(byteArrayOf(1 ,3, 4))
        private val messageAndKey = AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")

        private val converter = MessageConverter(
            membersAndGroups.second,
            membersAndGroups.first,
            clock,
        )

        @Test
        fun `it will create the message and marker`() {
            val records = converter.recordsForSessionEstablished(
                sessionManager,
                session,
                1L,
                messageAndKey,
            )

            assertThat(records).hasSize(2)

        }

        @Test
        fun `it will create link out message`() {
            val records = converter.recordsForSessionEstablished(
                sessionManager,
                session,
                1L,
                messageAndKey,
            )
            val linkOut = records.firstOrNull {
                it.topic == Schemas.P2P.LINK_OUT_TOPIC
            }?.value as? LinkOutMessage

            assertThat(linkOut).isNotNull()
        }

        @Test
        fun `it will create marker message`() {
            val records = converter.recordsForSessionEstablished(
                sessionManager,
                session,
                1L,
                messageAndKey,
            )
            val marker = records.firstOrNull {
                it.topic == Schemas.P2P.P2P_OUT_MARKERS
            }?.value as? AppMessageMarker

            assertThat(marker?.marker).isInstanceOf(LinkManagerSentMarker::class.java)
        }

        @Test
        fun `it will notify the session manager`() {
            converter.recordsForSessionEstablished(
                sessionManager,
                session,
                1L,
                messageAndKey,
            )

            verify(sessionManager).dataMessageSent(session)
        }

        @Test
        fun `when called with unknown member, it will not create any record`() {
            val bob = createTestHoldingIdentity("CN=Bob, O=Corp, L=LDN, C=GB", "group-1")
            val header = AuthenticatedMessageHeader(
                bob.toAvro(),
                carol.toAvro(),
                null,
                "msg",
                "",
                "system-1",
                MembershipStatusFilter.ACTIVE
            )
            val messageAndKey = AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")

            val records = converter.recordsForSessionEstablished(
                sessionManager,
                session,
                1L,
                messageAndKey,
            )

            assertThat(records).isEmpty()
        }

        @Test
        fun `when called with unknown member, it will not notify the manager`() {
            val bob = createTestHoldingIdentity("CN=Bob, O=Corp, L=LDN, C=GB", "group-1")
            val header = AuthenticatedMessageHeader(
                bob.toAvro(),
                carol.toAvro(),
                null,
                "msg",
                "",
                "system-1",
                MembershipStatusFilter.ACTIVE
            )
            val messageAndKey = AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")

            converter.recordsForSessionEstablished(
                sessionManager,
                session,
                1L,
                messageAndKey,
            )

            verify(sessionManager, never()).dataMessageSent(session)
        }

    }

    private fun authenticatedMessageAndKey(
        source: HoldingIdentity,
        dest: HoldingIdentity,
        data: ByteBuffer,
        messageId: String = ""
    ): AuthenticatedMessageAndKey {
        val header = AuthenticatedMessageHeader(
            dest.toAvro(), source.toAvro(), null, messageId, "", "system-1", MembershipStatusFilter.ACTIVE
        )
        return AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")
    }
}
