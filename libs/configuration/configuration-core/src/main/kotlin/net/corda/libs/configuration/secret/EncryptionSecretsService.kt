package net.corda.libs.configuration.secret

import net.corda.libs.configuration.SecretsCreateService

interface EncryptionSecretsService: SecretsLookupService, SecretsCreateService {
    companion object {
        const val SECRET_KEY = "encryptedSecret"
    }
}