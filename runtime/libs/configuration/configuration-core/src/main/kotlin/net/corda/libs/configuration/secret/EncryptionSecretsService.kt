package net.corda.libs.configuration.secret

interface EncryptionSecretsService: SecretsService {
    companion object {
        const val SECRET_KEY = "encryptedSecret"
    }
}