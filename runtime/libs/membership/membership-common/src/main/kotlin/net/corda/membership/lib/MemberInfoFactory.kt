package net.corda.membership.lib

import net.corda.data.membership.PersistentMemberInfo
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import java.util.*

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
    fun create(
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
    fun create(
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
    fun create(
        memberInfo: PersistentMemberInfo
    ): MemberInfo
}