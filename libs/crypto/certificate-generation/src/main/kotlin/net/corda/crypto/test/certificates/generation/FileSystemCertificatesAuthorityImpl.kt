package net.corda.crypto.test.certificates.generation

import net.corda.crypto.test.certificates.generation.CertificateAuthority.Companion.PASSWORD
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.time.Duration

@Suppress("LongParameterList")
internal class FileSystemCertificatesAuthorityImpl(
    keysFactoryDefinitions: KeysFactoryDefinitions,
    validDuration: Duration,
    defaultPrivateKeyAndCertificate: PrivateKeyWithCertificate?,
    private val home: File,
    firstSerialNumber: Long,
    issuer: String?,
) : LocalCertificatesAuthority(
    keysFactoryDefinitions,
    validDuration,
    defaultPrivateKeyAndCertificate,
    firstSerialNumber,
    issuer,
),
    FileSystemCertificatesAuthority {
    companion object {
        fun loadOrGenerate(
            keysFactoryDefinitions: KeysFactoryDefinitions,
            validDuration: Duration,
            home: File,
        ): FileSystemCertificatesAuthority {
            val (firstSerialNumber, defaultPrivateKeyAndCertificate, issuer) = if (home.exists()) {
                val serialNumber = File(home, "serialNumber.txt").readText().toLong()
                val keyStoreFile = File(home, "keystore.jks")
                val issuer = File(home, "issuer.txt").let {
                    if (it.exists()) {
                        it.readText()
                    } else {
                        null
                    }
                }
                val keyStore = keyStoreFile.inputStream().use { input ->
                    KeyStore.getInstance("JKS").also { keyStore ->
                        keyStore.load(input, PASSWORD.toCharArray())
                    }
                }
                val alias = keyStore.aliases().nextElement()
                Triple(
                    serialNumber,
                    PrivateKeyWithCertificate(
                        keyStore.getKey(alias, PASSWORD.toCharArray())
                                as PrivateKey,
                        keyStore.getCertificate(alias)
                    ),
                    issuer,
                )
            } else {
                Triple(1L, null, null)
            }
            return FileSystemCertificatesAuthorityImpl(
                keysFactoryDefinitions,
                validDuration,
                defaultPrivateKeyAndCertificate,
                home,
                firstSerialNumber,
                issuer,
            )
        }
    }

    override fun save() {
        home.mkdirs()
        File(home, "serialNumber.txt").writeText(serialNumber.toString())
        File(home, "issuer.txt").writeText(issuer.toString())
        File(home, "keystore.jks").outputStream().use {
            asKeyStore("alias").store(it, PASSWORD.toCharArray())
        }
    }
}
