package net.corda.p2p.gateway.keystore

import net.corda.v5.base.util.contextLogger
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Security
import java.security.cert.Certificate
import java.util.*

/**
 * The read-only key store implementation of [java.security.KeyStoreSpi], which is backed by another [KeyStore]
 * and [DelegatedSigningService].
 * Returns private keys as [DelegatedPrivateKey]
 * and registers [DelegatedSignatureProvider] as the first signature provider with Java security (if its not registered
 * already).
 * The class doesn't hold any entries on its own, it delegates key signing to [signingService] and certificate storage
 * to [certStore].
 * @param signingService source of private keys, if not provided then the instance doesn't support returning a key
 * @param certStore source of certificates
 */
@Suppress("TooManyFunctions")
class DelegatedKeystore(private val signingService: DelegatedSigningService?,
                        private val certStore: KeyStore) : KeyStoreSpi() {
    companion object {
        private val logger = contextLogger()
    }

    override fun engineGetKey(alias: String, password: CharArray): Key? {
        requireNotNull(signingService) { "Operation is not supported on read-only keystore." }
        return certStore.getCertificate(alias)?.publicKey?.let { publicKey ->
            DelegatedPrivateKey.create(publicKey.algorithm, publicKey.format) { sigAlgo, data ->
                logger.info("Signing using delegated key : $alias, algo : $sigAlgo")
                signingService.sign(alias, data, sigAlgo)
            }
        }
    }

    override fun engineGetCertificate(alias: String): Certificate? = certStore.getCertificate(alias)

    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = certStore.getCertificateChain(alias)

    override fun engineAliases(): Enumeration<String>? = certStore.aliases()

    override fun engineContainsAlias(alias: String): Boolean = certStore.containsAlias(alias)

    override fun engineSize(): Int = certStore.size()

    override fun engineIsKeyEntry(alias: String): Boolean = certStore.isKeyEntry(alias)

    override fun engineLoad(param: KeyStore.LoadStoreParameter?) {
        val providers = Security.getProviders()
        if (providers.size <= 1 || providers[1] !is DelegatedSignatureProvider) {
            Security.insertProviderAt(DelegatedSignatureProvider(), 1)
        }
    }

    override fun engineLoad(stream: InputStream, password: CharArray) {
        engineLoad(null)
    }

    @Throws(UnsupportedOperationException::class)
    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray, chain: Array<Certificate>?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class)
    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class)
    override fun engineSetCertificateEntry(alias: String, cert: Certificate) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class)
    override fun engineDeleteEntry(alias: String) {
        throw UnsupportedOperationException()
    }

    override fun engineIsCertificateEntry(alias: String): Boolean = certStore.isCertificateEntry(alias)

    override fun engineGetCertificateAlias(cert: Certificate): String = certStore.getCertificateAlias(cert)

    @Throws(UnsupportedOperationException::class)
    override fun engineStore(stream: OutputStream, password: CharArray) = throw UnsupportedOperationException()

    override fun engineGetCreationDate(alias: String): Date = certStore.getCreationDate(alias)
}
