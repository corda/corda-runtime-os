package net.corda.crypto.softhsm.impl

import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.core.CryptoTenants
import java.time.Instant
import javax.persistence.EntityManager
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq

class HSMRepositoryImplTest {

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
        val hsmRepository = HSMRepositoryImpl(
            org.mockito.kotlin.mock {
                on { createEntityManager() } doReturn em
            },
            CRYPTO_TENANT_ID
        )

        val test = hsmRepository.findTenantAssociation("tenant", "category")
        assertThat(test).isNull()
        assertThat(tenantCap.allValues.single()).isEqualTo("tenant")
        assertThat(categoryCap.allValues.single()).isEqualTo("category")
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
        val hsmStore = HSMRepositoryImpl(
            org.mockito.kotlin.mock {
                on { createEntityManager() } doReturn em
            },
            CryptoTenants.CRYPTO
        )

        val expected = HSMAssociationInfo("1", "tenant", "hsm", "category", "master_key", 0)
        val test = hsmStore.findTenantAssociation("tenant", "category")
        assertThat(test).usingRecursiveComparison().isEqualTo(expected)
        assertThat(tenantCap.allValues.single()).isEqualTo("tenant")
        assertThat(categoryCap.allValues.single()).isEqualTo("category")
    }

    @Test
    fun getHSMUsage() {
    }

    @Test
    fun associate() {
    }
}