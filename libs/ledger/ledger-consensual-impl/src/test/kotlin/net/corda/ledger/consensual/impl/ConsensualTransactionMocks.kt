package net.corda.ledger.consensual.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.parse
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class ConsensualTransactionMocks {

    companion object {
        fun mockMemberLookup(): MemberLookup {
            val memberInfo: MemberInfo = mock()
            val memberLookup: MemberLookup = mock()
            val memberContext: MemberContext = mock()
            val id = makeHoldingIdentity()

            whenever(memberContext.parse<String>("corda.groupId")).thenReturn(id.groupId)
            whenever(memberInfo.platformVersion).thenReturn(888)
            whenever(memberInfo.memberProvidedContext).thenReturn(memberContext)
            whenever(memberInfo.groupId).thenReturn(id.groupId)
            whenever(memberInfo.name).thenReturn(id.x500Name)
            whenever(memberLookup.myInfo()).thenReturn(memberInfo)

            return memberLookup
        }

        private fun makeHoldingIdentity() = HoldingIdentity(
            MemberX500Name("mock-member", "r3", "CX"),
            "mock-group")

        fun mockCpiInfoReadService(): CpiInfoReadService {
            val service = mock<CpiInfoReadService>()
            val dummyHash = SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32) { 65 })

            whenever(service.get(any())).thenReturn(
                CpiMetadata(
                    CpiIdentifier("MockCpi", "3.1415-fake", null),
                    dummyHash,
                    listOf(
                        makeCpkMetadata(1, CordappType.CONTRACT),
                        makeCpkMetadata(2, CordappType.WORKFLOW),
                        makeCpkMetadata(3, CordappType.CONTRACT),
                    ),
                    null,
                    1,
                    Instant.now()
                )
            )
            return service
        }

        fun mockVirtualNodeInfoService(): VirtualNodeInfoReadService {
            val service: VirtualNodeInfoReadService = mock()
            val dummyUUID = UUID.randomUUID()
            val nodeInfo = VirtualNodeInfo(
                makeHoldingIdentity(),
                CpiIdentifier("MockCpi", "1", null),
                vaultDmlConnectionId = dummyUUID,
                cryptoDmlConnectionId = dummyUUID,
                uniquenessDmlConnectionId = dummyUUID,
                timestamp = Instant.now()
            )
            whenever(service.get(any())).thenReturn(nodeInfo)

            return service
        }

        private fun makeCpkMetadata(i: Int, cordappType: CordappType) = CpkMetadata(
            CpkIdentifier("MockCpk", "$i", null),
            CpkManifest(CpkFormatVersion(1, 1)),
            "mock-bundle-$i",
            emptyList(),
            emptyList(),
            CordappManifest(
                "mock-bundle-symbolic",
                "$i",
                1,
                1,
                cordappType,
                "mock-shortname",
                "r3",
                i,
                "None",
                emptyMap()
            ),
            CpkType.UNKNOWN,
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32) { i.toByte() }),
            emptySet(),
            Instant.now()
        )
    }
}