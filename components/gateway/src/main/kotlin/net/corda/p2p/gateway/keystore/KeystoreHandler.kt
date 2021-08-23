package net.corda.p2p.gateway.keystore

import java.math.BigInteger
import java.security.KeyStore
import java.security.cert.X509Certificate
import net.corda.crypto.SigningService
import net.corda.v5.base.util.contextLogger

class KeystoreHandler(private val signingService: SigningService,
                      private val delegatedKeystoreProviderName: String = "DelegatedKeyStore",
                      private val delegatedTrustStoreProviderName: String = "DelegatedTrustStore") {

    companion object {
        private val log = contextLogger()
    }
    private lateinit var trustRoots: Set<X509Certificate>

    private lateinit var tlsKeyStore: KeyStore
    private lateinit var mutableTlsTruststore: KeyStore
    private lateinit var tlsTrustStore: KeyStore

    fun init() {
        val tlsSigningService = DelegatedSigningServiceImpl(signingService)
        tlsKeyStore = DelegatedKeystoreProvider.createKeyStore(delegatedKeystoreProviderName, tlsSigningService, mutableTlsKeystore)
        tlsTrustStore = DelegatedKeystoreProvider.createKeyStore(delegatedTrustStoreProviderName, null, mutableTlsTruststore)
        trustRoots = validateKeyStores(tlsTrustStore, tlsKeyStore)
    }

    fun loadKeyStoresFromFile() {
        // TODO[CORE-695]: Remove DEV_CA certificates generation.
        configuration.configureWithDevSSLCertificate(signingService, devModeKeyEntropy)

        val fileBasedCertStores = getCertificateStores()
        validateKeyStores(fileBasedCertStores.trustStore, fileBasedCertStores.sslKeyStore)

        val mutableTlsKeystore = CertificateStoreFactory.databaseTlsKeyStore(hibernateSessionFactory)
        with(fileBasedCertStores.sslKeyStore) {
            this.aliases().forEach { alias -> this.copyCertificateChain(alias, mutableTlsKeystore) }
            resyncKeystore(this, tlsSigningService)
        }

        val nodeKeyStore = CertificateStoreFactory.databaseNodeKeyStore(hibernateSessionFactory)
        with(fileBasedCertStores.nodeKeyStore) {
            this.aliases().forEach { alias -> this.copyCertificateChain(alias, nodeKeyStore) }
            resyncKeystore(this, signingService)
        }
    }

    /**
     * Load TLS trust store into in-memory keystore.
     */
    fun loadTruststoreFromFile() {
        mutableTlsTruststore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { this.load(null, DUMMY_PASSWORD.toCharArray()) }
        val fileBasedCertStores = getCertificateStores()
        fileBasedCertStores.trustStore.value.internal.copyAllCertificatesTo(mutableTlsTruststore)
    }

    private fun validateKeyStores(trustStore: KeyStore,
                                  sslKeyStore: KeyStore): Set<X509Certificate> {

        sslKeyStore.run {

            // Check TLS certificate validity and print warning for expiry within next 30 days.
            it. [tlsKeyAlias].checkValidity({
                "TLS certificate for alias '$tlsKeyAlias' is expired."
            }, { daysToExpiry ->
                log.warn("TLS certificate for alias '$tlsKeyAlias' will expire in $daysToExpiry day(s).")
            })

            // Check TLS cert path chains to the trusted root.
            val sslCertChainRoot = it.query { getCertificateChain(tlsKeyAlias) }.last()
            require(sslCertChainRoot in trustRoots) { "TLS certificate must chain to the trusted root." }
        }

        return trustRoots
    }

    fun getTlsKeyStore() = tlsKeyStore

    fun getTlsTrustStore() = tlsTrustStore
}