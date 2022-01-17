package com.r3.corda.utils.provider

import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Security
import java.security.cert.Certificate
import java.util.Collections
import java.util.Enumeration

@Suppress("TooManyFunctions")
class DelegatedKeystore(
    private val signingService: DelegatedSigningService
) : KeyStoreSpi() {
    private fun getAlias(name: String?): DelegatedSigningService.Alias? {
        return signingService.aliases.firstOrNull {
            it.name == name
        }
    }

    private fun getCertificates(name: String?): Collection<Certificate>? {
        return getAlias(name)?.certificates
    }

    override fun engineGetKey(alias: String, password: CharArray): Key? {
        val aliasService = getAlias(alias) ?: return null
        return aliasService.certificates.firstOrNull()?.publicKey?.let {
            DelegatedPrivateKey(it.format, it.algorithm, aliasService)
        }
    }

    override fun engineGetCertificate(alias: String): Certificate? = getCertificates(alias)?.firstOrNull()

    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = getCertificates(alias)?.toTypedArray()

    override fun engineAliases(): Enumeration<String>? = signingService.aliases
        .map {
            it.name
        }.let {
            Collections.enumeration(it)
        }

    override fun engineContainsAlias(alias: String): Boolean = signingService.aliases.any { it.name == alias }

    override fun engineSize(): Int = signingService.aliases.size

    override fun engineIsKeyEntry(alias: String): Boolean = engineContainsAlias(alias)

    override fun engineLoad(param: KeyStore.LoadStoreParameter?) {
        // Insert Delegated signature provider if its not registered with java security.
        val providers = Security.getProviders()
        if (providers.size <= 1 || providers[1] !is DelegatedSignatureProvider) {
            Security.insertProviderAt(DelegatedSignatureProvider(), 1)
        }
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
