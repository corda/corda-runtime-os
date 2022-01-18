package net.corda.libs.configuration.secret

import net.corda.crypto.Encryptor
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class SecretEncryptionUtil(
    private val encryptorFactory:
        (p: String, s: String) -> Encryptor = { p, s -> Encryptor.derive(p, s) }
): SecretEncryptor, SecretDecryptor {
    // cache the encryptors as creating them is expensive.
    private val encryptors = ConcurrentHashMap<Pair<String,String>, Encryptor>()

    override fun encrypt(plainText: String, salt: String, passphrase: String): String {
        if(salt.isBlank() || passphrase.isBlank())
            throw IllegalArgumentException("The 'salt' and 'passphrase' arguments should not be blank")
        val bytes = getOrCreateEncryptor(passphrase, salt).encrypt(plainText.toByteArray())
        return Base64.getEncoder().encodeToString(bytes)
    }

    override fun decrypt(cypherText: String, salt: String, passphrase: String): String {
        if(salt.isBlank() || passphrase.isBlank())
            throw IllegalArgumentException("The 'salt' and 'passphrase' arguments should not be blank")
        if(cypherText.isBlank())
            throw IllegalArgumentException("The 'cypherText' argument should not be blank")
        val encryptedBytes = Base64.getDecoder().decode(cypherText)
        try {
            return String(getOrCreateEncryptor(passphrase, salt).decrypt(encryptedBytes))
        }
        catch(e: Exception) {
            throw IllegalArgumentException("Cannot decrypt cypherText", e)
        }
    }

    private fun getOrCreateEncryptor(passphrase: String, salt: String): Encryptor =
        encryptors.computeIfAbsent(Pair(salt, passphrase)) { encryptorFactory(passphrase, salt) }
}