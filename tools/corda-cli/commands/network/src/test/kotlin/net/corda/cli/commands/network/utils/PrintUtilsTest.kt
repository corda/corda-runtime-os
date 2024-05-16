package net.corda.cli.commands.network.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.contains
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.cli.plugins.network.output.Output
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.security.cert.CertificateFactory

class PrintUtilsTest {

    @Suppress("MaxLineLength")
    companion object {
        // Includes new lines
        const val multiLineCert = """-----BEGIN CERTIFICATE-----
MIIFKTCCBBGgAwIBAgISBPBWAQX74sKyWaxrwN9Wyf/4MA0GCSqGSIb3DQEBCwUA
MDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD
EwJSMzAeFw0yMjA1MTMxMTE0NTlaFw0yMjA4MTExMTE0NThaMBQxEjAQBgNVBAMT
CWNvcmRhLm5ldDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMqmvfMO
na/+r0V3d3hpGPz5hesAAJRZjJCjsQr5ly8LodIfcPRSz+p5N8ui6ct8lyOmGLmi
VzKn6h+On4ilNnd2inIqBRcyFlU4YFyBqq9+FZdR64gEr2CVX8xDz5bMFymLZJoC
DnKgzq6LAvhQv/2NIkSRuLI09phKhMwQkAzFaOx0Q1kkmNnJYSf81dF1lbTVAAEH
sxMK+4dGECQCYFsfkrpk4wVBnaIdr7JLsrOHbbdLK8Ks/TxVNw20FOvuKZzR28lF
Z2roWY7S3s+x6mNZk4zhmTkBFXR747q7IVqj+Un3BU2G5/2TZ6LCJ+8m3WPD+9gz
MHdfNwDftNqTuMkCAwEAAaOCAlUwggJRMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUE
FjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQU
td2w7gkV6EYKUVTrXLPbKNpIc1UwHwYDVR0jBBgwFoAUFC6zF7dYVsuuUAlA5h+v
nYsUwsYwVQYIKwYBBQUHAQEESTBHMCEGCCsGAQUFBzABhhVodHRwOi8vcjMuby5s
ZW5jci5vcmcwIgYIKwYBBQUHMAKGFmh0dHA6Ly9yMy5pLmxlbmNyLm9yZy8wIwYD
VR0RBBwwGoIJY29yZGEubmV0gg13d3cuY29yZGEubmV0MEwGA1UdIARFMEMwCAYG
Z4EMAQIBMDcGCysGAQQBgt8TAQEBMCgwJgYIKwYBBQUHAgEWGmh0dHA6Ly9jcHMu
bGV0c2VuY3J5cHQub3JnMIIBBgYKKwYBBAHWeQIEAgSB9wSB9ADyAHcA36Veq2iC
Tx9sre64X04+WurNohKkal6OOxLAIERcKnMAAAGAvVf0zgAABAMASDBGAiEA7LTc
Kcc22HaRFQBqt5zCQjdUcuuZCzbDuhYfL7zbeW4CIQC/Jw3uq7nj1XjpPVb8amYO
ZBaIyLtqvfdLpnSvIe+NowB3ACl5vvCeOTkh8FZzn2Old+W+V32cYAr4+U1dJlwl
XceEAAABgL1X9L0AAAQDAEgwRgIhALp82uqQgsTTSGoQ44obZdgin8eLrUb0fnJX
uiOEjeIMAiEA4GM7LhToVLb7+EtEoCtkH7Mwr8rsmTV9oXYzjXuWUfQwDQYJKoZI
hvcNAQELBQADggEBAHMyXmq77uYcC/cvT1QFzZvjrohxeZQHzYWsIho6DfpS8RZd
N+O1sa4/tjMNN5XSrAY7YJczgBue13YH+Vw9k8hVqJ7vHKSbFbMrF03NgHLfM2rv
CHPCZCv3zqESdkcNaXNYDykcwpZjmUFV8T2gy8se+3FYfgiDr6lfpUIDF47EaD9S
IFv3D2+FNNS2VaC2U2Uta1XQkrdkUznq8A4rTY3RTTjlMhXf2OP19eUqsmFKF+5D
fMTdCNm5Klag/h/ogvYRXxYFvr+4l5hOzK1IJJWoftGi4s1f1pgv/sbi2DXKNPOP
7oKylBF5li7LtauuKA6rZM3S62LJvt/Y+d5mgaA=
-----END CERTIFICATE-----"""

        // Includes escaped new line character
        const val singleLineCert = "-----BEGIN CERTIFICATE-----\nMIIFKTCCBBGgAwIBAgISBPBWAQX74sKyWaxrwN9Wyf/4MA0GCSqGSIb3DQEBCwUA\nMDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD\nEwJSMzAeFw0yMjA1MTMxMTE0NTlaFw0yMjA4MTExMTE0NThaMBQxEjAQBgNVBAMT\nCWNvcmRhLm5ldDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMqmvfMO\nna/+r0V3d3hpGPz5hesAAJRZjJCjsQr5ly8LodIfcPRSz+p5N8ui6ct8lyOmGLmi\nVzKn6h+On4ilNnd2inIqBRcyFlU4YFyBqq9+FZdR64gEr2CVX8xDz5bMFymLZJoC\nDnKgzq6LAvhQv/2NIkSRuLI09phKhMwQkAzFaOx0Q1kkmNnJYSf81dF1lbTVAAEH\nsxMK+4dGECQCYFsfkrpk4wVBnaIdr7JLsrOHbbdLK8Ks/TxVNw20FOvuKZzR28lF\nZ2roWY7S3s+x6mNZk4zhmTkBFXR747q7IVqj+Un3BU2G5/2TZ6LCJ+8m3WPD+9gz\nMHdfNwDftNqTuMkCAwEAAaOCAlUwggJRMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUE\nFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQU\ntd2w7gkV6EYKUVTrXLPbKNpIc1UwHwYDVR0jBBgwFoAUFC6zF7dYVsuuUAlA5h+v\nnYsUwsYwVQYIKwYBBQUHAQEESTBHMCEGCCsGAQUFBzABhhVodHRwOi8vcjMuby5s\nZW5jci5vcmcwIgYIKwYBBQUHMAKGFmh0dHA6Ly9yMy5pLmxlbmNyLm9yZy8wIwYD\nVR0RBBwwGoIJY29yZGEubmV0gg13d3cuY29yZGEubmV0MEwGA1UdIARFMEMwCAYG\nZ4EMAQIBMDcGCysGAQQBgt8TAQEBMCgwJgYIKwYBBQUHAgEWGmh0dHA6Ly9jcHMu\nbGV0c2VuY3J5cHQub3JnMIIBBgYKKwYBBAHWeQIEAgSB9wSB9ADyAHcA36Veq2iC\nTx9sre64X04+WurNohKkal6OOxLAIERcKnMAAAGAvVf0zgAABAMASDBGAiEA7LTc\nKcc22HaRFQBqt5zCQjdUcuuZCzbDuhYfL7zbeW4CIQC/Jw3uq7nj1XjpPVb8amYO\nZBaIyLtqvfdLpnSvIe+NowB3ACl5vvCeOTkh8FZzn2Old+W+V32cYAr4+U1dJlwl\nXceEAAABgL1X9L0AAAQDAEgwRgIhALp82uqQgsTTSGoQ44obZdgin8eLrUb0fnJX\nuiOEjeIMAiEA4GM7LhToVLb7+EtEoCtkH7Mwr8rsmTV9oXYzjXuWUfQwDQYJKoZI\nhvcNAQELBQADggEBAHMyXmq77uYcC/cvT1QFzZvjrohxeZQHzYWsIho6DfpS8RZd\nN+O1sa4/tjMNN5XSrAY7YJczgBue13YH+Vw9k8hVqJ7vHKSbFbMrF03NgHLfM2rv\nCHPCZCv3zqESdkcNaXNYDykcwpZjmUFV8T2gy8se+3FYfgiDr6lfpUIDF47EaD9S\nIFv3D2+FNNS2VaC2U2Uta1XQkrdkUznq8A4rTY3RTTjlMhXf2OP19eUqsmFKF+5D\nfMTdCNm5Klag/h/ogvYRXxYFvr+4l5hOzK1IJJWoftGi4s1f1pgv/sbi2DXKNPOP\n7oKylBF5li7LtauuKA6rZM3S62LJvt/Y+d5mgaA=\n-----END CERTIFICATE-----\n"
        const val testObjectKey = "obj"
    }

