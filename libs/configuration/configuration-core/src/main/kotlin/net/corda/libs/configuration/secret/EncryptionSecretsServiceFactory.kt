package net.corda.libs.configuration.secret

interface EncryptionSecretsServiceFactory {
    /**
     * Create and instance of [EncryptionSecretsService] configured with the given
     * [passphrase] and [salt]
     *
     * @param passphrase
     * @param salt
     * @return
     */
    fun create(passphrase: String, salt: String): EncryptionSecretsService
}