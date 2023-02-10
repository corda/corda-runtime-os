package net.corda.membership.lib.impl.grouppolicy.v1

import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.PROTOCOL_MODE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.SESSION_KEY_POLICY
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.StaticNetwork.MEMBERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.CIPHER_SUITE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.P2P_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.REGISTRATION_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import net.corda.membership.lib.impl.grouppolicy.getBadCertError
import net.corda.membership.lib.impl.grouppolicy.getBadEnumError
import net.corda.membership.lib.impl.grouppolicy.getBlankValueError
import net.corda.membership.lib.impl.grouppolicy.getMissingCertError
import net.corda.membership.lib.impl.grouppolicy.getMissingKeyError
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class InteropGroupPolicyImplTest {

    companion object {
        const val EMPTY_STRING = ""
        const val WHITESPACE_STRING = "   "
        const val BAD_ENUM_VALUE = "BAD_VALUE_99&!!"
    }

    @Nested
    inner class StaticNetworkPolicyTests {
        @Test
        fun `complete static network group policy can be parsed correctly`() {
            val groupPolicy: GroupPolicy = assertDoesNotThrow {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        mgmInfoOverride = null
                    )
                )
            }

            assertSoftly {
                it.assertThat(groupPolicy.fileFormatVersion).isEqualTo(1)
                it.assertThat(groupPolicy.groupId).isEqualTo("INTEROP")
                it.assertThat(groupPolicy.registrationProtocol).isEqualTo("net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService")
                it.assertThat(groupPolicy.synchronisationProtocol).isEqualTo("net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService")

                it.assertThat(groupPolicy.protocolParameters.sessionKeyPolicy).isEqualTo(COMBINED)
//                it.assertThat(groupPolicy.protocolParameters.staticNetworkMembers)
//                    .isNotNull
//                    .isNotEmpty
//                    .hasSize(2)
//                    .containsExactly(
//                        mapOf(TEST_STATIC_MEMBER_KEY to TEST_STATIC_MEMBER_VALUE),
//                        mapOf(TEST_STATIC_MEMBER_KEY to TEST_STATIC_MEMBER_VALUE)
//                    )

                it.assertThat(groupPolicy.p2pParameters.sessionPki).isEqualTo(SessionPkiMode.STANDARD)
                it.assertThat(groupPolicy.p2pParameters.sessionTrustRoots)
                    .isNotNull
                    .isNotEmpty
                    .hasSize(1)
                    .isEqualTo("      \"-----BEGIN CERTIFICATE-----\\r\\nMIIFKTCCBBGgAwIBAgISBPBWAQX74sKyWaxrwN9Wyf/4MA0GCSqGSIb3DQEBCwUA\\r\\nMDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD\\r\\nEwJSMzAeFw0yMjA1MTMxMTE0NTlaFw0yMjA4MTExMTE0NThaMBQxEjAQBgNVBAMT\\r\\nCWNvcmRhLm5ldDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMqmvfMO\\r\\nna/+r0V3d3hpGPz5hesAAJRZjJCjsQr5ly8LodIfcPRSz+p5N8ui6ct8lyOmGLmi\\r\\nVzKn6h+On4ilNnd2inIqBRcyFlU4YFyBqq9+FZdR64gEr2CVX8xDz5bMFymLZJoC\\r\\nDnKgzq6LAvhQv/2NIkSRuLI09phKhMwQkAzFaOx0Q1kkmNnJYSf81dF1lbTVAAEH\\r\\nsxMK+4dGECQCYFsfkrpk4wVBnaIdr7JLsrOHbbdLK8Ks/TxVNw20FOvuKZzR28lF\\r\\nZ2roWY7S3s+x6mNZk4zhmTkBFXR747q7IVqj+Un3BU2G5/2TZ6LCJ+8m3WPD+9gz\\r\\nMHdfNwDftNqTuMkCAwEAAaOCAlUwggJRMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUE\\r\\nFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQU\\r\\ntd2w7gkV6EYKUVTrXLPbKNpIc1UwHwYDVR0jBBgwFoAUFC6zF7dYVsuuUAlA5h+v\\r\\nnYsUwsYwVQYIKwYBBQUHAQEESTBHMCEGCCsGAQUFBzABhhVodHRwOi8vcjMuby5s\\r\\nZW5jci5vcmcwIgYIKwYBBQUHMAKGFmh0dHA6Ly9yMy5pLmxlbmNyLm9yZy8wIwYD\\r\\nVR0RBBwwGoIJY29yZGEubmV0gg13d3cuY29yZGEubmV0MEwGA1UdIARFMEMwCAYG\\r\\nZ4EMAQIBMDcGCysGAQQBgt8TAQEBMCgwJgYIKwYBBQUHAgEWGmh0dHA6Ly9jcHMu\\r\\nbGV0c2VuY3J5cHQub3JnMIIBBgYKKwYBBAHWeQIEAgSB9wSB9ADyAHcA36Veq2iC\\r\\nTx9sre64X04+WurNohKkal6OOxLAIERcKnMAAAGAvVf0zgAABAMASDBGAiEA7LTc\\r\\nKcc22HaRFQBqt5zCQjdUcuuZCzbDuhYfL7zbeW4CIQC/Jw3uq7nj1XjpPVb8amYO\\r\\nZBaIyLtqvfdLpnSvIe+NowB3ACl5vvCeOTkh8FZzn2Old+W+V32cYAr4+U1dJlwl\\r\\nXceEAAABgL1X9L0AAAQDAEgwRgIhALp82uqQgsTTSGoQ44obZdgin8eLrUb0fnJX\\r\\nuiOEjeIMAiEA4GM7LhToVLb7+EtEoCtkH7Mwr8rsmTV9oXYzjXuWUfQwDQYJKoZI\\r\\nhvcNAQELBQADggEBAHMyXmq77uYcC/cvT1QFzZvjrohxeZQHzYWsIho6DfpS8RZd\\r\\nN+O1sa4/tjMNN5XSrAY7YJczgBue13YH+Vw9k8hVqJ7vHKSbFbMrF03NgHLfM2rv\\r\\nCHPCZCv3zqESdkcNaXNYDykcwpZjmUFV8T2gy8se+3FYfgiDr6lfpUIDF47EaD9S\\r\\nIFv3D2+FNNS2VaC2U2Uta1XQkrdkUznq8A4rTY3RTTjlMhXf2OP19eUqsmFKF+5D\\r\\nfMTdCNm5Klag/h/ogvYRXxYFvr+4l5hOzK1IJJWoftGi4s1f1pgv/sbi2DXKNPOP\\r\\n7oKylBF5li7LtauuKA6rZM3S62LJvt/Y+d5mgaA=\\r\\n-----END CERTIFICATE-----\\r\\n\",\n" +
                            "      \"-----BEGIN CERTIFICATE-----\\r\\nMIIFFjCCAv6gAwIBAgIRAJErCErPDBinU/bWLiWnX1owDQYJKoZIhvcNAQELBQAw\\r\\nTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\\r\\ncmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjAwOTA0MDAwMDAw\\r\\nWhcNMjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\\r\\nRW5jcnlwdDELMAkGA1UEAxMCUjMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\\r\\nAoIBAQC7AhUozPaglNMPEuyNVZLD+ILxmaZ6QoinXSaqtSu5xUyxr45r+XXIo9cP\\r\\nR5QUVTVXjJ6oojkZ9YI8QqlObvU7wy7bjcCwXPNZOOftz2nwWgsbvsCUJCWH+jdx\\r\\nsxPnHKzhm+/b5DtFUkWWqcFTzjTIUu61ru2P3mBw4qVUq7ZtDpelQDRrK9O8Zutm\\r\\nNHz6a4uPVymZ+DAXXbpyb/uBxa3Shlg9F8fnCbvxK/eG3MHacV3URuPMrSXBiLxg\\r\\nZ3Vms/EY96Jc5lP/Ooi2R6X/ExjqmAl3P51T+c8B5fWmcBcUr2Ok/5mzk53cU6cG\\r\\n/kiFHaFpriV1uxPMUgP17VGhi9sVAgMBAAGjggEIMIIBBDAOBgNVHQ8BAf8EBAMC\\r\\nAYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMBMBIGA1UdEwEB/wQIMAYB\\r\\nAf8CAQAwHQYDVR0OBBYEFBQusxe3WFbLrlAJQOYfr52LFMLGMB8GA1UdIwQYMBaA\\r\\nFHm0WeZ7tuXkAXOACIjIGlj26ZtuMDIGCCsGAQUFBwEBBCYwJDAiBggrBgEFBQcw\\r\\nAoYWaHR0cDovL3gxLmkubGVuY3Iub3JnLzAnBgNVHR8EIDAeMBygGqAYhhZodHRw\\r\\nOi8veDEuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYGZ4EMAQIBMA0GCysGAQQB\\r\\ngt8TAQEBMA0GCSqGSIb3DQEBCwUAA4ICAQCFyk5HPqP3hUSFvNVneLKYY611TR6W\\r\\nPTNlclQtgaDqw+34IL9fzLdwALduO/ZelN7kIJ+m74uyA+eitRY8kc607TkC53wl\\r\\nikfmZW4/RvTZ8M6UK+5UzhK8jCdLuMGYL6KvzXGRSgi3yLgjewQtCPkIVz6D2QQz\\r\\nCkcheAmCJ8MqyJu5zlzyZMjAvnnAT45tRAxekrsu94sQ4egdRCnbWSDtY7kh+BIm\\r\\nlJNXoB1lBMEKIq4QDUOXoRgffuDghje1WrG9ML+Hbisq/yFOGwXD9RiX8F6sw6W4\\r\\navAuvDszue5L3sz85K+EC4Y/wFVDNvZo4TYXao6Z0f+lQKc0t8DQYzk1OXVu8rp2\\r\\nyJMC6alLbBfODALZvYH7n7do1AZls4I9d1P4jnkDrQoxB3UqQ9hVl3LEKQ73xF1O\\r\\nyK5GhDDX8oVfGKF5u+decIsH4YaTw7mP3GFxJSqv3+0lUFJoi5Lc5da149p90Ids\\r\\nhCExroL1+7mryIkXPeFM5TgO9r0rvZaBFOvV2z0gp35Z0+L4WPlbuEjN/lxPFin+\\r\\nHlUjr8gRsI3qfJOQFy/9rKIJR0Y/8Omwt/8oTWgy1mdeHmmjk7j1nYsvC9JSQ6Zv\\r\\nMldlTTKB3zhThV1+XWYp6rjd5JW1zbVWEkLNxE7GJThEUG3szgBVGP7pSWTUTsqX\\r\\nnLRbwHOoq7hHwg==\\r\\n-----END CERTIFICATE-----\\r\\n\",\n" +
                            "      \"-----BEGIN CERTIFICATE-----\\r\\nMIIFYDCCBEigAwIBAgIQQAF3ITfU6UK47naqPGQKtzANBgkqhkiG9w0BAQsFADA/\\r\\nMSQwIgYDVQQKExtEaWdpdGFsIFNpZ25hdHVyZSBUcnVzdCBDby4xFzAVBgNVBAMT\\r\\nDkRTVCBSb290IENBIFgzMB4XDTIxMDEyMDE5MTQwM1oXDTI0MDkzMDE4MTQwM1ow\\r\\nTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\\r\\ncmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwggIiMA0GCSqGSIb3DQEB\\r\\nAQUAA4ICDwAwggIKAoICAQCt6CRz9BQ385ueK1coHIe+3LffOJCMbjzmV6B493XC\\r\\nov71am72AE8o295ohmxEk7axY/0UEmu/H9LqMZshftEzPLpI9d1537O4/xLxIZpL\\r\\nwYqGcWlKZmZsj348cL+tKSIG8+TA5oCu4kuPt5l+lAOf00eXfJlII1PoOK5PCm+D\\r\\nLtFJV4yAdLbaL9A4jXsDcCEbdfIwPPqPrt3aY6vrFk/CjhFLfs8L6P+1dy70sntK\\r\\n4EwSJQxwjQMpoOFTJOwT2e4ZvxCzSow/iaNhUd6shweU9GNx7C7ib1uYgeGJXDR5\\r\\nbHbvO5BieebbpJovJsXQEOEO3tkQjhb7t/eo98flAgeYjzYIlefiN5YNNnWe+w5y\\r\\nsR2bvAP5SQXYgd0FtCrWQemsAXaVCg/Y39W9Eh81LygXbNKYwagJZHduRze6zqxZ\\r\\nXmidf3LWicUGQSk+WT7dJvUkyRGnWqNMQB9GoZm1pzpRboY7nn1ypxIFeFntPlF4\\r\\nFQsDj43QLwWyPntKHEtzBRL8xurgUBN8Q5N0s8p0544fAQjQMNRbcTa0B7rBMDBc\\r\\nSLeCO5imfWCKoqMpgsy6vYMEG6KDA0Gh1gXxG8K28Kh8hjtGqEgqiNx2mna/H2ql\\r\\nPRmP6zjzZN7IKw0KKP/32+IVQtQi0Cdd4Xn+GOdwiK1O5tmLOsbdJ1Fu/7xk9TND\\r\\nTwIDAQABo4IBRjCCAUIwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYw\\r\\nSwYIKwYBBQUHAQEEPzA9MDsGCCsGAQUFBzAChi9odHRwOi8vYXBwcy5pZGVudHJ1\\r\\nc3QuY29tL3Jvb3RzL2RzdHJvb3RjYXgzLnA3YzAfBgNVHSMEGDAWgBTEp7Gkeyxx\\r\\n+tvhS5B1/8QVYIWJEDBUBgNVHSAETTBLMAgGBmeBDAECATA/BgsrBgEEAYLfEwEB\\r\\nATAwMC4GCCsGAQUFBwIBFiJodHRwOi8vY3BzLnJvb3QteDEubGV0c2VuY3J5cHQu\\r\\nb3JnMDwGA1UdHwQ1MDMwMaAvoC2GK2h0dHA6Ly9jcmwuaWRlbnRydXN0LmNvbS9E\\r\\nU1RST09UQ0FYM0NSTC5jcmwwHQYDVR0OBBYEFHm0WeZ7tuXkAXOACIjIGlj26Ztu\\r\\nMA0GCSqGSIb3DQEBCwUAA4IBAQAKcwBslm7/DlLQrt2M51oGrS+o44+/yQoDFVDC\\r\\n5WxCu2+b9LRPwkSICHXM6webFGJueN7sJ7o5XPWioW5WlHAQU7G75K/QosMrAdSW\\r\\n9MUgNTP52GE24HGNtLi1qoJFlcDyqSMo59ahy2cI2qBDLKobkx/J3vWraV0T9VuG\\r\\nWCLKTVXkcGdtwlfFRjlBz4pYg1htmf5X6DYO8A4jqv2Il9DjXA6USbW1FzXSLr9O\\r\\nhe8Y4IWS6wY7bCkjCWDcRQJMEhg76fsO3txE+FiYruq9RUWhiF1myv4Q6W+CyBFC\\r\\nDfvp7OOGAN6dEOM4+qR9sdjoSYKEBpsr6GtPAQw4dy753ec5\\r\\n-----END CERTIFICATE-----\\r\\n\"")
                it.assertThat(groupPolicy.p2pParameters.tlsTrustRoots)
                    .isNotEmpty
                    .hasSize(1)
                it.assertThat(groupPolicy.p2pParameters.tlsPki).isEqualTo(TlsPkiMode.STANDARD)
                it.assertThat(groupPolicy.p2pParameters.tlsVersion).isEqualTo(VERSION_1_3)
                it.assertThat(groupPolicy.p2pParameters.protocolMode).isEqualTo(AUTH_ENCRYPT)

                it.assertThat(groupPolicy.mgmInfo).isNull()

                it.assertThat(groupPolicy.cipherSuite.entries)
                    .isNotEmpty
                    .hasSize(6)
                    .contains(
                        entry("corda.provider", "default"),
                        entry("corda.signature.provider", "default"),
                        entry("corda.signature.default", "ECDSA_SECP256K1_SHA256"),
                        entry("corda.signature.FRESH_KEYS", "ECDSA_SECP256K1_SHA256"),
                        entry("corda.digest.default", "SHA256"),
                        entry("corda.cryptoservice.provider", "default")
                    )
            }
        }

        @Test
        fun `session trust roots can be null if pki mode is NoPKI`() {
            assertDoesNotThrow {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = SessionPkiMode.NO_PKI.toString(),
                            sessionTrustRootOverride = null
                        ),
                        mgmInfoOverride = null
                    )
                )
            }
        }
    }

    @Nested
    inner class ErrorTests {
        @Test
        fun `file format version must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(fileFormatVersionOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(FILE_FORMAT_VERSION))
        }

        @Test
        fun `group policy ID must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(groupIdOverride = EMPTY_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(GROUP_ID))
        }

        @Test
        fun `group policy ID must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(groupIdOverride = WHITESPACE_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(GROUP_ID))
        }

        @Test
        fun `group policy ID must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(groupIdOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(GROUP_ID))
        }

        @Test
        fun `registration protocol must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(registrationProtocolOverride = EMPTY_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(REGISTRATION_PROTOCOL))
        }

        @Test
        fun `registration protocol must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(registrationProtocolOverride = WHITESPACE_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(REGISTRATION_PROTOCOL))
        }

        @Test
        fun `registration protocol must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(registrationProtocolOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(REGISTRATION_PROTOCOL))
        }

        @Test
        fun `sync protocol must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(syncProtocolOverride = EMPTY_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SYNC_PROTOCOL))
        }

        @Test
        fun `sync protocol must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(syncProtocolOverride = WHITESPACE_STRING))
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SYNC_PROTOCOL))
        }

        @Test
        fun `sync protocol must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(syncProtocolOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(SYNC_PROTOCOL))
        }

        @Test
        fun `protocol parameters must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(protocolParametersOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(PROTOCOL_PARAMETERS))
        }

        @Test
        fun `session key policy must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            sessionKeyPolicyOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(SESSION_KEY_POLICY))
        }

        @Test
        fun `session key policy must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            sessionKeyPolicyOverride = EMPTY_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SESSION_KEY_POLICY))
        }

        @Test
        fun `session key policy must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            sessionKeyPolicyOverride = WHITESPACE_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SESSION_KEY_POLICY))
        }

        @Test
        fun `session key policy must match enum`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            sessionKeyPolicyOverride = BAD_ENUM_VALUE
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadEnumError(SESSION_KEY_POLICY, BAD_ENUM_VALUE))
        }

        @Test
        fun `static member list must be present if static network is present`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        protocolParametersOverride = buildProtocolParameters(
                            staticNetworkOverride = buildStaticMemberTemplate(null)
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(MEMBERS))
        }

        @Test
        fun `p2p parameters must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(p2pParametersOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(P2P_PARAMETERS))
        }

        @Test
        fun `session pki must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(SESSION_PKI))
        }

        @Test
        fun `session pki must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = EMPTY_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SESSION_PKI))
        }

        @Test
        fun `session pki must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = WHITESPACE_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(SESSION_PKI))
        }

        @Test
        fun `session pki must match enum`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionPkiOverride = BAD_ENUM_VALUE
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadEnumError(SESSION_PKI, BAD_ENUM_VALUE))
        }

        @Test
        fun `session trust roots must be non null if pki mode is not NoPKI`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionTrustRootOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(SESSION_TRUST_ROOTS))
        }

        @Test
        fun `session trust roots must contain at least one cert if pki mode is not NoPKI`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionTrustRootOverride = emptyList()
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingCertError(SESSION_TRUST_ROOTS))
        }

        @Test
        fun `session trust roots must contain valid certificates`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionTrustRootOverride = listOf(UNPARSEABLE_CERT)
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadCertError(SESSION_TRUST_ROOTS, 0))
        }

        @Test
        fun `session trust roots with a mix of valid and invalid certificates fails`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            sessionTrustRootOverride = listOf(
                                R3_COM_CERT,
                                UNPARSEABLE_CERT
                            )
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadCertError(SESSION_TRUST_ROOTS, 1))
        }

        @Test
        fun `tls trust roots must be non null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsTrustRootOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(TLS_TRUST_ROOTS))
        }

        @Test
        fun `tls trust roots must contain at least one cert`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsTrustRootOverride = emptyList()
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingCertError(TLS_TRUST_ROOTS))
        }

        @Test
        fun `tls trust roots must contain valid certificates`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsTrustRootOverride = listOf(UNPARSEABLE_CERT)
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadCertError(TLS_TRUST_ROOTS, 0))
        }

        @Test
        fun `tls trust roots with a mix of valid and invalid certificates fails`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsTrustRootOverride = listOf(
                                R3_COM_CERT,
                                UNPARSEABLE_CERT
                            )
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadCertError(TLS_TRUST_ROOTS, 1))
        }

        @Test
        fun `tls pki must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsPkiOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(TLS_PKI))
        }

        @Test
        fun `tls pki must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsPkiOverride = EMPTY_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(TLS_PKI))
        }

        @Test
        fun `tls pki must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsPkiOverride = WHITESPACE_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(TLS_PKI))
        }

        @Test
        fun `tls pki must match enum`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsPkiOverride = BAD_ENUM_VALUE
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadEnumError(TLS_PKI, BAD_ENUM_VALUE))
        }

        @Test
        fun `tls version must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsVersionOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(TLS_VERSION))
        }

        @Test
        fun `tls version must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsVersionOverride = EMPTY_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(TLS_VERSION))
        }

        @Test
        fun `tls version must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsVersionOverride = WHITESPACE_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(TLS_VERSION))
        }

        @Test
        fun `tls version must match enum`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            tlsVersionOverride = BAD_ENUM_VALUE
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadEnumError(TLS_VERSION, BAD_ENUM_VALUE))
        }

        @Test
        fun `protocol mode must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            protocolModeOverride = null
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(PROTOCOL_MODE))
        }

        @Test
        fun `protocol mode must not be empty`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            protocolModeOverride = EMPTY_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(PROTOCOL_MODE))
        }

        @Test
        fun `protocol mode must not be all whitespace`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            protocolModeOverride = WHITESPACE_STRING
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBlankValueError(PROTOCOL_MODE))
        }

        @Test
        fun `protocol mode must match enum`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(
                    buildGroupPolicyNode(
                        p2pParametersOverride = buildP2PParameters(
                            protocolModeOverride = BAD_ENUM_VALUE
                        )
                    )
                )
            }
            assertThat(ex.message).isEqualTo(getBadEnumError(PROTOCOL_MODE, BAD_ENUM_VALUE))
        }

        @Test
        fun `mgm info can be null`() {
            assertDoesNotThrow {
                InteropGroupPolicyImpl(buildGroupPolicyNode(mgmInfoOverride = null))
            }
        }

        @Test
        fun `cipher suite must not be null`() {
            val ex = assertThrows<BadGroupPolicyException> {
                InteropGroupPolicyImpl(buildGroupPolicyNode(cipherSuiteOverride = null))
            }
            assertThat(ex.message).isEqualTo(getMissingKeyError(CIPHER_SUITE))
        }
    }
}