package net.corda.packaging

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.sha256Bytes

fun ByteArray.sha256(): SecureHash =
    SecureHash(DigestAlgorithmName.SHA2_256.name, this.sha256Bytes())
