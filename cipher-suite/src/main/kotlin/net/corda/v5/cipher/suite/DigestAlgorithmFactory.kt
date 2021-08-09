package net.corda.v5.cipher.suite

interface DigestAlgorithmFactory {
    val algorithm: String
    fun getInstance(): DigestAlgorithm
}