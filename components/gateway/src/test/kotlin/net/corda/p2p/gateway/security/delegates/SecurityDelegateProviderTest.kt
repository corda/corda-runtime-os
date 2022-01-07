package net.corda.p2p.gateway.security.delegates

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.Security
import java.security.Signature

class SecurityDelegateProviderTest {
    @Test
    fun `init add the SecurityDelegateProvider to the providers`() {
        SecurityDelegateProvider.size

        assertThat(Security.getProviders()[0]).isEqualTo(SecurityDelegateProvider)
    }

    @Test
    fun `init add the key store delegate service`() {
        assertThat(
            SecurityDelegateProvider.services
                .map {
                    it.type to it.algorithm
                }
        ).contains("KeyStore" to "CordaDelegateKeyStore")
    }

    @Test
    fun `init add RSA signature service`() {
        assertThat(
            SecurityDelegateProvider.services
                .map {
                    it.type to it.algorithm
                }
        ).contains("Signature" to "RSASSA-PSS")
    }

    @Test
    fun `init add EC signature service`() {
        assertThat(
            SecurityDelegateProvider.services
                .map {
                    it.type to it.algorithm
                }
        ).containsAll(
            SigningService.Hash.values().map {
                "Signature" to it.ecName
            }
        )
    }

    @Test
    fun `createKeyStore create a keystore with the requested service`() {
        val alias = mock<SigningService.Alias>() {
            on { name } doReturn "aliasName"
        }
        val service = mock<SigningService> {
            on { aliases } doReturn listOf(alias)
        }

        val keyStore = SecurityDelegateProvider.createKeyStore(service)

        assertThat(keyStore.aliases().toList()).contains("aliasName")
    }

    @Test
    fun `newInstance of DelegateSignatureService creates DelegatedSignature`() {
        SecurityDelegateProvider.size

        val signature = Signature.getInstance(SigningService.Hash.SHA384.ecName)

        assertThat(signature.provider).isEqualTo(SecurityDelegateProvider)
    }
}
