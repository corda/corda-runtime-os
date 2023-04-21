package net.corda.membership.lib

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.v5.membership.MemberInfo

data class SignedMemberInfo (
    val memberInfo: MemberInfo,
    val memberSignature: CryptoSignatureWithKey,
    val memberSignatureSpec: CryptoSignatureSpec,
)