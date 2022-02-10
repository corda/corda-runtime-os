package net.corda.libs.configuration.secret

interface EncryptionSecretsService: SecretsLookupService, SecretsCreateService {
    companion object {
        const val SECRET_KEY = "encryptedSecret"
    }
}