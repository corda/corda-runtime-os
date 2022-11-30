package net.corda.crypto.impl

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.cipher.suite.CustomSignatureSpec
import net.corda.v5.cipher.suite.PlatformDigestService
import net.corda.v5.crypto.SignatureSpec

// TODO This seems to be called by both internal and sandbox APIs
//  Feels wrong to be called with `PlatformDigestService` since it looks for `CustomSignatureSpec`?
//  Unless `customDigestName` here means it could be a platform digest algorithm but different to SignatureSpec's one?
fun SignatureSpec.getSigningData(digestService: PlatformDigestService, data: ByteArray): ByteArray = when (this) {
    is CustomSignatureSpec -> digestService.hash(data, customDigestName).bytes
    else -> data
}

fun SignatureSpec.getSigningData(digestService: DigestService, data: ByteArray): ByteArray = when (this) {
    is CustomSignatureSpec -> digestService.hash(data, customDigestName).bytes
    else -> data
}
