package net.corda.crypto.softhsm.impl

import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.Collections.emptyList
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction

class HSMRepositoryImplTest {

    companion object {
        @JvmStatic
        private fun masterKeyPolicies() = listOf(MasterKeyPolicy.UNIQUE, MasterKeyPolicy.SHARED, MasterKeyPolicy.NONE)
    }

    @Test
    fun `findTenantAssociation returns null when there are no results`() {
        val tenantCap = argumentCaptor<String>()
        val categoryCap = argumentCaptor<String>()
        val em = org.mockito.kotlin.mock<EntityManager> {
            on { createQuery(any(), eq(HSMCategoryAssociationEntity::class.java)) } doAnswer {
                org.mockito.kotlin.mock {
                    on { setParameter(eq("tenantId"), tenantCap.capture()) } doReturn it
                    on { setParameter(eq("category"), categoryCap.capture()) } doReturn it
                    on { resultList } doReturn emptyList()
                }
            }
        }
        HSMRepositoryImpl(
            org.mockito.kotlin.mock {
                on { createEntityManager() } doReturn em
            }
        ).use { hsmRepository ->
            val test = hsmRepository.findTenantAssociation("tenant", "category")
            assertThat(test).isNull()
            assertThat(tenantCap.allValues.single()).isEqualTo("tenant")
            assertThat(categoryCap.allValues.single()).isEqualTo("category")
        }
    }

    @Test
    fun `findTenantAssociation returns first value when there are results`() {
        val hsmAssociation1 = HSMAssociationEntity("2", "tenant", "hsm", Instant.ofEpochMilli(0), "master_key")
        val hsmCategoryAssociation1 = HSMCategoryAssociationEntity("1", "tenant", "category", hsmAssociation1, Instant.ofEpochMilli(0), 0)
        val hsmAssociation2 = HSMAssociationEntity("2", "tenant", "hsm", Instant.ofEpochMilli(0), "master_key")
        val hsmCategoryAssociation2 = HSMCategoryAssociationEntity("2", "tenant", "category", hsmAssociation2, Instant.ofEpochMilli(0), 0)
        val tenantCap = argumentCaptor<String>()
        val categoryCap = argumentCaptor<String>()
        val em = org.mockito.kotlin.mock<EntityManager> {
            on { createQuery(any(), eq(HSMCategoryAssociationEntity::class.java)) } doAnswer {
                org.mockito.kotlin.mock {
                    on { setParameter(eq("tenantId"), tenantCap.capture()) } doReturn it
                    on { setParameter(eq("category"), categoryCap.capture()) } doReturn it
                    on { resultList } doReturn listOf(hsmCategoryAssociation1, hsmCategoryAssociation2)
                }
            }
        }
        HSMRepositoryImpl(
            org.mockito.kotlin.mock {
                on { createEntityManager() } doReturn em
            }
        ).use { hsmStore ->
            val expected = HSMAssociationInfo("1", "tenant", "hsm", "category", "master_key", 0)
            val test = hsmStore.findTenantAssociation("tenant", "category")
            assertThat(test).usingRecursiveComparison().isEqualTo(expected)
            assertThat(tenantCap.allValues.single()).isEqualTo("tenant")
            assertThat(categoryCap.allValues.single()).isEqualTo("category")
        }
    }

    @Test
    fun getHSMUsage() {
    }

    @Test
    fun associate() {
    }

    @ParameterizedTest
    @MethodSource("masterKeyPolicies")
    fun `createOrLookupCategoryAssociation returns existing associations`(masterKeyPolicy: MasterKeyPolicy) {
        val (entityCap, repo) = makeMockedInstance(true)
        val association = repo.createOrLookupCategoryAssociation("tenant", "hsm", masterKeyPolicy)
        assertThat(entityCap.allValues.size).isEqualTo(0)
        assertThat(association.masterKeyAlias).isEqualTo("master_key")
    }

    @ParameterizedTest
    @MethodSource("masterKeyPolicies")
    fun `createOrLookupCategoryAssociation creates appropriate associations`(masterKeyPolicy: MasterKeyPolicy) {
        val (entityCap, repo) = makeMockedInstance(false)
        val association = repo.createOrLookupCategoryAssociation("tenant", "hsm", masterKeyPolicy)
        assertThat(entityCap.allValues.size).isEqualTo(1)
        when (masterKeyPolicy) {
            MasterKeyPolicy.UNIQUE -> assertThat(association.masterKeyAlias).isNotNull()
            MasterKeyPolicy.SHARED, MasterKeyPolicy.NONE-> assertThat(association.masterKeyAlias).isNull()
        }
    }

    private fun makeMockedInstance(associationsExist: Boolean): Pair<KArgumentCaptor<HSMCategoryAssociationEntity>, HSMRepositoryImpl> {
        val entityCap = argumentCaptor<HSMCategoryAssociationEntity>()

        val hsmAssociation1 = HSMAssociationEntity("2", "tenant", "hsm", Instant.ofEpochMilli(0), "master_key")
        val hsmCategoryAssociation1 =
            HSMCategoryAssociationEntity("1", "tenant", "category", hsmAssociation1, Instant.ofEpochMilli(0), 0)

        val et = mock<EntityTransaction>()
        val em = mock<EntityManager> {
            on { createQuery(any(), eq(HSMAssociationEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(any<String>(), any()) } doReturn it
                    on { resultList } doReturn (if (associationsExist) listOf(hsmAssociation1)  else listOf())
                }
            }
            on { createQuery(any(), eq(HSMCategoryAssociationEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(any<String>(), any()) } doReturn it
                    on { resultList } doReturn (if (associationsExist) listOf(hsmCategoryAssociation1) else listOf())
                }
            }
            on { transaction } doReturn et
            on { merge(entityCap.capture()) } doAnswer { entityCap.lastValue }
        }
        val repo = HSMRepositoryImpl(
            mock {
                on { createEntityManager() } doReturn em
            }
        )
        return Pair(entityCap, repo)
    }
}