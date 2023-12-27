package net.corda.crypto.delegated.signing

import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.cert.Certificate
import java.util.Collections
import java.util.Enumeration

@Suppress("TooManyFunctions")
internal class DelegatedKeystore(
    private val certificateStore: DelegatedCertificateStore,
    private val signer: DelegatedSigner,
) : KeyStoreSpi() {
    private fun getCertificates(alias: String): Collection<Certificate>? = certificateStore.aliasToCertificates[alias]

    override fun engineGetKey(alias: String, password: CharArray): Key? {
        certificateStore.logger("QQQ in engineGetKey for $alias")
        return getCertificates(alias)
            ?.firstOrNull()
            ?.publicKey?.let {
                certificateStore.logger("QQQ \t got public key: $it")
                DelegatedPrivateKey(it, signer)
            }
    }


    override fun engineGetCertificate(alias: String): Certificate? {
        certificateStore.logger("QQQ in engineGetCertificate for $alias")
        return getCertificates(alias)?.firstOrNull().also {
            certificateStore.logger("QQQ \t return $it")
        }
    }


    override fun engineGetCertificateChain(alias: String): Array<Certificate>? {
        certificateStore.logger("QQQ in engineGetCertificateChain for $alias")
        return getCertificates(alias)?.toTypedArray().also {
            certificateStore.logger("QQQ \t return ${it?.size}")
        }
    }


    override fun engineAliases(): Enumeration<String> =
        Collections.enumeration(certificateStore.aliasToCertificates.keys).also {
            certificateStore.logger("QQQ engineAliases returns ${certificateStore.aliasToCertificates.keys}")
        }

    override fun engineContainsAlias(alias: String): Boolean {
        certificateStore.logger("QQQ in engineContainsAlias for $alias")
        return certificateStore.aliasToCertificates.containsKey(alias).also {
            certificateStore.logger("QQQ \t return $it")
        }
    }

    override fun engineSize() = certificateStore.aliasToCertificates.size

    override fun engineIsKeyEntry(alias: String): Boolean = engineContainsAlias(alias)

    override fun engineLoad(param: KeyStore.LoadStoreParameter?) {
        // Do nothing
    }

    override fun engineLoad(stream: InputStream, password: CharArray) {
        engineLoad(null)
    }

    // Read only keystore, write operations are not supported.
    override fun engineSetKeyEntry(alias: String?, key: Key?, password: CharArray?, chain: Array<out Certificate>?) {
        throw UnsupportedOperationException()
    }

    override fun engineSetKeyEntry(alias: String?, key: ByteArray?, chain: Array<out Certificate>?) {
        throw UnsupportedOperationException()
    }

    override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) {
        throw UnsupportedOperationException()
    }

    override fun engineDeleteEntry(alias: String?) {
        throw UnsupportedOperationException()
    }

    override fun engineIsCertificateEntry(alias: String?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun engineGetCertificateAlias(cert: Certificate?): String? {
        throw UnsupportedOperationException()
    }

    override fun engineStore(stream: OutputStream?, password: CharArray?) {
        throw UnsupportedOperationException()
    }

    override fun engineGetCreationDate(alias: String) = throw UnsupportedOperationException()
}
