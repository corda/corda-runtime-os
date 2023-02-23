package net.corda.virtualnode.read.rpc.extensions

import java.time.Instant
import java.util.UUID
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.test.util.TestRandom
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VirtualNodeInfoRPCExtensionsTest {

    private companion object {
        const val INVALID_SHORT_HASH = "invalid"
        val VALID_SHORT_HASH = ShortHash.of("1234567890ab")
    }

    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()

    @Test
    fun `getByHoldingIdentityShortHashOrThrow returns VirtualNodeInfo if virtual node exists`() {
        val info = VirtualNodeInfo(
            createTestHoldingIdentity(
                MemberX500Name.parse("O=Alice, L=London, C=GB").toString(),
                UUID.randomUUID().toString()
            ),
            CpiIdentifier("TEST_CPI", "1.0", TestRandom.secureHash()),
            timestamp = Instant.now(),
            vaultDmlConnectionId = UUID(30, 0),
            cryptoDmlConnectionId = UUID(0, 0),
            uniquenessDmlConnectionId = UUID(0, 0)
        )

        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(VALID_SHORT_HASH))
            .thenReturn(info)

        val result1 = virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(VALID_SHORT_HASH)
        val result2 = virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(VALID_SHORT_HASH.value)
        val result3 = virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(VALID_SHORT_HASH) { "done" }
        val result4 = virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(VALID_SHORT_HASH.value) { "done" }

        assertEquals(info, result1)
        assertEquals(info, result2)
        assertEquals(info, result3)
        assertEquals(info, result4)
    }

    @Test
    fun `getByHoldingIdentityShortHashOrThrow throws ResourceNotFoundException if virtual node does not exist`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(VALID_SHORT_HASH)).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(VALID_SHORT_HASH)
        }
        assertThrows<ResourceNotFoundException> {
            virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(VALID_SHORT_HASH.value)
        }
        assertThrows<ResourceNotFoundException> {
            virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(VALID_SHORT_HASH) { "done" }
        }
        assertThrows<ResourceNotFoundException> {
            virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(VALID_SHORT_HASH.value) { "done" }
        }
    }

    @Test
    fun `getByHoldingIdentityShortHashOrThrow throws BadRequest short hash is invalid`() {
        assertThrows<BadRequestException> {
            virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(INVALID_SHORT_HASH)
        }
        assertThrows<BadRequestException> {
            virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(INVALID_SHORT_HASH) { "done" }
        }
    }

    @Test
    fun `ShortHash ofOrThrow returns ShortHash if valid`() {
        assertEquals(VALID_SHORT_HASH, ShortHash.ofOrThrow(VALID_SHORT_HASH.value))
    }

    @Test
    fun `ShortHash ofOrThrow throws BadRequest if ShortHash is invalid`() {
        assertThrows<BadRequestException> { ShortHash.ofOrThrow(INVALID_SHORT_HASH) }
    }

    @Test
    fun `ShortHash parseOrThrow returns ShortHash if valid`() {
        assertEquals(VALID_SHORT_HASH, ShortHash.parseOrThrow(VALID_SHORT_HASH.value))
    }

    @Test
    fun `ShortHash parseOrThrow throws BadRequest if ShortHash is invalid`() {
        assertThrows<BadRequestException> { ShortHash.parseOrThrow("${VALID_SHORT_HASH}AS3") }
    }
}
