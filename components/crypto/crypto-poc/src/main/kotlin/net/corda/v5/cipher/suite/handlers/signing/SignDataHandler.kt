package net.corda.v5.cipher.suite.handlers.signing

interface SignDataHandler {
    val rank: Int

    /**
     * Signs a byte array using the private key identified by the input arguments.
     *
     * @param spec (either [SigningAliasSpec] or [SigningWrappedSpec]) to be used for signing.
     * @param data the data to be signed.
     * @param context the optional key/value operation context. The context will have at least one variable defined -
     * 'tenantId'.
     *
     * @return the signature bytes formatted according to the default signature spec.
     *
     * @throws IllegalArgumentException if the key is not found, the key scheme is not supported, the signature spec
     * is not supported or in general the input parameters are wrong
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun sign(
        spec: SigningSpec,
        data: ByteArray,
        metadata: ByteArray,
        context: Map<String, String>
    ): ByteArray
}