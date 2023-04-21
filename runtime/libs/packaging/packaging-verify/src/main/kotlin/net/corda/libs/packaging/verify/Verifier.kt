package net.corda.libs.packaging.verify

interface Verifier {
    /**
     * Throws if it fails to verify.
     */
    fun verify()
}