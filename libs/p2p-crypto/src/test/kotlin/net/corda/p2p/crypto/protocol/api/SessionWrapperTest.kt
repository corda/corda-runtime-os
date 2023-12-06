package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.crypto.protocol.AuthenticatedEncryptionSessionDetails
import net.corda.data.p2p.crypto.protocol.AuthenticatedSessionDetails
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.Security

class SessionWrapperTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun `wrap will return AuthenticatedEncryptionSession when needed`() {
        val session = mock<Session> {
            on { details } doReturn mock<AuthenticatedEncryptionSessionDetails>()
        }

        val wrapper = SessionWrapper.wrap(session)

        assertThat(wrapper).isInstanceOf(AuthenticatedEncryptionSession::class.java)
    }

    @Test
    fun `wrap will return AuthenticatedSession when needed`() {
        val session = mock<Session> {
            on { details } doReturn mock<AuthenticatedSessionDetails>()
        }

        val wrapper = SessionWrapper.wrap(session)

        assertThat(wrapper).isInstanceOf(AuthenticatedSession::class.java)
    }

    @Test
    fun `wrap will throw an exception when details type is unexpected`() {
        val session = mock<Session> {
            on { details } doReturn 12
        }

        assertThrows<CordaRuntimeException> {
            SessionWrapper.wrap(session)
        }
    }
}