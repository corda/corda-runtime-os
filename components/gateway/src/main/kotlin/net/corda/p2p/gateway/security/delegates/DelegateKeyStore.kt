package net.corda.p2p.gateway.security.delegates

import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.cert.Certificate
import java.util.Collections
import java.util.Date
import java.util.Enumeration

@Suppress("TooManyFunctions")
internal class DelegateKeyStore : KeyStoreSpi() {

    private var service: SigningService? = null

    internal class LoadParameter(val service: SigningService) : KeyStore.LoadStoreParameter {
        override fun getProtectionParameter() = null
    }

    override fun engineLoad(param: KeyStore.LoadStoreParameter?) {
        if (param is LoadParameter) {
            service = param.service
        }
    }

    private fun getAlias(name: String?): SigningService.Alias? {
        return service?.aliases?.firstOrNull {
            it.name == name
        }
    }
    private fun getCertificates(name: String?): Collection<Certificate>? {
        return getAlias(name)?.certificates
    }

    override fun engineGetCertificateChain(alias: String?): Array<Certificate> {
        return getCertificates(alias)?.toTypedArray() ?: emptyArray()
    }

    override fun engineGetCertificate(alias: String?): Certificate? {
        return getCertificates(alias)?.firstOrNull()
    }

    override fun engineAliases(): Enumeration<String> {
        return service?.aliases?.map { it.name }?.let {
            Collections.enumeration(it)
        } ?: Collections.emptyEnumeration()
    }

    override fun engineContainsAlias(alias: String?): Boolean {
        return service?.aliases?.any { it.name == alias } ?: false
    }

    override fun engineSize(): Int {
        return service?.aliases?.size ?: 0
    }

    override fun engineIsKeyEntry(alias: String?): Boolean {
        return engineContainsAlias(alias)
    }

    override fun engineGetKey(alias: String, password: CharArray?): Key? {
        val aliasService = getAlias(alias) ?: return null
        return aliasService.certificates.firstOrNull()?.publicKey?.let {
            DelegatedPrivateKey(it.format, it.algorithm, aliasService)
        }
    }

    override fun engineIsCertificateEntry(alias: String?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun engineGetCertificateAlias(cert: Certificate?): String {
        throw UnsupportedOperationException()
    }

    override fun engineGetCreationDate(alias: String?): Date {
        throw UnsupportedOperationException()
    }

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

    override fun engineStore(stream: OutputStream?, password: CharArray?) {
        throw UnsupportedOperationException()
    }

    override fun engineLoad(stream: InputStream?, password: CharArray?) {
        throw UnsupportedOperationException()
    }
}
