package net.corda.crypto.delegated.signing

internal enum class Hash {
    SHA256,
    SHA384,
    SHA512;

    val ecSignatureName by lazy {
        "${name}withECDSA"
    }
}
