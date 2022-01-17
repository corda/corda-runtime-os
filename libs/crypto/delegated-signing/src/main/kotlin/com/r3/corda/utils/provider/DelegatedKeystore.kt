package com.r3.corda.utils.provider

import net.corda.v5.base.util.contextLogger
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
    companion object {
        private val logger = contextLogger()
    }

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

    override fun engineGetCertificate(alias: String): Certificate? = getAlias(alias)?.certificates?.firstOrNull()

    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = getAlias(alias)?.certificates?.toTypedArray()

    override fun engineAliases(): Enumeration<String>? = signingService.aliases.map { it.name }.let {
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
    override fun engineSetKeyEntry(var1: String, var2: Key, var3: CharArray, var4: Array<Certificate>?) {
        throw UnsupportedOperationException()
    }

    override fun engineSetKeyEntry(var1: String, var2: ByteArray, var3: Array<Certificate>) {
        throw UnsupportedOperationException()
    }

    override fun engineSetCertificateEntry(var1: String, var2: Certificate) {
        throw UnsupportedOperationException()
    }

    override fun engineDeleteEntry(var1: String) {
        throw UnsupportedOperationException()
    }

    override fun engineIsCertificateEntry(var1: String): Boolean {
        throw UnsupportedOperationException()
    }

    override fun engineGetCertificateAlias(var1: Certificate): String? {
        throw UnsupportedOperationException()
    }

    override fun engineStore(var1: OutputStream, var2: CharArray) {
        throw UnsupportedOperationException()
    }

    override fun engineGetCreationDate(var1: String) = throw UnsupportedOperationException()
}
