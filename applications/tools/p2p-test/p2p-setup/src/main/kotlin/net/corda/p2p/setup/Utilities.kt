package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.data.config.Configuration
import net.corda.messaging.api.records.Record
import net.corda.p2p.test.KeyAlgorithm
import net.corda.schema.Schemas
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.File
import java.io.Reader
import java.security.KeyPair
import java.security.PublicKey

fun PublicKey.toAlgorithm(): KeyAlgorithm = when (this.algorithm) {
    "RSA" -> KeyAlgorithm.RSA
    "EC" -> KeyAlgorithm.ECDSA
    else -> {
        throw SetupException("Algorithm ${this.algorithm} not supported")
    }
}

fun Reader.readKeyPair(): KeyPair? {
    return PEMParser(this).use { parser ->
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
}

fun File.readKeyPair(): KeyPair? {
    return this.reader().use { reader ->
        reader.readKeyPair()
    }
}

fun File.readPublicKey(): PublicKey? {
    return this.reader().use { reader ->
        reader.readPublicKey()
    }
}

fun Reader.readPublicKey(): PublicKey? {
    return PEMParser(this).use { parser ->
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
}

fun Config.toConfigurationRecord(
    packageName: String,
    componentName: String,
    topic: String = Schemas.Config.CONFIG_TOPIC
): Record<String, Configuration> {
    val content = Configuration(this.root().render(ConfigRenderOptions.concise()), "1.0")
    val recordKey = "$packageName.$componentName"
    return Record(topic, recordKey, content)
}
