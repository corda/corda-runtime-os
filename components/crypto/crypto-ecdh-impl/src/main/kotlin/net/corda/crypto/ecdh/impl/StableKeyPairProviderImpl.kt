package net.corda.crypto.ecdh.impl

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.ecdh.ECDHKeyPair
import net.corda.crypto.ecdh.StableKeyPairProvider
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

@Component(service = [StableKeyPairProvider::class])
class StableKeyPairProviderImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient
) : AbstractComponent<StableKeyPairProviderImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<StableKeyPairProvider>(),
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
    )
), StableKeyPairProvider {
    interface Impl : AutoCloseable {
        fun create(
            tenantId: String,
            publicKey: PublicKey,
            otherPublicKey: PublicKey,
            digestName: String
        ): ECDHKeyPair
    }

    override fun createActiveImpl(): Impl = ActiveImpl(cryptoOpsClient)

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun create(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        digestName: String
    ): ECDHKeyPair =
        impl.create(tenantId, publicKey, otherPublicKey, digestName)

    class InactiveImpl : Impl {
        override fun create(
            tenantId: String,
            publicKey: PublicKey,
            otherPublicKey: PublicKey,
            digestName: String
        ): ECDHKeyPair {
            throw IllegalStateException("The component is in invalid state.")
        }

        override fun close() = Unit
    }

    class ActiveImpl(
        private val cryptoOpsClient: CryptoOpsClient
    ) : Impl {
        override fun create(
            tenantId: String,
            publicKey: PublicKey,
            otherPublicKey: PublicKey,
            digestName: String
        ): ECDHKeyPair =
            StableKeyPair(cryptoOpsClient, tenantId, publicKey, otherPublicKey, digestName)

        override fun close() = Unit
    }
}