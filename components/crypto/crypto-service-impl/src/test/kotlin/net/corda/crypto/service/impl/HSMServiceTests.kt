package net.corda.crypto.service.impl

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}