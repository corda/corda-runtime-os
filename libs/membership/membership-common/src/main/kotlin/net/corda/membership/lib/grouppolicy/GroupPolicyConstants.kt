package net.corda.membership.lib.grouppolicy

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Constants for [GroupPolicy]. For example, expected keys and default values.
 */
class GroupPolicyConstants {

    /**
     * Key names for the properties in the group policy file.
     */
    object PolicyKeys {
        /**
         * Root level keys.
         */
        object Root {
            const val FILE_FORMAT_VERSION = "fileFormatVersion"

            const val GROUP_ID = "groupId"

            const val REGISTRATION_PROTOCOL = "registrationProtocol"

            const val SYNC_PROTOCOL = "synchronisationProtocol"

            const val PROTOCOL_PARAMETERS = "protocolParameters"

            const val P2P_PARAMETERS = "p2pParameters"

            const val MGM_INFO = "mgmInfo"

            const val CIPHER_SUITE = "cipherSuite"
        }

        /**
         * Protocol parameter level keys.
         */
        object ProtocolParameters {
            const val SESSION_KEY_POLICY = "sessionKeyPolicy"

            const val STATIC_NETWORK = "staticNetwork"

            /**
             * Static network level keys.
             */
            object StaticNetwork {
                const val MEMBERS = "members"
            }
        }

        /**
         * P2P parameter level keys.
         */
        object P2PParameters {
            const val SESSION_TRUST_ROOTS = "sessionTrustRoots"

            const val TLS_TRUST_ROOTS = "tlsTrustRoots"

            const val SESSION_PKI = "sessionPki"

            const val TLS_PKI = "tlsPki"

            const val TLS_VERSION = "tlsVersion"

            const val PROTOCOL_MODE = "protocolMode"

            const val TLS_TYPE = "tlsType"
        }
    }

    /**
     * Default values used in the group policy file.
     */
    object PolicyValues {
        /**
         * Root level default values.
         */
        object Root {
            /**
             * The group ID which should be set in a group policy file for an MGM.
             */
            const val MGM_DEFAULT_GROUP_ID = "CREATE_ID"
        }

        /**
         * Protocol parameter level defaults.
         */
        object ProtocolParameters {
            /**
             * Enum defining allowed values for sessionKeyPolicy.
             */
            enum class SessionKeyPolicy(private val policy: String) {
                COMBINED("Combined"),
                DISTINCT("Distinct");

                companion object {
                    fun fromString(str: String?): SessionKeyPolicy? = values().firstOrNull {
                        it.policy.equals(str, ignoreCase = true)
                    }
                }

                override fun toString(): String {
                    return policy
                }
            }
        }

        /**
         * P2P parameter level defaults.
         */
        object P2PParameters {
            /**
             * Enum defining the allowed session PKI mode values.
             */
            enum class SessionPkiMode(private val mode: String) {
                STANDARD("Standard"),
                STANDARD_EV3("StandardEV3"),
                CORDA_4("Corda4"),
                NO_PKI("NoPKI");

                companion object {
                    fun fromString(str: String?): SessionPkiMode? = values().firstOrNull {
                        it.mode.equals(str, ignoreCase = true)
                    }
                }

                override fun toString(): String {
                    return mode
                }
            }

            /**
             * Enum defining the allowed TLS PKI mode values.
             */
            enum class TlsPkiMode(private val mode: String) {
                STANDARD("Standard"),
                STANDARD_EV3("StandardEV3"),
                CORDA_4("Corda4");

                companion object {
                    fun fromString(str: String?): TlsPkiMode? = values().firstOrNull {
                        it.mode.equals(str, ignoreCase = true)
                    }
                }

                override fun toString(): String {
                    return mode
                }
            }

            /**
             * Enum defining the allowed TLS version values.
             */
            enum class TlsVersion(private val version: String) {
                VERSION_1_2("1.2"),
                VERSION_1_3("1.3");

                companion object {
                    fun fromString(str: String?): TlsVersion? = values().firstOrNull {
                        it.version == str
                    }
                }

                override fun toString(): String {
                    return version
                }
            }

            /**
             * Enum defining the allowed protocol mode values.
             */
            enum class ProtocolMode(private val mode: String) {
                AUTH("Authentication"),
                AUTH_ENCRYPT("Authenticated_Encryption");

                companion object {
                    fun fromString(str: String?): ProtocolMode? = values().firstOrNull {
                        it.mode.equals(str, ignoreCase = true)
                    }
                }

                override fun toString(): String {
                    return mode
                }
            }

            /**
             * Enum defining the TLS type values.
             */
            enum class TlsType(
                val groupPolicyName: String,
            ) {
                ONE_WAY("OneWay"),
                MUTUAL("Mutual");

                companion object {
                    fun getClusterType(configurationGetter: (String)-> SmartConfig?) : TlsType {
                        val gatewayConfiguration =
                            configurationGetter.invoke(ConfigKeys.P2P_GATEWAY_CONFIG) ?:
                            throw FailToReadClusterTlsTypeException(
                                "Could not get the Gateway configuration"
                            )
                        val tlsType = gatewayConfiguration
                            .getConfig("sslConfig")
                            .getString(PolicyKeys.P2PParameters.TLS_TYPE)
                        return valueOf(tlsType)
                    }
                    fun fromString(str: String?): TlsType? = values()
                        .firstOrNull {
                        it.groupPolicyName.equals(str, ignoreCase = true) ||
                                it.name.equals(str, ignoreCase = true)
                    }
                }

                class FailToReadClusterTlsTypeException(message: String)  : CordaRuntimeException(message)
            }
        }
    }

    /**
     * Keys as stored in the persistence layer.
     */
    object PropertyKeys {
        const val REGISTRATION_PROTOCOL = "protocol.registration"
        const val SYNC_PROTOCOL = "protocol.synchronisation"
        const val SESSION_KEY_POLICY = "key.session.policy"
        const val SESSION_PKI_MODE = "pki.session"
        const val TLS_PKI_MODE = "pki.tls"
        const val TLS_VERSION = "tls.version"
        const val SESSION_TRUST_ROOTS = "truststore.session"
        const val TLS_TRUST_ROOTS = "truststore.tls"
        const val P2P_PROTOCOL_MODE = "protocol.p2p.mode"
        const val TLS_TYPE = "tls.type"
    }
}
