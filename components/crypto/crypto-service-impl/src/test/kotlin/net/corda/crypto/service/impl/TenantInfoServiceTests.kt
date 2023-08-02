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

class TenantInfoServiceTests {
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
        val association11 = factory.tenantInfoService.populate(tenantId1, CryptoConsts.Categories.LEDGER, factory.cryptoService)
        assertHSMAssociation(association11, tenantId1, CryptoConsts.Categories.LEDGER)
        val foundAssociation11 = factory.tenantInfoService.lookup(tenantId1, CryptoConsts.Categories.LEDGER)
        assertHSMAssociation(foundAssociation11, tenantId1, CryptoConsts.Categories.LEDGER)

        assertNull(factory.tenantInfoService.lookup(tenantId1, CryptoConsts.Categories.TLS))
        assertNull(factory.tenantInfoService.lookup(UUID.randomUUID().toString(), CryptoConsts.Categories.LEDGER))

        val association12 = factory.tenantInfoService.populate(tenantId1, CryptoConsts.Categories.TLS, factory.cryptoService)
        assertHSMAssociation(association12, tenantId1, CryptoConsts.Categories.TLS)
        val foundAssociation12 = factory.tenantInfoService.lookup(tenantId1, CryptoConsts.Categories.TLS)
        assertHSMAssociation(foundAssociation12, tenantId1, CryptoConsts.Categories.TLS)
        assertEquals(
            foundAssociation11!!.masterKeyAlias,
            foundAssociation12!!.masterKeyAlias,
            "The master key alias must stay the same for the same tenant, even if categories are different"
        )

        val association21 = factory.tenantInfoService.populate(tenantId2, CryptoConsts.Categories.LEDGER, factory.cryptoService)
        assertHSMAssociation(association21, tenantId2, CryptoConsts.Categories.LEDGER)
        val foundAssociation21 = factory.tenantInfoService.lookup(tenantId2, CryptoConsts.Categories.LEDGER)
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
        val association1 = factory.tenantInfoService.populate(tenantId, CryptoConsts.Categories.LEDGER, factory.cryptoService)
        assertHSMAssociation(association1, tenantId, CryptoConsts.Categories.LEDGER)
        val association2 = factory.tenantInfoService.populate(tenantId, CryptoConsts.Categories.LEDGER, factory.cryptoService)
        assertHSMAssociation(association2, tenantId, CryptoConsts.Categories.LEDGER)
    }
}