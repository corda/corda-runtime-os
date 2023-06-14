package net.corda.membership.lib.impl

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SignedMemberInfo
import net.corda.v5.membership.MemberInfo

class SignedMemberInfoImpl(
    override val memberContextBytes: ByteArray,
    override val mgmContextBytes: ByteArray,
    override val memberSignature: CryptoSignatureWithKey,
    override val memberSignatureSpec: CryptoSignatureSpec,
    private val memberInfoFactory: MemberInfoFactory
) : SignedMemberInfo, MemberInfo by memberInfoFactory.createMemberInfo(memberContextBytes, mgmContextBytes) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedMemberInfoImpl

        if (!memberContextBytes.contentEquals(other.memberContextBytes)) return false
        if (!mgmContextBytes.contentEquals(other.mgmContextBytes)) return false
        if (memberSignature != other.memberSignature) return false
        if (memberSignatureSpec != other.memberSignatureSpec) return false
        if (memberInfoFactory != other.memberInfoFactory) return false

        return true
    }

    override fun hashCode(): Int {
        var result = memberContextBytes.contentHashCode()
        result = 31 * result + mgmContextBytes.contentHashCode()
        result = 31 * result + memberSignature.hashCode()
        result = 31 * result + memberSignatureSpec.hashCode()
        result = 31 * result + memberInfoFactory.hashCode()
        return result
    }
}