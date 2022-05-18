package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

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
            AvroSealedClasses.DataMessage.AuthenticatedAndEncrypted(mock())
        val authenticatedSession = mock<AuthenticatedSession>()
        val sessionAndMessage = AvroSealedClasses.SessionAndMessage.create(
            authenticatedSession,
            SESSION_ID,
            authenticatedEncryptedDataMessage
        )

        assertThat(sessionAndMessage).isNull()
        loggingInterceptor.assertSingleWarning(
            "Received encrypted message for session with SessionId = sessionId which is " +
                "authentication only. The message was discarded."
        )
    }

    @Test
    fun `Cannot create a SessionAndMessage with an AuthenticatedDataMessage and a AuthenticatedEncryptionSession`() {
        val authenticatedDataMessage = AvroSealedClasses.DataMessage.Authenticated(
            mock())
        val authenticatedEncryptionSession = mock<AuthenticatedEncryptionSession>()
        val sessionAndMessage = AvroSealedClasses
            .SessionAndMessage
            .create(authenticatedEncryptionSession, SESSION_ID, authenticatedDataMessage)

        assertThat(sessionAndMessage).isNull()
        loggingInterceptor.assertSingleWarning(
            "Received unencrypted message for session with SessionId = sessionId which is " +
                    "authentication and encryption. The message was discarded."
        )
    }
}
