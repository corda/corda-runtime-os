package net.corda.v5.cipher.suite.handlers.generation

interface GenerateKeyHandler {
    val rank: Int

    /**
     * Generates and optionally stores a key pair. The implementation is free to decide how the generated key
     * is stored - either in the corresponding HSM or wrapped and exported. The rule of thumb would be in the [spec]
     * has the [KeyGenerationSpec.alias] defined then it's expected that the key will be stored in the HSM otherwise
     * wrapped and exported but as mentioned above it's up to the concrete implementation. Such behaviour must be
     * defined beforehand and advertised. If the key is exported, its key material will be persisted
     * on the platform side.
     *
     * @param spec parameters to generate the key pair.
     * @param context the optional key/value operation context. The context will have at least two variables defined -
     * 'tenantId' and 'category'.
     *
     * @return Information about the generated key, could be either [GeneratedPublicKey] or [GeneratedWrappedKey]
     * depending on how the key is generated and persisted or wrapped and exported.
     *
     * @throws IllegalArgumentException the key scheme is not supported or in general the input parameters are wrong
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun generateKeyPair(
        spec: KeyGenerationSpec,
        context: Map<String, String>
    ): GeneratedKey
}