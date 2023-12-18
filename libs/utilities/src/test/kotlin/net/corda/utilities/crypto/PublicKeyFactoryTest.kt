package net.corda.utilities.crypto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PublicKeyFactoryTest {
    private companion object {
        private val VALID_KEY_PEM = """
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7fFAKpIU1LlAf7S2n4847JcqvgaT
rVwmQ9GmruPVpC2wEPpFggbVL3/mtm61qoCi81hphd0W9yVhSVwVT0wQHw==
-----END PUBLIC KEY-----
        """.replace("\r", "")
            .replace("\n", System.lineSeparator())

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

        const val PRIVATE_KEY_PEM = """
-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCV++ivLWZQi2yc
ZjjD1q6+J5eyy0t+urYKZE4NfcHIfX9Yg0OxXJ5LNpHP5v/WCTy3QRVOLZbUCsy4
lzZkvRMv6YkL6hgahMl3fn61tksVKBhvcxrs/yBj62ut5uGmxBEfPNeZdFC1BPr/
czGn8z8zQz9biiy3U9uX5d+at7h7KZIQH3AfReAI3ZcHv7RpEmur9q4EQUNcSHje
mQkNk9vVQYauAMUbAS/JfogNWYoPRIwALX/hxXEE4dhqr1JAZET5b6SDNvi3cruG
LdJNvKFawpY6mZIILR5CW9UXBT1OVknr8qujqBfju5qjebPPR2wRs98wJ9P1PIvW
wp1+cBXjAgMBAAECggEAAp2v1MbtuUah14sSUWQgHx9e8ujjipAbRJWPt8+Fo2Lu
sHe+yXCnwUnwKCGChh2H2BKxvu/UMiNQFYGQiIX9uC6SXiTCnZRdeTVwiQywRP5E
0taG6A6ZHT4ulPn7lJQfBubz+t4kIxVHp73109TgXfaZO80AqoR8RmIKGgY7T5IG
tcq4oDCF/x8jWByBd0lKD3SA6Mb6e/977bGcjaU8/97ZiXHUXMuiT1XZC4VAyVCj
d1MhpNlAE9vm8qVxSCceqUwUPOkrChYIYhP/1Ph6xI+sPVLFWsQMOOyD9crIH5lf
hx1GokGoBr03db8QL+xDatrPD0MUlQrKWfiHc9lPWQKBgQDIVlfiwN4576v3J0uX
3QuCeJPWIPv3FYVUNBBGBDMgLEc6JZacRVjwCIqhlNKPwo3/xYFonLJEsV61qCtA
0T+2iMWM6a8wifhxWVSbuO72SKFFa3tr5f07IEAXt3TOXAQ+I27OBtmN1gEIUlBm
jY2gX3KbWh3ICOQLjBtb5oZQmwKBgQC/qAWv4NfPYtyj3j9iUxGJCTg93qgd6Aj8
kd+21lyzio2evVCPPENEtCLEK7T24Iazp24T2da0e/ULsoLuswYr9xTVEgp042lS
5pWAhrAyRLGnOciOojuhtjw9WCra+BBuDj3zgVKs1qkjYtlMt0hlj55nF/apPgXe
U9STr3MwWQKBgDoYd+IE5izM6QSCO7StYFIUn2KALDFJ52E0u+dh/mV9Eaa2Ecf9
gD4TbAhRfQI9fCpI3y80CapL+VgajXh9dcl8PjWP6sLbr9VG/3ctGPcItuPHuIHg
rx+/SXbXN6NEIeLXKaHLcLll7uS34iLrN7/jDfwpFOvWUDNdDO4ImrizAoGAQbbT
1F1MwfAM5ScEJquC3LDLlvMsQ6zWv/socOGJQYplSqNw5tvr2LfHH+o4j1mm9hyX
crkDlDjYaZ1YpY2+tP5wJyqbaT68U096vWnxKhtBqqv2Z2ma9rSsbQA5GYFq6MF7
Xm3eMiMcNWTkSxig+ynGT2T5D0iT7Ipj3guPfGkCgYEAhq1NlaRfJSd+abF/IoHO
eM3B4oa4aQF6ef6rOIiyjf5UQuicIljizg6e+VwX/eHxBj4BjCvwMVo2UUXowoou
w04JkMaEhKtP9jyoyIkG+wWkebCzTO76CZDOTXmU9yrTJtIot8Fj3FJZNIMjA+ro
psWkUjFguCBSXoZKJcsMeQI=
-----END PRIVATE KEY-----
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
    fun `toPem return a valid public key PEM`() {
        val key = publicKeyFactory(VALID_KEY_PEM.reader())

        assertThat(key?.toPem()?.trim()).isEqualTo(VALID_KEY_PEM.trim())
    }

    @Test
    fun `factory create a private key from a PEM source`() {
        val key = privateKeyFactory(PRIVATE_KEY_PEM.reader())

        assertThat(key?.algorithm).isEqualTo("RSA")
    }

    @Test
    fun `factory return null for non private key source`() {
        val key = privateKeyFactory(CERTIFICATE_PEM.reader())

        assertThat(key).isNull()
    }
}
