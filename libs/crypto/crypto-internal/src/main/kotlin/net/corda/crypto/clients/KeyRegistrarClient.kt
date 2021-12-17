package net.corda.crypto.clients

import net.corda.crypto.SigningService

/**
 * The key registration client to generate keys.
 */
interface KeyRegistrarClient {
    /**
     * Generates a new random key pair using the configured default key scheme and adds it to the internal key storage.
     *
     * @param tenantId The tenant owning the key.
     * @param category The key category, such as TLS, LEDGER, etc. Don't use FRESH_KEY category as there is separate API
     * for the fresh keys which is base around wrapped keys.
     * @param alias the tenant defined key alias for the key pair to be generated.
     * @param context the optional key/value operation context.
     *
     * @return The public part of the pair.
     */
    fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        context: Map<String, String> = SigningService.EMPTY_CONTEXT
    ): CryptoPublishResult
}