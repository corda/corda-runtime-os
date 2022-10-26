package net.corda.ledger.common.testkit

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.crypto.DigitalSignature
import java.time.Instant

private val signature = DigitalSignature.WithKey(publicKeyExample, "0".toByteArray(), mapOf())
private val digitalSignatureMetadata =
    DigitalSignatureMetadata(Instant.now(), mapOf())
val signatureWithMetaDataExample = DigitalSignatureAndMetadata(signature, digitalSignatureMetadata)