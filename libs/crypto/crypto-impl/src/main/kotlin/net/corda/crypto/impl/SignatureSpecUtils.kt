package net.corda.crypto.impl

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.cipher.suite.CustomSignatureSpec
import net.corda.v5.cipher.suite.PlatformDigestService
import net.corda.v5.crypto.SignatureSpec

fun SignatureSpec.getSigningData(digestService: PlatformDigestService, data: ByteArray): ByteArray = when (this) {
    is CustomSignatureSpec -> digestService.hash(data, customDigestName).bytes
    else -> data
}

fun SignatureSpec.getSigningData(digestService: DigestService, data: ByteArray): ByteArray = when (this) {
    is CustomSignatureSpec -> digestService.hash(data, customDigestName).bytes
    else -> data
}
