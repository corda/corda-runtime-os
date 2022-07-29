package net.corda.crypto.persistence.impl

import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [WrappingKeyStore::class])
class WrappingKeyStoreImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CryptoConnectionsFactory::class)
    private val connectionsFactory: CryptoConnectionsFactory
) : AbstractComponent<WrappingKeyStoreImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<WrappingKeyStore>(),
    upstream = DependenciesTracker.Default(
        setOf(LifecycleCoordinatorName.forComponent<CryptoConnectionsFactory>())
    )
), WrappingKeyStore {
    override fun createActiveImpl(): Impl = Impl(connectionsFactory)

    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) =
        impl.saveWrappingKey(alias, key)

    override fun findWrappingKey(alias: String): WrappingKeyInfo? =
        impl.findWrappingKey(alias)

    class Impl(private val connectionsFactory: CryptoConnectionsFactory) : AbstractImpl {
        fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
            entityManagerFactory().transaction { em ->
                em.persist(
                    WrappingKeyEntity(
                        alias = alias,
                        created = Instant.now(),
                        encodingVersion = key.encodingVersion,
                        algorithmName = key.algorithmName,
                        keyMaterial = key.keyMaterial
                    )
                )
            }
        }

        fun findWrappingKey(alias: String): WrappingKeyInfo? = entityManagerFactory().use { em ->
            em.find(WrappingKeyEntity::class.java, alias)?.let { rec ->
                WrappingKeyInfo(
                    encodingVersion = rec.encodingVersion,
                    algorithmName = rec.algorithmName,
                    keyMaterial = rec.keyMaterial
                )
            }
        }

        private fun entityManagerFactory() = connectionsFactory.getEntityManagerFactory(CryptoTenants.CRYPTO)
    }
}