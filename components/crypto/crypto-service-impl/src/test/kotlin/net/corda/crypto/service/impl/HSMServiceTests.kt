package net.corda.crypto.service.impl

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_ALIASED
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.crypto.service.impl.infra.TestServicesFactory.Companion.CUSTOM1_HSM_ID
import net.corda.crypto.service.impl.infra.TestServicesFactory.Companion.CUSTOM2_HSM_ID
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HSMServiceTests {
    companion object {
        private fun assertHSMAssociation(
            association: HSMAssociationInfo?,
            tenantId: String,
            category: String,
            hsmId: String = SOFT_HSM_ID
        ) {
            assertNotNull(association)
            assertThat(association.id).isNotBlank
            assertEquals(tenantId, association.tenantId)
            assertEquals(hsmId, association.hsmId)
            assertEquals(category, association.category)
            assertThat(association.masterKeyAlias).isNotBlank
            assertEquals(0, association.deprecatedAt)
        }
    }

    private lateinit var factory: TestServicesFactory

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
    }

    @Test
    fun `Should assign SOFT HSM and retrieves assignment`() {
        val tenantId1 = UUID.randomUUID().toString()
        val tenantId2 = UUID.randomUUID().toString()

        val association11 = factory.hsmService.assignSoftHSM(tenantId1, CryptoConsts.Categories.LEDGER)
        assertHSMAssociation(association11, tenantId1, CryptoConsts.Categories.LEDGER)
        val foundAssociation11 = factory.hsmService.findAssignedHSM(tenantId1, CryptoConsts.Categories.LEDGER)
        assertHSMAssociation(foundAssociation11, tenantId1, CryptoConsts.Categories.LEDGER)

        assertNull(factory.hsmService.findAssignedHSM(tenantId1, CryptoConsts.Categories.TLS))
        assertNull(factory.hsmService.findAssignedHSM(UUID.randomUUID().toString(), CryptoConsts.Categories.LEDGER))

        val association12 = factory.hsmService.assignSoftHSM(tenantId1, CryptoConsts.Categories.TLS)
        assertHSMAssociation(association12, tenantId1, CryptoConsts.Categories.TLS)
        val foundAssociation12 = factory.hsmService.findAssignedHSM(tenantId1, CryptoConsts.Categories.TLS)
        assertHSMAssociation(foundAssociation12, tenantId1, CryptoConsts.Categories.TLS)
        assertEquals(
            foundAssociation11!!.masterKeyAlias,
            foundAssociation12!!.masterKeyAlias,
            "The master key alias must stay the same for the same tenant, even if categories are different"
        )

        val association21 = factory.hsmService.assignSoftHSM(tenantId2, CryptoConsts.Categories.LEDGER)
        assertHSMAssociation(association21, tenantId2, CryptoConsts.Categories.LEDGER)
        val foundAssociation21 = factory.hsmService.findAssignedHSM(tenantId2, CryptoConsts.Categories.LEDGER)
        assertHSMAssociation(foundAssociation21, tenantId2, CryptoConsts.Categories.LEDGER)
        assertNotEquals(
            foundAssociation11.masterKeyAlias,
            foundAssociation21!!.masterKeyAlias,
            "The master key alias must stay the same for the same tenant, even if categories are different"
        )
    }

    @Test
    fun `Should not fail assigning SOFT HSM twice`() {
        val tenantId = UUID.randomUUID().toString()
        val association1 = factory.hsmService.assignSoftHSM(tenantId, CryptoConsts.Categories.LEDGER)
        assertHSMAssociation(association1, tenantId, CryptoConsts.Categories.LEDGER)
        val association2 = factory.hsmService.assignSoftHSM(tenantId, CryptoConsts.Categories.LEDGER)
        assertHSMAssociation(association2, tenantId, CryptoConsts.Categories.LEDGER)
    }

    @Test
    fun `Should assign HSM and retrieve assignment`() {
        val tenantId1 = UUID.randomUUID().toString()
        val tenantId2 = UUID.randomUUID().toString()

        factory.hsmService.assignHSM(tenantId1, CryptoConsts.Categories.LEDGER, emptyMap())
        val usage1 = factory.hsmStore.getHSMUsage()
        assertThat(usage1).hasSize(1)
        assertThat(usage1.first().usages).isEqualTo(1)
        assertThat(usage1.first().hsmId).isIn(CUSTOM1_HSM_ID, CUSTOM2_HSM_ID)

        factory.hsmService.assignHSM(tenantId1, CryptoConsts.Categories.TLS, emptyMap())
        val usage2 = factory.hsmStore.getHSMUsage()
        assertThat(usage2).hasSize(2)
        assertThat(usage2).anyMatch { it.hsmId == CUSTOM1_HSM_ID && it.usages == 1 }
        assertThat(usage2).anyMatch { it.hsmId == CUSTOM2_HSM_ID && it.usages == 1 }

        factory.hsmService.assignHSM(tenantId2, CryptoConsts.Categories.LEDGER, emptyMap())
        val usage3 = factory.hsmStore.getHSMUsage()
        assertThat(usage3).hasSize(2)
        assertThat(usage3).anyMatch { it.hsmId == CUSTOM1_HSM_ID }
        assertThat(usage3).anyMatch { it.hsmId == CUSTOM2_HSM_ID }
        assertTrue(
            (usage3[0].usages == 2 && usage3[1].usages == 1) ||
                    (usage3[0].usages == 1 && usage3[1].usages == 2)
        )
    }

    @Test
    fun `Should assign HSM with preference to use ALIASED and retrieve assignment`() {
        val tenantId1 = UUID.randomUUID().toString()
        val tenantId2 = UUID.randomUUID().toString()

        factory.hsmService.assignHSM(
            tenantId1, CryptoConsts.Categories.LEDGER, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_ALIASED
            )
        )
        val usage1 = factory.hsmStore.getHSMUsage()
        assertThat(usage1).hasSize(1)
        assertThat(usage1.first().usages).isEqualTo(1)
        assertThat(usage1.first().hsmId).isEqualTo(CUSTOM1_HSM_ID)

        factory.hsmService.assignHSM(
            tenantId1, CryptoConsts.Categories.TLS, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_ALIASED
            )
        )
        val usage2 = factory.hsmStore.getHSMUsage()
        assertThat(usage2).hasSize(1)
        assertThat(usage2).anyMatch { it.hsmId == CUSTOM1_HSM_ID && it.usages == 2 }

        // should fall back to use another one as the exact match already full
        factory.hsmService.assignHSM(
            tenantId2, CryptoConsts.Categories.LEDGER, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_ALIASED
            )
        )
        val usage3 = factory.hsmStore.getHSMUsage()
        assertThat(usage3).hasSize(2)
        assertThat(usage3).anyMatch { it.hsmId == CUSTOM1_HSM_ID && it.usages == 2 }
        assertThat(usage3).anyMatch { it.hsmId == CUSTOM2_HSM_ID && it.usages == 1 }
    }

    @Test
    fun `Should assign HSM ignoring SOFT HSM stats and retrieve assignment`() {
        factory.hsmService.assignSoftHSM(UUID.randomUUID().toString(), CryptoConsts.Categories.LEDGER)

        val tenantId1 = UUID.randomUUID().toString()
        val tenantId2 = UUID.randomUUID().toString()

        factory.hsmService.assignHSM(tenantId1, CryptoConsts.Categories.LEDGER, emptyMap())
        val usage1 = factory.hsmStore.getHSMUsage()
        assertThat(usage1).hasSize(2)
        assertThat(usage1).anyMatch { it.hsmId == SOFT_HSM_ID && it.usages == 1 }
        assertThat(usage1).anyMatch { (it.hsmId == CUSTOM1_HSM_ID || it.hsmId == CUSTOM2_HSM_ID) && it.usages == 1 }

        factory.hsmService.assignHSM(tenantId1, CryptoConsts.Categories.TLS, emptyMap())
        val usage2 = factory.hsmStore.getHSMUsage()
        assertThat(usage2).hasSize(3)
        assertThat(usage1).anyMatch { it.hsmId == SOFT_HSM_ID && it.usages == 1 }
        assertThat(usage2).anyMatch { it.hsmId == CUSTOM1_HSM_ID && it.usages == 1 }
        assertThat(usage2).anyMatch { it.hsmId == CUSTOM2_HSM_ID && it.usages == 1 }

        factory.hsmService.assignHSM(tenantId2, CryptoConsts.Categories.LEDGER, emptyMap())
        val usage3 = factory.hsmStore.getHSMUsage()
        assertThat(usage3).hasSize(3)
        assertThat(usage1).anyMatch { it.hsmId == SOFT_HSM_ID && it.usages == 1 }
        assertThat(usage3).anyMatch { it.hsmId == CUSTOM1_HSM_ID }
        assertThat(usage3).anyMatch { it.hsmId == CUSTOM2_HSM_ID }
        assertTrue(
            (usage3[0].usages == 2 && usage3[1].usages == 1) ||
                    (usage3[0].usages == 1 && usage3[1].usages == 2)
        )
    }
}