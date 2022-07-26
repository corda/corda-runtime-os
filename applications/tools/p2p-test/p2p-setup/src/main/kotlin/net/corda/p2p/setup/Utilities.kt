package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

fun String.verifyKeyPair() {
    this.reader().use {
        PEMParser(it).use { parser ->
            generateSequence {
                parser.readObject()
            }.map {
                if (it is PEMKeyPair) {
                    JcaPEMKeyConverter().getKeyPair(it)
                } else {
                    null
                }
            }.filterNotNull()
                .firstOrNull()
        }
    } ?: throw SetupException("Invalid key pair PEM: $this")
}

fun String.verifyPublicKey() {
    this.reader().use {
        PEMParser(it).use { parser ->
            generateSequence {
                parser.readObject()
            }.map {
                if (it is SubjectPublicKeyInfo) {
                    JcaPEMKeyConverter().getPublicKey(it)
                } else {
                    null
                }
            }.filterNotNull()
                .firstOrNull()
        }
    } ?: throw SetupException("Invalid public key PEM: $this")
}

fun Config.toConfigurationRecord(
    key: String,
    topic: String = Schemas.Config.CONFIG_TOPIC
): Record<String, Configuration> {
    val conf = this.root().render(ConfigRenderOptions.concise())
    val content = Configuration(conf, conf, 0, ConfigurationSchemaVersion(1,0))
    return Record(topic, key, content)
}
