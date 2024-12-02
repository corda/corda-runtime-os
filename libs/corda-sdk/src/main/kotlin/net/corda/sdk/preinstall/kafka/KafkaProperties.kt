package net.corda.sdk.preinstall.kafka

import java.util.Properties

data class KafkaProperties(
    private val bootstrapServers: String,
    var saslUsername: String? = null,
    var saslPassword: String? = null,
    var saslMechanism: String? = null,
    var truststorePassword: String? = null,
    var truststoreFile: String? = null,
    var truststoreType: String? = null,
    var timeout: Int = 3000,
    var tlsEnabled: Boolean = false,
    var saslEnabled: Boolean = false
) {

    class SaslPlainWithoutTlsException(message: String) : Exception(message)

    fun getKafkaProperties(): Properties {
        val props = Properties()
        props["bootstrap.servers"] = bootstrapServers
        props["request.timeout.ms"] = timeout
        props["connections.max.idle.ms"] = 5000

        // Assembles the properties in the same way as the helm charts:
        // https://github.com/corda/corda-runtime-os/blob/release/os/5.0/charts/corda-lib/templates/_bootstrap.tpl#L185
        if (tlsEnabled) {
            if (saslEnabled) {
                props["security.protocol"] = "SASL_SSL"
                props["sasl.mechanism"] = saslMechanism
                if (saslMechanism == "PLAIN") {
                    props["sasl.jaas.config"] =
                        "org.apache.kafka.common.security.plain.PlainLoginModule required username=" +
                        "\"${saslUsername}\" password=\"${saslPassword}\" ;"
                } else {
                    props["sasl.jaas.config"] =
                        "org.apache.kafka.common.security.scram.ScramLoginModule required username=" +
                        "\"${saslUsername}\" password=\"${saslPassword}\" ;"
                }
            } else {
                props["security.protocol"] = "SSL"
            }

            props["ssl.truststore.type"] = truststoreType?.uppercase()
            if (truststorePassword != null) {
                props["ssl.truststore.password"] = truststorePassword
            }

            if (truststoreFile != null) {
                props["ssl.truststore.certificates"] = truststoreFile
            }
        } else if (saslEnabled) {
            if (saslMechanism == "PLAIN") {
                throw SaslPlainWithoutTlsException("If TLS is not enabled, SASL should only be used with SCRAM authentication.")
            }
            props["security.protocol"] = "SASL_PLAINTEXT"
            props["sasl.mechanism"] = saslMechanism
            props["sasl.jaas.config"] =
                "org.apache.kafka.common.security.scram.ScramLoginModule required username=" +
                "\"${saslUsername}\" password=\"${saslPassword}\" ;"
        }
        return props
    }
}
