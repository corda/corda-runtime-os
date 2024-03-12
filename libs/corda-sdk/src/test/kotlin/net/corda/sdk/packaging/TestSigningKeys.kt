package net.corda.sdk.packaging

import java.io.InputStream
import java.security.KeyStore

object TestSigningKeys {
    private val KEY_STORE_PASSWORD = "keystore password".toCharArray()

    private fun privateKeyEntry(alias: String, keyStoreInputStream: InputStream): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStoreInputStream.use { keyStoreData -> keyStore.load(keyStoreData, KEY_STORE_PASSWORD) }
        return keyStore.getEntry(alias, KeyStore.PasswordProtection(KEY_STORE_PASSWORD)) as KeyStore.PrivateKeyEntry
    }

    private fun resourceInputStream(filePath: String): InputStream {
        return this::class.java.getResourceAsStream(filePath)
            ?: throw IllegalStateException("$filePath not found!")
    }

    const val SIGNING_KEY_1_ALIAS = "signing key 1"
    const val SIGNING_KEY_2_ALIAS = "signing key 2"

    val SIGNING_KEY_1 = privateKeyEntry(SIGNING_KEY_1_ALIAS, resourceInputStream("/signingkeys.pfx"))
    val SIGNING_KEY_2 = privateKeyEntry(SIGNING_KEY_2_ALIAS, resourceInputStream("/signingkeys.pfx"))

    const val CPK_SIGNER_NAME = "CPK-SIG"
    const val CPB_SIGNER_NAME = "CPB-SIG"
    const val CPI_SIGNER_NAME = "CPI-SIG"
    val CPK_SIGNER = net.corda.libs.packaging.testutils.TestUtils.Signer(
        CPK_SIGNER_NAME,
        SIGNING_KEY_2
    )
    val CPB_SIGNER = net.corda.libs.packaging.testutils.TestUtils.Signer(
        CPB_SIGNER_NAME,
        SIGNING_KEY_1
    )
}
