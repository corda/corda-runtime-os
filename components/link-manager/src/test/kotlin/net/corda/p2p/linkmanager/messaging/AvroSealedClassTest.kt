package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.linkmanager.LinkManagerTest.Companion.createSessionPair
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AvroSealedClassTest {

    companion object {
        const val SESSION_ID = "sessionId"

        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    @AfterEach
    fun resetLogging() {
        loggingInterceptor.reset()
    }

    @Test
    fun `Cannot create a SessionAndMessage with an AuthenticatedEncryptedDataMessage and an AuthenticatedSession`() {
        val authenticatedEncryptedDataMessage =
            AvroSealedClasses.DataMessage.AuthenticatedAndEncrypted(Mockito.mock(AuthenticatedEncryptedDataMessage::class.java))
        val authenticatedSession = createSessionPair().initiatorSession
        val sessionAndMessage = AvroSealedClasses.SessionAndMessage.create(
            authenticatedSession,
            SESSION_ID,
            authenticatedEncryptedDataMessage
        )

        assertNull(sessionAndMessage)
        loggingInterceptor.assertSingleWarning(
            "Received encrypted message for session with SessionId = sessionId which is " +
                "authentication only. The message was discarded."
        )
    }

    @Test
    fun `Cannot create a SessionAndMessage with an AuthenticatedDataMessage and a AuthenticatedEncryptionSession`() {
        val authenticatedDataMessage = AvroSealedClasses.DataMessage.Authenticated(
            Mockito.mock(AuthenticatedDataMessage::class.java))
        val authenticatedEncryptionSession = createSessionPair(ProtocolMode.AUTHENTICATED_ENCRYPTION).initiatorSession
        val sessionAndMessage = AvroSealedClasses
            .SessionAndMessage
            .create(authenticatedEncryptionSession, SESSION_ID, authenticatedDataMessage)

        assertNull(sessionAndMessage)
        loggingInterceptor.assertSingleWarning(
            "Received unencrypted message for session with SessionId = sessionId which is " +
                    "authentication and encryption. The message was discarded."
        )
    }
}