    @Test
    fun `string containing single line certificate is not reformatted`() {
        PrintUtils.printJsonOutput(
            singleLineCert,
            AssertCertInJsonStringIsValidOutput(),
        )
    }

    @Test
    fun `string containing multiline certificate is not reformatted`() {
        PrintUtils.printJsonOutput(
            multiLineCert,
            AssertCertInJsonStringIsValidOutput(),
        )
    }

    @Test
    fun `json object with string containing single line certificate is not reformatted`() {
        PrintUtils.printJsonOutput(
            mapOf(testObjectKey to singleLineCert),
            AssertCertInJsonObjectIsValidOutput(),
        )
    }

    @Test
    fun `json object with string containing multiline certificate is not reformatted`() {
        PrintUtils.printJsonOutput(
            mapOf(testObjectKey to multiLineCert),
            AssertCertInJsonObjectIsValidOutput(),
        )
    }

    private inner class AssertCertInJsonStringIsValidOutput : Output {
        override fun generateOutput(content: String) {
            jacksonObjectMapper().readTree(content).apply {
                assertThat(isTextual).isTrue
                assertCertificateCanBeParsed(this)
            }
        }
    }

    private inner class AssertCertInJsonObjectIsValidOutput : Output {
        override fun generateOutput(content: String) {
            jacksonObjectMapper().readTree(content).apply {
                assertThat(contains(testObjectKey)).isTrue
                assertCertificateCanBeParsed(get(testObjectKey))
            }
        }
    }

    private fun assertCertificateCanBeParsed(jsonNode: JsonNode) {
        val pemCert = jsonNode.textValue()
        assertThat(pemCert).contains("\n")
        assertDoesNotThrow {
            CertificateFactory.getInstance("X.509").generateCertificate(pemCert.byteInputStream())
        }
    }
}
