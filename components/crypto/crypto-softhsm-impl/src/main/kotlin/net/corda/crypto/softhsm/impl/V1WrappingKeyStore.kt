package net.corda.crypto.softhsm.impl

import java.time.Instant
import javax.persistence.EntityManagerFactory
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use

class V1WrappingKeyStore(
    private val entityManagerFactory: EntityManagerFactory,
) {
    fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
        entityManagerFactory.createEntityManager().transaction { em ->
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

    fun findWrappingKey(alias: String): WrappingKeyInfo? =
        entityManagerFactory.createEntityManager().use { em ->
            em.find(WrappingKeyEntity::class.java, alias)?.let { rec ->
                WrappingKeyInfo(
                    encodingVersion = rec.encodingVersion,
                    algorithmName = rec.algorithmName,
                    keyMaterial = rec.keyMaterial
                )
            }
        }

}