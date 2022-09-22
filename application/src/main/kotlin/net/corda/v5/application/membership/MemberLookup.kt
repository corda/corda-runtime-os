package net.corda.v5.application.membership

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import java.security.PublicKey

/**
 * [MemberLookup] allows flows to retrieve the [MemberInfo] for any member of the network, including itself.
 *
 * The platform will provide an instance of [MemberLookup] to flows via property injection.
 */
@DoNotImplement
interface MemberLookup {

    /**
     * Returns the [MemberInfo] for the calling flow.
     */
    @Suspendable
    fun myInfo(): MemberInfo

    /**
     * Returns the [MemberInfo] with the specific X500 name.
     *
     * @param name The [MemberX500Name] name of the member to retrieve.
     *
     * @return An instance of [MemberInfo] for the given [MemberX500Name] name or null if no member found.
     */
    @Suspendable
    fun lookup(name: MemberX500Name): MemberInfo?

    /**
     * Returns the [MemberInfo] with the specific public key.
     *
     * @param key The [PublicKey] of the member to retrieve.
     *
     * @return An instance of [MemberInfo] for the given [PublicKey] name or null if no member found.
     */
    @Suspendable
    fun lookup(key: PublicKey): MemberInfo?

    /**
     * Returns a list of [MemberInfo] for all members in the network.
     *
     * @return A list of [MemberInfo].
     */
    @Suspendable
    fun lookup(): List<MemberInfo>
}

