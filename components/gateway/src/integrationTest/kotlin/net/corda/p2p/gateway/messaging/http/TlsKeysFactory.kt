package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.GatewayTruststore
import net.corda.p2p.test.KeyAlgorithm
import java.io.File
import java.security.KeyStore
import java.util.UUID

internal class TlsKeysFactory(
    private val password: String,
    private val keyAlgorithm: KeyAlgorithm
) : AutoCloseable {
    override fun close() {
        tempOpenSslDir.deleteRecursively()
    }
    private val tempOpenSslDir by lazy {
        File.createTempFile("opensSsl.", ".test").also { tempOpenSslDir ->
            tempOpenSslDir.delete()
            tempOpenSslDir.mkdirs()
            val signedCertificatesStorage = File(tempOpenSslDir, "ca.db.certs")
            signedCertificatesStorage.mkdirs()
            val indexFile = File(tempOpenSslDir, "ca.db.index")
            indexFile.writeText("")
            val serialNumberFile = File(tempOpenSslDir, "ca.db.serial")
            serialNumberFile.writeText("01")
            val configFile = File(tempOpenSslDir, "ca.conf")
            configFile.writeText(
                """
[ ca ]
default_ca = ca_default

[ ca_default ]
certs = $tempOpenSslDir
new_certs_dir = $signedCertificatesStorage
database = $indexFile
serial = $serialNumberFile
RANDFILE = $tempOpenSslDir/ca.db.rand
certificate = $tempOpenSslDir/ca.crt
private_key = $tempOpenSslDir/ca.key
default_days = 1024
default_crl_days = 1024
default_md = md5
preserve = no
policy = generic_policy
copy_extensions = copy
[ generic_policy ]
countryName = optional
stateOrProvinceName = optional
localityName = optional
organizationName = optional
organizationalUnitName = optional
commonName = supplied
emailAddress = optional
            
                """.trimIndent()
            )
        }
    }

    private val trustStoreFile by lazy {
        File(tempOpenSslDir, "truststore.pem").also {
            createKeyPair("ca")

            run(
                "openssl",
                "req",
                "-new", "-x509",
                "-nodes",
                "-key", "ca.key",
                "-out", it.absolutePath,
                "-passin", "pass:$password",
                "-passout", "pass:$password",
                "-subj", "/C=UK/CN=r3.com"
            )
        }
    }

    val trustStoreKeyStore by lazy {
        TrustStoresMap.TrustedCertificates(listOf(trustStoreFile.readText())).trustStore
    }
    val gatewayTrustStore by lazy {
        GatewayTruststore(listOf(trustStoreFile.readText()))
    }

    private fun createKeyPair(name: String) {
        when (keyAlgorithm) {
            KeyAlgorithm.RSA -> {
                run(
                    "openssl",
                    "genrsa",
                    "-out", "$name.key",
                    "2048"
                )
            }
            KeyAlgorithm.ECDSA -> {
                run(
                    "openssl",
                    "ecparam",
                    "-out", "$name.key",
                    "-name", "prime256v1",
                    "-genkey"
                )
            }
        }
    }

    fun createKeyStore(
        host: String,
        name: String,
    ): KeyStoreWithPassword {
        val fileName = UUID.randomUUID().toString().replace("-", "")
        createKeyPair(fileName)
        run(
            "openssl",
            "req", "-new",
            "-key", "$fileName.key",
            "-out", "$fileName.csr",
            "-subj", "/C=UK/CN=$host",
            "-addext", "subjectAltName = DNS:$host"
        )
        run(
            "openssl",
            "ca", "-in", "$fileName.csr",
            "-out", "$fileName.cer",
            "-cert", trustStoreFile.absolutePath,
            "-keyfile", "ca.key",
            "-passin", "pass:$password",
            "-config", "ca.conf",
            "-batch",
            "-passin", "pass:$password",
            "-md", "sha512"
        )
        val combine = File(tempOpenSslDir, "$fileName.combined.pem")
        val keyFile = File(tempOpenSslDir, "$fileName.key")
        val certificateFile = File(tempOpenSslDir, "$fileName.cer")
        val pkcs12 = File(tempOpenSslDir, "$fileName.pkcs12")
        combine.appendText(certificateFile.readText())
        combine.appendText(keyFile.readText())
        run(
            "openssl",
            "pkcs12",
            "-export",
            "-name", name,
            "-out", pkcs12.absolutePath,
            "-in", combine.absolutePath,
            "-passin", "pass:$password",
            "-passout", "pass:$password"
        )

        val keyStore = KeyStore.getInstance("PKCS12").also { keyStore ->
            pkcs12.inputStream().use {
                keyStore.load(it, password.toCharArray())
            }
        }
        return KeyStoreWithPassword(keyStore, password)
    }

    private fun run(vararg commands: String) {
        val process = ProcessBuilder(*commands)
            .directory(tempOpenSslDir)
            .inheritIO()
            .start()
        if (process.waitFor() != 0) {
            throw RuntimeException("Fail to run ${commands.first()}")
        }
    }
}
