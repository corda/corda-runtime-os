package net.corda.libs.packaging.testutils

import net.corda.test.util.InMemoryZipFile
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.CodeSigner
import java.security.KeyStore
import java.security.KeyStore.PasswordProtection
import java.security.MessageDigest
import java.security.Timestamp
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date

object TestUtils {
    private val KEY_PASSWORD = "cordadevpass".toCharArray()
    val KEY_STORE_PASSWORD = KEY_PASSWORD
    val ALICE = Signer("alice", privateKeyEntry("alice", resourceInputStream("alice.p12")))
    val BOB = Signer("bob", privateKeyEntry("bob", resourceInputStream("bob.p12")))
    val ROOT_CA = certificate("rootca", resourceInputStream("rootca.p12"))
    internal val CA1 = certificate("ca1", resourceInputStream("ca1.p12"))
    internal val CA2 = certificate("ca2", resourceInputStream("ca2.p12"))
    internal val CODE_SIGNER_ALICE = codeSigner("alice", resourceInputStream("alice.p12"))
    const val EXTERNAL_CHANNELS_CONFIG_FILE_CONTENT = "{}"

    val ROOT_CA_KEY_STORE : InputStream
        get() = resourceInputStream("rootca.p12")

    /**
     * Compute the [SecureHash] of a [ByteArray] using the specified [DigestAlgorithmName]
     */
    private fun ByteArray.hash(algo : DigestAlgorithmName = DigestAlgorithmName.SHA2_256) : SecureHash {
        val md = MessageDigest.getInstance(algo.name)
        md.update(this)
        return SecureHash(algo.name, md.digest())
    }

    private fun resourceInputStream(fileName: String): InputStream =
        this::class.java.classLoader.getResourceAsStream(fileName)
            ?: throw FileNotFoundException("Resource file \"$fileName\" not found")

    class Signer(val alias: String, val privateKeyEntry: KeyStore.PrivateKeyEntry)

    class Library(val name: String) {
        val content = "Content of library $name".toByteArray()
        val hash: String = Base64.getEncoder().encodeToString(content.hash().bytes)
    }

    class Dependency(val name: String, val version: String, val hash: SecureHash? = null)

    private fun privateKeyEntry(alias: String, keyStoreInputStream: InputStream): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStoreInputStream.use { keyStoreData -> keyStore.load(keyStoreData, KEY_STORE_PASSWORD) }
        return keyStore.getEntry(alias, PasswordProtection(KEY_PASSWORD)) as KeyStore.PrivateKeyEntry
    }

    private fun certificate(alias: String, keyStoreInputStream: InputStream ): X509Certificate {
        val privateKeyEntry = privateKeyEntry(alias, keyStoreInputStream)
        return privateKeyEntry.certificate as X509Certificate
    }

    private fun certificateChain(alias: String, keyStoreInputStream: InputStream): List<Certificate> {
        val privateKeyEntry = privateKeyEntry(alias, keyStoreInputStream)
        return privateKeyEntry.certificateChain.toList()
    }

    private fun codeSigner(alias: String, keyStoreInputStream: InputStream): CodeSigner {
        val certificateChain = certificateChain(alias, keyStoreInputStream)
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certPath = certificateFactory.generateCertPath(certificateChain)
        return CodeSigner(certPath, Timestamp(Date(), certPath))
    }

    fun InMemoryZipFile.addFile(name: String, content: ByteArray) {
        addEntry(name, content)
    }

    internal fun InMemoryZipFile.addFile(name: String, content: String? = null) {
        val contentBytes = (content ?: "Content of file $name").toByteArray()
        addFile(name, contentBytes)
    }

    fun InMemoryZipFile.signedBy(vararg signers: Signer): InMemoryZipFile {
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

    fun base64ToBytes(base64: String) =
        Base64.getDecoder().decode(base64)

    internal fun SecureHash.toBase64() =
        Base64.getEncoder().encodeToString(this.bytes)
}
