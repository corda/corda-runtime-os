package net.corda.crypto.impl

import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.CryptoLibraryFactoryProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.cipher.suite.CipherSuiteFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoLibraryFactoryProvider::class])
class CryptoLibraryFactoryProviderImpl @Activate constructor(
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : CryptoLibraryFactoryProvider {
    override fun create(memberId: String): CryptoLibraryFactory =
        CryptoLibraryFactoryImpl(
            memberId = memberId,
            cipherSuiteFactory = cipherSuiteFactory,
            publisherFactory = publisherFactory
        )
}