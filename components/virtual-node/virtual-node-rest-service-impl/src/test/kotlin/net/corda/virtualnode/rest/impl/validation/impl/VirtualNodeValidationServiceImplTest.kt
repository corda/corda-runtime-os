package net.corda.virtualnode.rest.impl.validation.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.parseSecureHash
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class VirtualNodeValidationServiceImplTest {
    private val now = Instant.now()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val cpiInfoReadService = mock<CpiInfoReadService>()
    private val validationService = VirtualNodeValidationServiceImpl(virtualNodeInfoReadService, cpiInfoReadService)
    private val vnodeId = "aaaa1111bbbb"
    private val cpiFileChecksum = "0123456789AB"
    private val cpiFileChecksumFull = "SHA-256:0123456789AB"
    private val vnodeShortHash = ShortHash.of(vnodeId)
    private val cpiSecureHash = parseSecureHash(cpiFileChecksumFull)
    private val mockVnode = mock<VirtualNodeInfo> {
        whenever(it.flowOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
        whenever(it.flowP2pOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
        whenever(it.flowStartOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
        whenever(it.vaultDbOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
    }

    @Test
    fun `validateVirtualNodeExists throws resource not found if vnode not found`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(vnodeShortHash)).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            validationService.validateAndGetVirtualNode(vnodeId)
        }
    }

    @Test
    fun `validateVirtualNodeExists throws bad request if virtual node is not in maintenance`() {
        val activeVnode = mock<VirtualNodeInfo> {
            whenever(it.vaultDbOperationalStatus).thenReturn(OperationalStatus.ACTIVE)
            whenever(it.flowP2pOperationalStatus).thenReturn(OperationalStatus.ACTIVE)
            whenever(it.flowStartOperationalStatus).thenReturn(OperationalStatus.ACTIVE)
            whenever(it.flowOperationalStatus).thenReturn(OperationalStatus.ACTIVE)
        }
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(vnodeShortHash)).thenReturn(activeVnode)

        assertThrows<BadRequestException> {
            validationService.validateAndGetVirtualNode(vnodeId)
        }
    }

    @Test
    fun `validateVirtualNodeExists does not throw when it finds vnode`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(vnodeShortHash)).thenReturn(mockVnode)

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
        val cpiId1 = CpiIdentifier("name1", "v1", parseSecureHash("SHA-256:1234567890"))
        val cpiId2 = CpiIdentifier("name2", "v2", parseSecureHash("SHA-256:1234567890"))

        val currentCpi = mock<CpiMetadata> {
            whenever(it.cpiId).thenReturn(cpiId1)
        }
        val targetCpi = mock<CpiMetadata> {
            whenever(it.cpiId).thenReturn(cpiId2)
        }

        assertThrows<BadRequestException> {
            validationService.validateCpiUpgradePrerequisites(currentCpi, targetCpi)
        }
    }

    @Test
    fun `validateCpiUpgradePrerequisites CPI signer summary must match`() {
        val cpiId1 = CpiIdentifier("name", "v1", parseSecureHash("SHA-256:1234567890"))
        val cpiId2 = CpiIdentifier("name", "v2", parseSecureHash("SHA-256:2234567800"))

        val currentCpi = mock<CpiMetadata> {
            whenever(it.cpiId).thenReturn(cpiId1)
        }
        val targetCpi = mock<CpiMetadata> {
            whenever(it.cpiId).thenReturn(cpiId2)
        }

        assertThrows<BadRequestException> {
            validationService.validateCpiUpgradePrerequisites(currentCpi, targetCpi)
        }
    }

    @Test
    fun `validate and get group id - invalid x500`() {
        val request = getExampleVirtualNodeRequest(x500Name = "abc")
        assertThrows<InvalidInputDataException> { validationService.validateAndGetGroupId(request) }
    }

    @Test
    fun `validate and get group id - throws if CPI checksum is valid 12 digit hex number`() {
        val invalidValues = listOf<String>(
            "0123456789A", // to short
            "0123456789ABC", // to long
            "0123456789AX", // right length not hex
        )
        invalidValues.forEach { example->
            val request = getExampleVirtualNodeRequest(cpiShortFileChecksum = example)
            assertThrows<InvalidInputDataException> { validationService.validateAndGetGroupId(request) }
        }
    }

    @Test
    fun `validate and get group id - vault DML must be provided when DDL set`() {
        val request = getExampleVirtualNodeRequest(vaultDmlConnection = null)
        assertThrows<InvalidInputDataException> { validationService.validateAndGetGroupId(request) }
    }

    @Test
    fun `validate and get group id - crypt DML must be provided when DDL set`() {
        val request = getExampleVirtualNodeRequest(cryptoDmlConnection = null)
        assertThrows<InvalidInputDataException> { validationService.validateAndGetGroupId(request) }
    }

    @Test
    fun `validate and get group id - uniqueness DML must be provided when DDL set`() {
        val request = getExampleVirtualNodeRequest(uniquenessDmlConnection = null)
        assertThrows<InvalidInputDataException> { validationService.validateAndGetGroupId(request) }
    }

    @Test
    fun `validate and get group id - CPI Meta data not found `() {
        val request = getExampleVirtualNodeRequest()
        whenever(cpiInfoReadService.getAll()).thenReturn(listOf())
        assertThrows<InvalidInputDataException> { validationService.validateAndGetGroupId(request) }
    }

    @Test
    fun `validate and get group id - CPI Meta missing group policy`() {
        val cpiMetadataInvalid = CpiMetadata(
            CpiIdentifier("name", "v1", SecureHashImpl("SHA-256", ByteArray(16))),
            cpiSecureHash,
            listOf(),
            null,
            1,
            now
        )
        val request = getExampleVirtualNodeRequest()
        whenever(cpiInfoReadService.getAll()).thenReturn(listOf(cpiMetadataInvalid))
        assertThrows<InternalServerException> { validationService.validateAndGetGroupId(request) }
    }

    @Test
    fun `validate and get group id - returns group ID`() {
        val cpiMetadata = CpiMetadata(
            CpiIdentifier("name", "v1", SecureHashImpl("SHA-256", ByteArray(16))),
            cpiSecureHash,
            listOf(),
            """{ "groupId":"grp1"}""",
            1,
            now
        )
        whenever(cpiInfoReadService.getAll()).thenReturn(listOf(cpiMetadata))
        val request = getExampleVirtualNodeRequest()
        assertThat(validationService.validateAndGetGroupId(request)).isEqualTo("grp1")
    }

    @Test
    fun `validate and get group id - returns new UUID when group ID is special MGM group`() {
        val cpiMetadata = CpiMetadata(
            CpiIdentifier("name", "v1", SecureHashImpl("SHA-256", ByteArray(16))),
            cpiSecureHash,
            listOf(),
            """{ "groupId":"CREATE_ID"}""",
            1,
            now
        )
        whenever(cpiInfoReadService.getAll()).thenReturn(listOf(cpiMetadata))
        val request = getExampleVirtualNodeRequest()
        // check a UUID is returned
        UUID.fromString(validationService.validateAndGetGroupId(request))
    }

    @Test
    fun `validate virtual node does not exists throws when matching virtual node found`() {
        val alice = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB")
        val holdingIdentity = HoldingIdentity(alice, "grp1")
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentity.shortHash))
            .thenReturn(mockVnode)

        val error = assertThrows<ResourceAlreadyExistsException> {
            validationService.validateVirtualNodeDoesNotExist(holdingIdentity)
        }

        assertThat(error.message)
            .isEqualTo(
                "Virtual Node 'HoldingIdentity(x500Name=CN=Alice, O=Alice Corp, L=LDN, C=GB, groupId=grp1)' already exists."
            )
    }

    @Test
    fun `validate virtual node does not exists passes when no matching virtual node found`() {
        val alice = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB")
        val holdingIdentity = HoldingIdentity(alice, "grp1")
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentity.shortHash))
            .thenReturn(null)

        validationService.validateVirtualNodeDoesNotExist(holdingIdentity)
    }

    @Suppress("LongParameterList")
    private fun getExampleVirtualNodeRequest(
        x500Name: String = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
        cpiShortFileChecksum: String = cpiFileChecksum,
        vaultDdlConnection: String? = "vddl",
        vaultDmlConnection: String? = "vdml",
        cryptoDdlConnection: String? = "cdml",
        cryptoDmlConnection: String? = "cddl",
        uniquenessDdlConnection: String? = "uddl",
        uniquenessDmlConnection: String? = "udml"
    ): CreateVirtualNodeRequest {
        return CreateVirtualNodeRequest(
            x500Name,
            cpiShortFileChecksum,
            vaultDdlConnection,
            vaultDmlConnection,
            cryptoDdlConnection,
            cryptoDmlConnection,
            uniquenessDdlConnection,
            uniquenessDmlConnection,
        )
    }
}