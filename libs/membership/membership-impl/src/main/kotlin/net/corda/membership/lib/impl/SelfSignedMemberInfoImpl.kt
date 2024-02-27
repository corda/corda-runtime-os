package net.corda.membership.lib.impl

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.v5.membership.MemberInfo
import java.util.Objects

class SelfSignedMemberInfoImpl(
    override val memberContextBytes: ByteArray,
    override val mgmContextBytes: ByteArray,
    override val memberSignature: CryptoSignatureWithKey,
    override val memberSignatureSpec: CryptoSignatureSpec,
    private val memberInfoFactory: MemberInfoFactory
) : SelfSignedMemberInfo, MemberInfo by memberInfoFactory.createMemberInfo(memberContextBytes, mgmContextBytes) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SelfSignedMemberInfoImpl

        if (!memberContextBytes.contentEquals(other.memberContextBytes)) return false
        if (!mgmContextBytes.contentEquals(other.mgmContextBytes)) return false
        if (memberSignature != other.memberSignature) return false
        if (memberSignatureSpec != other.memberSignatureSpec) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(memberContextBytes, mgmContextBytes, memberSignature, memberSignatureSpec)
    }
}
