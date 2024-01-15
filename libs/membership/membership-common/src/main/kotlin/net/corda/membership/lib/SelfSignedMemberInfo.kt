package net.corda.membership.lib

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.v5.membership.MemberInfo

interface SelfSignedMemberInfo : MemberInfo {
    val memberContextBytes: ByteArray
    val mgmContextBytes: ByteArray
    val memberSignature: CryptoSignatureWithKey
    val memberSignatureSpec: CryptoSignatureSpec
}
