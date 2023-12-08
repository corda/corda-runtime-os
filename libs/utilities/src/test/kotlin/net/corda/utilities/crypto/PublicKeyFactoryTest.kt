package net.corda.utilities.crypto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PublicKeyFactoryTest {
    private companion object {
        const val VALID_KEY_PEM = """
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7fFAKpIU1LlAf7S2n4847JcqvgaT
rVwmQ9GmruPVpC2wEPpFggbVL3/mtm61qoCi81hphd0W9yVhSVwVT0wQHw==
-----END PUBLIC KEY-----
        """

        const val CERTIFICATE_PEM = """
-----BEGIN CERTIFICATE-----
MIIBSTCB8aADAgECAgECMAoGCCqGSM49BAMCMB4xCzAJBgNVBAYTAlVLMQ8wDQYD
VQQDDAZyMy5jb20wHhcNMjMwNDA0MDkwMjA1WhcNMjMwNTA0MDkwMjA1WjAeMQsw
CQYDVQQGEwJVSzEPMA0GA1UEAwwGcjMuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0D
AQcDQgAE8kG/7nCMi4vlsOMByRqIEZ85/guWJ3FVLli4xzhfm6oBwwonvgWYpxCl
BFeouJ/6fnb/LW8in9XdShP/kXrL+KMgMB4wDwYDVR0TAQH/BAUwAwEB/zALBgNV
HQ8EBAMCAa4wCgYIKoZIzj0EAwIDRwAwRAIgaxHWXC7NXtd4XHPRgLqV+UcNS7NW
ORC/w+12HlGG968CICBvpZAN2HHIlo2Vmgak+avL2zdIK6LQo0nXuY+4e0KT
-----END CERTIFICATE-----
        """
    }

    @Test
    fun `factory create a public key from a PEM source`() {
        val key = publicKeyFactory(VALID_KEY_PEM.reader())

        assertThat(key?.algorithm).isEqualTo("EC")
    }

    @Test
    fun `factory return null for non public key source`() {
        val key = publicKeyFactory(CERTIFICATE_PEM.reader())

        assertThat(key).isNull()
    }

    @Test
    fun `toPem return a valid PEM`() {
        val key = publicKeyFactory(VALID_KEY_PEM.reader())

        assertThat(key?.toPem()?.trim()).isEqualTo(VALID_KEY_PEM.trim())
    }
}
