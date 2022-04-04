package net.corda.crypto.test.certificates.generation

import net.corda.crypto.test.certificates.generation.CertificateAuthority.Companion.PASSWORD
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.time.Duration

internal class FileSystemCertificatesAuthorityImpl(
    keysFactoryDefinitions: KeysFactoryDefinitions,
    validDuration: Duration,
    defaultPrivateKeyAndCertificate: PrivateKeyWithCertificate?,
    private val home: File,
    firstSerialNumber: Long,
) : LocalCertificatesAuthority(
    keysFactoryDefinitions, validDuration, defaultPrivateKeyAndCertificate, firstSerialNumber
),
    FileSystemCertificatesAuthority {
    companion object {
        fun loadOrGenerate(
            keysFactoryDefinitions: KeysFactoryDefinitions,
            validDuration: Duration,
            home: File,
        ): FileSystemCertificatesAuthority {
            val (firstSerialNumber, defaultPrivateKeyAndCertificate) = if (home.exists()) {
                val serialNumber = File(home, "serialNumber.txt").readText().toLong()
                val keyStoreFile = File(home, "keystore.jks")
                val keyStore = keyStoreFile.inputStream().use { input ->
                    KeyStore.getInstance("JKS").also { keyStore ->
                        keyStore.load(input, PASSWORD.toCharArray())
                    }
                }
                val alias = keyStore.aliases().nextElement()
                serialNumber to
                    PrivateKeyWithCertificate(
                        keyStore.getKey(alias, PASSWORD.toCharArray())
                            as PrivateKey,
                        keyStore.getCertificate(alias)
                    )
            } else {
                1L to null
            }
            return FileSystemCertificatesAuthorityImpl(
                keysFactoryDefinitions,
                validDuration,
                defaultPrivateKeyAndCertificate,
                home,
                firstSerialNumber,
            )
        }
    }

    override fun save() {
        home.mkdirs()
        File(home, "serialNumber.txt").writeText(serialNumber.toString())
        File(home, "keystore.jks").outputStream().use {
            asKeyStore("alias").store(it, PASSWORD.toCharArray())
        }
    }
}
