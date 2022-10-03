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
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MemberInfo
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class ConsensualTransactionMocks {

    companion object {
        fun mockMemberLookup(): MemberLookup {
            val memberInfo: MemberInfo = mock()
            whenever(memberInfo.platformVersion).thenReturn(888)

            val memberLookup: MemberLookup = mock()
            whenever(memberLookup.myInfo()).thenReturn(memberInfo)

            return memberLookup
        }

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

        fun makeCpkMetadata(i: Int, cordappType: CordappType) = CpkMetadata(
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