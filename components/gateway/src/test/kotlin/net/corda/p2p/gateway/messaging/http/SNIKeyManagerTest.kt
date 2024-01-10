package net.corda.p2p.gateway.messaging.http

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

class SNIKeyManagerTest {
    private val keyType = "type"
    private val aliasNop = "alias_nop"
    private val aliasC4 = "alias_c4"
    private val aliasC5 = "alias_c5"

    private val keyManager = mock<X509ExtendedKeyManager>()
    private val matcher = mock<HostnameMatcher> {
        on { aliasMatch(aliasNop) } doReturn HostnameMatcher.MatchType.NONE
        on { aliasMatch(aliasC4) } doReturn HostnameMatcher.MatchType.C4
        on { aliasMatch(aliasC5) } doReturn HostnameMatcher.MatchType.C5
    }
    private val sslParameters = mock<SSLParameters> {
        on { sniMatchers } doReturn listOf(matcher)
    }
    private val issuer = mock<X500Principal>()
    private val engine = mock<SSLEngine> {
        on { sslParameters } doReturn sslParameters
    }
    private val socket = mock<SSLSocket> {
        on { sslParameters } doReturn sslParameters
    }
    private val manager = SNIKeyManager(keyManager)

    @Test
    fun `null aliases return null`() {
        whenever(keyManager.getServerAliases(any(), any())).doReturn(null)

        val alias = manager.chooseEngineServerAlias(keyType, arrayOf(issuer), engine)

        assertThat(alias).isNull()
    }

    @Test
    fun `empty aliases return null`() {
        whenever(keyManager.getServerAliases(any(), any())).doReturn(arrayOf())

        val alias = manager.chooseServerAlias(keyType, arrayOf(issuer), socket)

        assertThat(alias).isNull()
    }

    @Test
    fun `Corda 4 will return corda 4`() {
        whenever(keyManager.getServerAliases(keyType, null)).doReturn(
            arrayOf(
                aliasC4,
            )
        )
        val alias = manager.chooseServerAlias(keyType, arrayOf(issuer), socket)

        assertThat(alias).isEqualTo(aliasC4)
    }

    @Test
    fun `unmatched alias will be ignored`() {
        whenever(keyManager.getServerAliases(keyType, arrayOf(issuer))).doReturn(
            arrayOf(
                aliasNop,
            )
        )
        val alias = manager.chooseServerAlias(keyType, arrayOf(issuer), socket)

        assertThat(alias).isNull()
    }

    @Test
    fun `the first passed alias will be returned`() {
        whenever(keyManager.getServerAliases(keyType, null)).doReturn(
            arrayOf(
                aliasNop,
                aliasNop,
                aliasC4,
            )
        )
        val alias = manager.chooseServerAlias(keyType, arrayOf(issuer), socket)

        assertThat(alias).isEqualTo(aliasC4)
    }

    @Test
    fun `no issuers returns alias`() {
        whenever(keyManager.getServerAliases(keyType, null)).doReturn(arrayOf(aliasC5))
        val alias = manager.chooseEngineServerAlias(keyType, null, engine)

        assertThat(alias).isEqualTo(aliasC5)
    }

    @Test
    fun `issuers returns alias if issuer match`() {
        val certificate = mock<X509Certificate> {
            on { issuerX500Principal } doReturn issuer
        }
        whenever(keyManager.getCertificateChain(aliasC5)).doReturn(arrayOf(certificate))
        whenever(keyManager.getServerAliases(keyType, null)).doReturn(arrayOf(aliasC5))

        val alias = manager.chooseEngineServerAlias(keyType, arrayOf(issuer), engine)

        assertThat(alias).isEqualTo(aliasC5)
    }

    @Test
    fun `issuers returns alias null issuer will not match`() {
        val certificate = mock<X509Certificate> {
            on { issuerX500Principal } doReturn mock()
        }
        whenever(keyManager.getCertificateChain(aliasC5)).doReturn(arrayOf(certificate))
        whenever(keyManager.getServerAliases(keyType, arrayOf(issuer))).doReturn(arrayOf(aliasC5))

        val alias = manager.chooseEngineServerAlias(keyType, arrayOf(issuer), engine)

        assertThat(alias).isNull()
    }

    @Test
    fun `issuers returns null if certificate has no issuer match`() {
        val certificate = mock<X509Certificate> {
            on { issuerX500Principal } doReturn null
        }
        whenever(keyManager.getCertificateChain(aliasC5)).doReturn(arrayOf(certificate))
        whenever(keyManager.getServerAliases(keyType, arrayOf(issuer))).doReturn(arrayOf(aliasC5))

        val alias = manager.chooseEngineServerAlias(keyType, arrayOf(issuer), engine)

        assertThat(alias).isNull()
    }
}
