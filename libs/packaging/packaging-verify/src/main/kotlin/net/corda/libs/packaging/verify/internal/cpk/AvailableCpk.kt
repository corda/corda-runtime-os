package net.corda.libs.packaging.verify.internal.cpk

import java.security.CodeSigner

/** Stores metadata of CPKs available in CPB that is used to resolve dependencies */
internal data class AvailableCpk (
    val name: String,
    val version: String,
    val fileHashCalculator: FileHashCalculator,
    val codeSigners: List<CodeSigner>
)
