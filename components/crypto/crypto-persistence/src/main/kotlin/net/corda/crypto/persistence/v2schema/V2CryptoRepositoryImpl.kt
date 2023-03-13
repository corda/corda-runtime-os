package net.corda.crypto.persistence.v2schema

import net.corda.crypto.persistence.CryptoRepository
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import java.time.Instant
import java.time.LocalDateTime
import javax.persistence.EntityManagerFactory

class V2CryptoRepositoryImpl(private val entityManagerFactory: () -> EntityManagerFactory) : CryptoRepository {
    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
        entityManagerFactory().transaction { em ->
            em.persist(
                V2WrappingKeyEntity(
                    alias = alias,
                    created = Instant.now(),
                    encodingVersion = key.encodingVersion,
                    algorithmName = key.algorithmName,
                    keyMaterial = key.keyMaterial,
                    rotationDate = LocalDateTime.now()
                )
            )
        }
    }

    override fun findWrappingKey(alias: String): WrappingKeyInfo? = entityManagerFactory().use { em ->
        em.find(V2WrappingKeyEntity::class.java, alias)?.let { rec ->
            WrappingKeyInfo(
                encodingVersion = rec.encodingVersion,
                algorithmName = rec.algorithmName,
                keyMaterial = rec.keyMaterial
            )
        }
    }
}