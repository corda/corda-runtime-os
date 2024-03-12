package net.corda.crypto.softhsm.impl

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyMaterialEntity
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.persistence.EntityManagerFactory

class WrappingRepositoryImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val tenantId: String,
) : WrappingRepository {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun close() = entityManagerFactory.close()

    override fun saveKeyWithId(key: WrappingKeyInfo, id: UUID?): WrappingKeyInfo =
        entityManagerFactory.createEntityManager().use { em ->
            em.transaction { t ->
                t.merge(
                    WrappingKeyEntity(
                        id = id ?: UUID.randomUUID(),
                        generation = key.generation,
                        alias = key.alias,
                        created = Instant.now(),
                        rotationDate = LocalDate.parse("9999-12-31").atStartOfDay().toInstant(ZoneOffset.UTC),
                        encodingVersion = key.encodingVersion,
                        algorithmName = key.algorithmName,
                        keyMaterial = key.keyMaterial,
                        isParentKeyManaged = false,
                        parentKeyReference = key.parentKeyAlias,
                    )
                )
            }.toDto().also {
                logger.info("Storing wrapping key with alias ${key.alias} generation ${key.generation} in tenant $tenantId")
            }
        }

    override fun saveKey(key: WrappingKeyInfo): WrappingKeyInfo =
        saveKeyWithId(key, null)

    override fun findKey(alias: String): WrappingKeyInfo? = findKeyAndId(alias)?.second
    override fun findKeyAndId(alias: String): Pair<UUID, WrappingKeyInfo>? =
        entityManagerFactory.createEntityManager().use { it ->
            it.createQuery(
                "FROM ${WrappingKeyEntity::class.simpleName} AS k WHERE k.alias = :alias " +
                    "AND k.generation = (SELECT MAX(k.generation) FROM ${WrappingKeyEntity::class.java.simpleName} k WHERE k.alias=:alias)",
                WrappingKeyEntity::class.java
            ).setParameter("alias", alias).setMaxResults(1).resultList.singleOrNull()?.let { dao ->
                Pair(dao.id, dao.toDto())
            }
        }

    override fun findKeysNotWrappedByParentKey(parentKeyAlias: String): List<WrappingKeyInfo> =
        entityManagerFactory.createEntityManager().use {
            it.createQuery(
                "FROM ${WrappingKeyEntity::class.simpleName} " +
                "WHERE (alias, generation) IN (" +
                    "SELECT alias, MAX(generation) FROM ${WrappingKeyEntity::class.simpleName} " +
                    "GROUP BY alias" +
                ") AND parentKeyReference != :parentKeyAlias",
                WrappingKeyEntity::class.java
            ).setParameter("parentKeyAlias", parentKeyAlias).resultList
                .map { dao -> dao.toDto() }
        }

    override fun getKeyById(id: UUID): WrappingKeyInfo? = entityManagerFactory.createEntityManager().use {
        it.createQuery(
            "FROM ${WrappingKeyEntity::class.simpleName} AS k WHERE k.id = :id",
            WrappingKeyEntity::class.java
        ).setParameter("id", id).resultStream.map { dao -> dao.toDto() }.findFirst().orElse(null)
    }

    override fun getAllKeyIdsAndAliases(): Set<Pair<UUID, String>> =
        entityManagerFactory.createEntityManager().use { it ->
            // We can have multiple key materials per signing key, all of which point to different wrapping keys with different
            // generation numbers. So it's important to group results from this query by signing key id and then grab only the
            // one with the highest generation number of the wrapping key. This leaves us with the single mapping of
            // signing key <-> key material <-> wrapping key (highest generation).
            it.createQuery(
                "SELECT s, w FROM ${SigningKeyEntity::class.java.simpleName} s, ${SigningKeyMaterialEntity::class.java.simpleName} m," +
                    " ${WrappingKeyEntity::class.java.simpleName} w " +
                    "WHERE s.tenantId = :tenantId AND m.signingKeyId = s.id AND m.wrappingKeyId = w.id "
            ).setParameter("tenantId", tenantId).resultList
                .map {
                    val signingKeyAndWrappingKey =
                        checkNotNull(it as? Array<*>) { "JPA returned invalid results object" }
                    val signingKeyEntity = checkNotNull(signingKeyAndWrappingKey[0] as? SigningKeyEntity)
                    { "JPA returned wrong entity type for SigningKeyEntity" }
                    val wrappingKeyEntity = checkNotNull(signingKeyAndWrappingKey[1] as? WrappingKeyEntity)
                    { "JPA returned wrong entity type for WrappingKeyEntity" }
                    Pair(signingKeyEntity, wrappingKeyEntity)
                }
                .groupBy { it.first.id } // group by signing key id
                .map {
                    it.value.sortedBy { it.second.generation }.lastOrNull()
                } // highest generation wrapping key per signing key only
                .filterNotNull()
                .map { Pair(it.second.id, it.second.alias) } // extract UUID and alias of wrapping keys
                .toSet()
        }
}

// NOTE: this should be on the entity object directly, but this means this repo (and the DTOs) need
//   to move to the crypto-persistence-model module first.
fun WrappingKeyEntity.toDto() =
    WrappingKeyInfo(
        encodingVersion = this.encodingVersion,
        algorithmName = this.algorithmName,
        keyMaterial = this.keyMaterial,
        generation = this.generation,
        parentKeyAlias = this.parentKeyReference,
        alias = this.alias
    )
        