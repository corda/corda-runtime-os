package net.corda.crypto.service.impl

import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_NONE
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_WORKER_SET_ID
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.service.impl.infra.TestServicesFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HSMServiceTests {
    companion object {
        private fun assertHSMAssociation(tenantId: String, category: String, association: HSMTenantAssociation?) {
            assertNotNull(association)
            assertThat(association.id).isNotBlank
            assertEquals(SOFT_HSM_WORKER_SET_ID, association.worhsmIdkerSetId)
            assertEquals(0, association.deprecatedAt)
            assertEquals(tenantId, association.tenantId)
            assertEquals(category, association.category)
            assertNotNull(association.masterKeyAlias)
            assertThat(association.masterKeyAlias).isNotBlank
            assertThat(association.aliasSecret).isNotEmpty
        }
    }
    private lateinit var factory: TestServicesFactory
    private lateinit var service: HSMServiceImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        service = factory.hsmService
    }

    @Test
    fun `Should assign SOFT HSM with new master key alias and retrieves assignment`() {
        val tenantId1 = UUID.randomUUID().toString()
        val tenantId2 = UUID.randomUUID().toString()
        val association11 = service.assignSoftHSM(tenantId1, CryptoConsts.Categories.LEDGER)
        assertEquals(SOFT_HSM_WORKER_SET_ID, association11.hsmId)
        assertEquals(0, association11.deprecatedAt)
        val foundAssociation11 = service.findAssignedHSM(tenantId1, CryptoConsts.Categories.LEDGER)
        assertHSMAssociation(tenantId1, CryptoConsts.Categories.LEDGER, foundAssociation11)
        assertNull(service.findAssignedHSM(tenantId1, CryptoConsts.Categories.TLS))
        assertNull(service.findAssignedHSM(UUID.randomUUID().toString(), CryptoConsts.Categories.LEDGER))
        val association12 = service.assignSoftHSM(tenantId1, CryptoConsts.Categories.TLS)
        assertEquals(SOFT_HSM_WORKER_SET_ID, association12.hsmId)
        assertEquals(0, association12.deprecatedAt)
        val foundAssociation12 = service.findAssignedHSM(tenantId1, CryptoConsts.Categories.TLS)
        assertHSMAssociation(tenantId1, CryptoConsts.Categories.TLS, foundAssociation12)
        assertEquals(
            foundAssociation11!!.masterKeyAlias,
            foundAssociation12!!.masterKeyAlias,
            "The master key alias must stay the same for the same tenant, even if categories are different"
        )
        val association21 = service.assignSoftHSM(tenantId2, CryptoConsts.Categories.LEDGER)
        assertEquals(SOFT_HSM_WORKER_SET_ID, association21.hsmId)
        assertEquals(0, association21.deprecatedAt)
        val foundAssociation21 = service.findAssignedHSM(tenantId2, CryptoConsts.Categories.LEDGER)
        assertHSMAssociation(tenantId2, CryptoConsts.Categories.TLS, foundAssociation21)
        assertNotEquals(
            foundAssociation11.masterKeyAlias,
            foundAssociation21!!.masterKeyAlias,
            "The master key alias must stay the same for the same tenant, even if categories are different"
        )
    }

    @Test
    fun `Should not fail assigning SOFT HSM twice`() {
        val tenantId1 = UUID.randomUUID().toString()
        val association1 = service.assignSoftHSM(tenantId1, CryptoConsts.Categories.LEDGER)
        assertEquals(SOFT_HSM_WORKER_SET_ID, association1.hsmId)
        assertEquals(0, association1.deprecatedAt)
        val association2 = service.assignSoftHSM(tenantId1, CryptoConsts.Categories.LEDGER)
        assertEquals(SOFT_HSM_WORKER_SET_ID, association2.hsmId)
        assertEquals(0, association2.deprecatedAt)
    }

    @Test
    fun `Should assign HSM with new master key alias and then retrieve assignment`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NEW,
            null,
            7,
            57,
            factory.softHSMSupportedSchemas,
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        val configId = service.putHSMConfig(info, serviceConfig)
        service.linkCategories(
            configId,
            CryptoConsts.Categories.all.map {
                HSMCategoryInfo(it, PrivateKeyPolicy.ALIASED)
            }
        )
        val tenantId1 = UUID.randomUUID().toString()
        val hsm1 = service.assignHSM(
            tenantId1, CryptoConsts.Categories.LEDGER, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_NONE
            )
        )
        assert(configId, info, hsm1)
        val association1 = service.findAssignedHSM(tenantId1, CryptoConsts.Categories.LEDGER)
        assert(configId, tenantId1, hsm1,  association1)
        val hsm2 = service.assignHSM(
            tenantId1, CryptoConsts.Categories.TLS, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_NONE
            )
        )
        assert(configId, hsm1, hsm2)
        val association2 = service.findAssignedHSM(tenantId1, CryptoConsts.Categories.TLS)
        assertNotNull(association2)
        assertEquals(tenantId1, association2.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, association2.category)
        assertNotNull(association2.aliasSecret)
        assertNotNull(association2.masterKeyAlias)
        assertEquals(
            association1!!.masterKeyAlias,
            association2.masterKeyAlias,
            "The master key alias must stay the same for the same tenant, even if categories are different"
        )
        assertThat(factory.softCache.keys).containsKey(association2.masterKeyAlias)
        assert(configId, hsm1, "{}".toByteArray(), association2.config)
        val tenantId2 = UUID.randomUUID().toString()
        val hsm3 = service.assignHSM(
            tenantId2, CryptoConsts.Categories.TLS, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_NONE
            )
        )
        assert(configId, hsm1, hsm3)
        val association3 = service.findAssignedHSM(tenantId2, CryptoConsts.Categories.TLS)
        assertNotNull(association3)
        assertEquals(tenantId2, association3.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, association3.category)
        assertNotNull(association3.aliasSecret)
        assertNotNull(association3.masterKeyAlias)
        assertNotEquals(
            association1.masterKeyAlias,
            association3.masterKeyAlias,
            "The master key alias must be different for the different tenants"
        )
        assertThat(factory.softCache.keys).containsKey(association3.masterKeyAlias)
        assert(configId, hsm1, "{}".toByteArray(), association3.config)

        // next one to the one which has less associated tenants

        val info2 = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NEW,
            null,
            7,
            57,
            factory.softHSMSupportedSchemas,
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig2 = "{}".toByteArray()
        val configId2 = service.putHSMConfig(info2, serviceConfig2)
        service.linkCategories(
            configId2,
            CryptoConsts.Categories.all.map {
                HSMCategoryInfo(it, PrivateKeyPolicy.ALIASED)
            }
        )
        val tenantId3 = UUID.randomUUID().toString()
        val hsm4 = service.assignHSM(
            tenantId3, CryptoConsts.Categories.TLS, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_NONE
            )
        )
        assert(configId2, info2, hsm4)
        val association4 = service.findAssignedHSM(tenantId3, CryptoConsts.Categories.TLS)
        assertNotNull(association4)
        assertEquals(tenantId3, association4.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, association4.category)
        assertNotNull(association4.aliasSecret)
        assertNotNull(association4.masterKeyAlias)
        assertNotEquals(
            association1.masterKeyAlias,
            association4.masterKeyAlias,
            "The master key alias must be different for the different tenants"
        )
        assertThat(factory.softCache.keys).containsKey(association4.masterKeyAlias)
        assert(configId2, info2, "{}".toByteArray(), association4.config)
    }
}