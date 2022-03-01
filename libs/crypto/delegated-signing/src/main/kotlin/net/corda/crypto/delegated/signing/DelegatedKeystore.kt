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
    private fun getCertificates(alias: String): Collection<Certificate>? = certificateStore.aliasToTenantInfo[alias]?.certificateChain

    override fun engineGetKey(alias: String, password: CharArray): Key? =
        certificateStore.aliasToTenantInfo[alias]
            ?.let { tenantInfo ->
                tenantInfo.certificateChain.firstOrNull()?.let { certificate ->
                    certificate.publicKey?.let { publicKey ->
                        DelegatedPrivateKey(tenantInfo.tenantId, publicKey, signer)
                    }
                }
            }

    override fun engineGetCertificate(alias: String): Certificate? =
        getCertificates(alias)?.firstOrNull()

    override fun engineGetCertificateChain(alias: String): Array<Certificate>? =
        getCertificates(alias)?.toTypedArray()

    override fun engineAliases(): Enumeration<String> =
        Collections.enumeration(certificateStore.aliasToTenantInfo.keys)

    override fun engineContainsAlias(alias: String): Boolean = certificateStore.aliasToTenantInfo.containsKey(alias)

    override fun engineSize() = certificateStore.aliasToTenantInfo.size

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
