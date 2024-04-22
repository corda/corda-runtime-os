package net.corda.membership.lib

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import java.util.SortedMap

/**
 * Factory for building [MemberInfo] objects from different sources.
 * By using this factory, we do not need to depend on the member implementation classes to construct a layered property
 * map.
 */
interface MemberInfoFactory {
    /**
     * Builds a [MemberInfo] from two sorted context maps.
     *
     * @param memberContext Sorted map of the member provided context.
     * @param mgmContext Sorted map of the mgm provided context.
     *
     * @return A [MemberInfo] matching the input context maps.
     */
    fun createMemberInfo(
        memberContext: SortedMap<String, String?>,
        mgmContext: SortedMap<String, String?>
    ): MemberInfo

    /**
     * Builds a [MemberInfo] from a [MemberContext] and an [MGMContext].
     *
     * @param memberContext [MemberContext] object representing the member provided context.
     * @param mgmContext [MGMContext] object representing the mgm provided context.
     *
     * @return A [MemberInfo] matching the input contexts.
     */
    fun createMemberInfo(
        memberContext: MemberContext,
        mgmContext: MGMContext
    ): MemberInfo

    /**
     * Builds a [MemberInfo] from a [PersistentMemberInfo]. [PersistentMemberInfo] is the avro data type used to persist
     * the member info to the message bus.
     *
     * @param memberInfo [PersistentMemberInfo] object representing the full member information.
     *
     * @return A [MemberInfo] matching the input member information.
     */
    fun createMemberInfo(
        memberInfo: PersistentMemberInfo
    ): MemberInfo

    fun createMemberInfo(
        memberContext: ByteArray,
        mgmContext: ByteArray
    ): MemberInfo

    fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberContext: ByteArray,
        mgmContext: ByteArray,
        memberSignature: CryptoSignatureWithKey,
        memberSignatureSpec: CryptoSignatureSpec,
    ): PersistentMemberInfo

    @Suppress("LongParameterList")
    fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberContext: ByteArray,
        mgmContext: ByteArray,
        memberSignatureKey: ByteArray,
        memberSignatureContent: ByteArray,
        memberSignatureSpec: String,
    ): PersistentMemberInfo

    fun createPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberInfo: SelfSignedMemberInfo,
    ): PersistentMemberInfo

    /**
     * Should be only used when creating members on static networks or for MGM on dynamic networks.
     */
    fun createMgmOrStaticPersistentMemberInfo(
        viewOwningMember: HoldingIdentity,
        memberInfo: MemberInfo,
        memberSignature: CryptoSignatureWithKey,
        memberSignatureSpec: CryptoSignatureSpec,
    ): PersistentMemberInfo

    /**
     * Should be only used when creating members on static networks.
     */
    fun createStaticSelfSignedMemberInfo(
        memberInfo: MemberInfo,
        memberSignature: CryptoSignatureWithKey,
        memberSignatureSpec: CryptoSignatureSpec,
    ): SelfSignedMemberInfo

    fun createSelfSignedMemberInfo(
        memberContext: ByteArray,
        mgmContext: ByteArray,
        memberSignature: CryptoSignatureWithKey,
        memberSignatureSpec: CryptoSignatureSpec,
    ): SelfSignedMemberInfo
}
