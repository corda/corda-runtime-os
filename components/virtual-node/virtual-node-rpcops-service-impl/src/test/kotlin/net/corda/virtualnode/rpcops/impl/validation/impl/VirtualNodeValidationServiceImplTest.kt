package net.corda.virtualnode.rpcops.impl.validation.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.IllegalArgumentException

class VirtualNodeValidationServiceImplTest {
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val cpiInfoReadService = mock<CpiInfoReadService>()

    private val validationService = VirtualNodeValidationServiceImpl(virtualNodeInfoReadService, cpiInfoReadService)
    private val vnodeId = "aaaa1111bbbb"
    private val cpiFileChecksum = "SHA-256:2234567800"
    private val vnodeShortHash = ShortHash.of(vnodeId)
    private val cpiSecureHash = SecureHash.parse(cpiFileChecksum)

    @Test
    fun `validateVirtualNodeExists throws resource not found if vnode not found`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(vnodeShortHash)).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            validationService.validateAndGetVirtualNode(vnodeId)
        }
    }

    @Test
    fun `validateVirtualNodeExists does not throw when it finds vnode`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(vnodeShortHash)).thenReturn(mock())

        assertDoesNotThrow {
            validationService.validateAndGetVirtualNode(vnodeId)
        }
    }

    @Test
    fun `validateAndGetUpgradeCpi throws resource not found if cpi not found`() {
        whenever(cpiInfoReadService.getAll()).thenReturn(emptyList())

        assertThrows<ResourceNotFoundException> {
            validationService.validateAndGetCpiByChecksum(cpiFileChecksum)
        }
    }

    @Test
    fun `validateAndGetUpgradeCpi returns matching cpi`() {
        val mockCpi = mock<CpiMetadata> {
            whenever(it.fileChecksum).thenReturn(cpiSecureHash)
        }
        whenever(cpiInfoReadService.getAll()).thenReturn(listOf(mockCpi))

        val result = validationService.validateAndGetCpiByChecksum(cpiFileChecksum)
        assertThat(result).isEqualTo(mockCpi)
    }

    @Test
    fun `validateAndGetUpgradeCpi throws if multiple matching CPIs`() {
        val mockCpi1 = mock<CpiMetadata> {
            whenever(it.fileChecksum).thenReturn(cpiSecureHash)
        }
        val mockCpi2 = mock<CpiMetadata> {
            whenever(it.fileChecksum).thenReturn(cpiSecureHash)
        }
        whenever(cpiInfoReadService.getAll()).thenReturn(listOf(mockCpi1, mockCpi2))

        assertThrows<IllegalArgumentException> {
            validationService.validateAndGetCpiByChecksum(cpiFileChecksum)
        }
    }

    @Test
    fun `validateCpiUpgradePrerequisites CPI name must match`() {
        val cpiId1 = CpiIdentifier("name1", "v1", SecureHash.parse("SHA-256:1234567890"))
        val cpiId2 = CpiIdentifier("name2", "v2", SecureHash.parse("SHA-256:1234567890"))

        val currentCpi = mock<CpiMetadata> {
            whenever(it.cpiId).thenReturn(cpiId1)
        }
        val targetCpi = mock<CpiMetadata> {
            whenever(it.cpiId).thenReturn(cpiId2)
        }

        assertThrows<IllegalArgumentException> {
            validationService.validateCpiUpgradePrerequisites(currentCpi, targetCpi)
        }
    }

    @Test
    fun `validateCpiUpgradePrerequisites CPI signer summary must match`() {
        val cpiId1 = CpiIdentifier("name", "v1", SecureHash.parse("SHA-256:1234567890"))
        val cpiId2 = CpiIdentifier("name", "v2", SecureHash.parse("SHA-256:2234567800"))

        val currentCpi = mock<CpiMetadata> {
            whenever(it.cpiId).thenReturn(cpiId1)
        }
        val targetCpi = mock<CpiMetadata> {
            whenever(it.cpiId).thenReturn(cpiId2)
        }

        assertThrows<IllegalArgumentException> {
            validationService.validateCpiUpgradePrerequisites(currentCpi, targetCpi)
        }
    }
}