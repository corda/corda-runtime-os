package net.corda.ledger.common.testkit

import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import java.security.PublicKey
import java.time.Instant

fun getSignatureWithMetadataExample(publicKey: PublicKey = publicKeyExample, createdTs: Instant = Instant.now()) =
    DigitalSignatureAndMetadata(
        DigitalSignatureWithKeyId(publicKey.fullIdHash(), "signature".toByteArray()),
        DigitalSignatureMetadata(
            createdTs,
            SignatureSpecImpl(SignatureSpecs.ECDSA_SHA256.signatureName),
            mapOf("propertyKey1" to "propertyValue1")
        )
    )