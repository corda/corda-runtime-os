package net.corda.v5.cipher.suite

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec

/**
 * Defines the [KeyScheme] and which [SignatureSpec]s can be inferred to be used for the schemes using
 * the digest algorithm.
 *
 * See https://www.bouncycastle.org/specifications.html to get list of supported signatures.
 * Scroll down to "Signature Algorithms" / "Schemes" (or search for "SHA256withECDDSA")
 */
open class KeySchemeInfo(
    val scheme: KeyScheme,
    val defaultSignatureSpecsByDigest: Map<DigestAlgorithmName, SignatureSpec>,
    val defaultSignatureSpec: SignatureSpec?,
    val supportedSignatureSpecs: List<SignatureSpec>
)