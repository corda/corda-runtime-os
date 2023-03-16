package net.corda.ledger.common.testkit

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey
import java.time.Instant

fun getSignatureWithMetadataExample(publicKey: PublicKey = publicKeyExample, createdTs: Instant = Instant.now()) =
    DigitalSignatureAndMetadata(
        DigitalSignature.WithKey(publicKey, "signature".toByteArray()),
        DigitalSignatureMetadata(createdTs, SignatureSpec("dummySignatureName"), mapOf("propertyKey1" to "propertyValue1"))
    )