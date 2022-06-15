package net.corda.libs.packaging.verify

import net.corda.libs.packaging.hash
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.KeyStore
import java.security.KeyStore.PasswordProtection
import java.security.cert.X509Certificate
import java.util.Base64

internal object TestUtils {
    private val KEY_PASSWORD = "cordadevpass".toCharArray()
    private val KEY_STORE_PASSWORD = KEY_PASSWORD
    internal val ALICE = Signer("alice", privateKeyEntry("alice", resourceInputStream("alice.p12")))
    internal val BOB = Signer("bob", privateKeyEntry("bob", resourceInputStream("bob.p12")))
    internal val ROOT_CA = certificate("rootca", resourceInputStream("rootca.p12"))
    internal val CA1 = certificate("ca1", resourceInputStream("ca1.p12"))
    internal val CA2 = certificate("ca2", resourceInputStream("ca2.p12"))

    private fun resourceInputStream(fileName: String): InputStream =
        this::class.java.classLoader.getResourceAsStream(fileName)
            ?: throw FileNotFoundException("Resource file \"$fileName\" not found")

    class Signer(val alias: String, val privateKeyEntry: KeyStore.PrivateKeyEntry)

    class Library(val name: String) {
        val content = "Content of library $name".toByteArray()
        val hash: String = Base64.getEncoder().encodeToString(content.hash().bytes)
    }

    class Dependency(val name: String, val version: String)

    private fun privateKeyEntry(
        alias: String,
        keyStoreInputStream: InputStream,
    ): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStoreInputStream.use { keyStoreData -> keyStore.load(keyStoreData, KEY_STORE_PASSWORD) }
        return keyStore.getEntry(alias, PasswordProtection(KEY_PASSWORD)) as KeyStore.PrivateKeyEntry
    }

    private fun certificate(
        alias: String,
        keyStoreInputStream: InputStream,
    ): X509Certificate {
        val privateKeyEntry = privateKeyEntry(alias, keyStoreInputStream)
        return privateKeyEntry.certificate as X509Certificate
    }

    internal fun InMemoryZipFile.addFile(name: String, content: ByteArray) {
        addEntry(name, content)
    }

    internal fun InMemoryZipFile.addFile(name: String, content: String? = null) {
        val contentBytes = (content ?: "Content of file $name").toByteArray()
        addFile(name, contentBytes)
    }

    internal fun InMemoryZipFile.signedBy(vararg signers: Signer): InMemoryZipFile {
        if (signers.isEmpty()) return this
        // Sets zip entry attributes required for signing
        var zipFile = InMemoryZipFile(this.toByteArray())
        signers.forEach {
            val signedZipFile = zipFile.sign(it.privateKeyEntry, it.alias)
            zipFile.close()
            zipFile = signedZipFile
        }
        return zipFile
    }
}
