package net.corda.libs.packaging.verify.internal.cpk

import net.corda.libs.packaging.hash
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.InputStream

/** CPK library verification related data */
class CpkLibrary(val name: String, val hash: SecureHash) {
    constructor(name: String, inputStream: InputStream) : this(name, inputStream.hash(DigestAlgorithmName.SHA2_256))
}