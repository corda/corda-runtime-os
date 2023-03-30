package repository

import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.softhsm.impl.HSMRepositoryImpl
import net.corda.crypto.softhsm.impl.toHSMAssociation
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import javax.persistence.EntityManagerFactory
import javax.persistence.PersistenceException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HSMRepositoryTest : CryptoRepositoryTest() {
    private val category = "romans"

    @ParameterizedTest
    @MethodSource("emfs")
    fun associate(emf: EntityManagerFactory) {
        val tenantId = "caesar${UUID.randomUUID().toString().take(6)}"
        val repo = HSMRepositoryImpl(emf, tenantId)

        val ai = repo.associate(
            tenantId,
            category,
            "hsm-id",
            MasterKeyPolicy.SHARED
        )

        // loaded
        var loaded = emf.createEntityManager().use {
            (it
                .createQuery("FROM ${HSMCategoryAssociationEntity::class.simpleName} AS t " +
                        "WHERE t.tenantId = :tenantId AND t.category = :category")
                .setParameter("tenantId", tenantId)
                .setParameter("category", category)
                .singleResult as HSMCategoryAssociationEntity)
                // master key alias is loaded from a related object, this means that this extension function must
                //  be called before the EM is closed. Otherwise, we must set the mapping up, so it eagerly fetches.
                .toHSMAssociation()
        }

        assertThat(loaded).isEqualTo(ai)
    }

    @MethodSource("emfs")
    fun `when associate unique generate alias`(emf: EntityManagerFactory) {
        val tenantId = "caesar${UUID.randomUUID().toString().take(6)}"
        val repo = HSMRepositoryImpl(emf, tenantId)

        val ai = repo.associate(
            tenantId,
            category,
            "hsm-id",
            MasterKeyPolicy.UNIQUE
        )

        assertThat(ai.masterKeyAlias).isNotNull
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `when associate twice should throw`(emf: EntityManagerFactory) {
        val tenantId = "caesar${UUID.randomUUID().toString().take(6)}"
        val repo = HSMRepositoryImpl(emf, tenantId)

        repo.associate(
            tenantId,
            category,
            "hsm-id",
            MasterKeyPolicy.SHARED
        )

        assertThrows<PersistenceException> {
            repo.associate(
                tenantId,
                category,
                "hsm-id",
                MasterKeyPolicy.SHARED
            )
        }
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun findTenantAssociation(emf: EntityManagerFactory) {
        val tenantId = "caesar${UUID.randomUUID().toString().take(6)}"
        val repo = HSMRepositoryImpl(emf, tenantId)

        repo.associate(
            tenantId,
            category,
            "hsm-id",
            MasterKeyPolicy.SHARED
        )

        val loaded = repo.findTenantAssociation(tenantId, category)

        assertThat(loaded).isNotNull

        assertSoftly {
            it.assertThat(loaded!!.tenantId).isEqualTo(tenantId)
            it.assertThat(loaded.category).isEqualTo(category)
            it.assertThat(loaded.hsmId).isEqualTo("hsm-id")
            it.assertThat(loaded.masterKeyAlias).isNull()
        }
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `findTenantAssociation returns null when none found`(emf: EntityManagerFactory) {
        val tenantId = "caesar${UUID.randomUUID().toString().take(6)}"
        val repo = HSMRepositoryImpl(emf, tenantId)

        val loaded = repo.findTenantAssociation(tenantId, category)

        assertThat(loaded).isNull()
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun getHSMUsage(emf: EntityManagerFactory) {
        val tenantId = "caesar${UUID.randomUUID().toString().take(6)}"
        val hsmId1 = "one-${UUID.randomUUID().toString().take(6)}"
        val hsmId2 = "two-${UUID.randomUUID().toString().take(6)}"
        val repo = HSMRepositoryImpl(emf, tenantId)

        repo.associate(
            "one-one",
            category,
            hsmId1,
            MasterKeyPolicy.SHARED
        )

        repo.associate(
            "one-two",
            category,
            hsmId1,
            MasterKeyPolicy.SHARED
        )

        repo.associate(
            "two-one",
            category,
            hsmId2,
            MasterKeyPolicy.SHARED
        )

        val loaded = repo.getHSMUsage()

        assertSoftly { assertions ->
            assertions.assertThat(loaded.size).isGreaterThanOrEqualTo(2)
            val one = loaded.singleOrNull { it.hsmId == hsmId1 }
            assertions.assertThat(one).isNotNull
            assertions.assertThat(one?.usages).isEqualTo(2)
            val two = loaded.singleOrNull { it.hsmId == hsmId2 }
            assertions.assertThat(two).isNotNull
            assertions.assertThat(two?.usages).isEqualTo(1)
        }
    }
}