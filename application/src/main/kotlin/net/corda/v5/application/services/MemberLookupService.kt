package net.corda.v5.application.services

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import java.security.PublicKey

/**
 * Group Manager Service contains list of identities in Membership Management Group along with information about their identity keys,
 * services they provide and host names or IP addresses where they can be connected to. The cache wraps around a list fetched from the
 * manager, and adds easy lookup of the data stored within it. Generally it would be initialised with a specific Group Manager,
 * which it fetches data from and then subscribes to updates of.
 *
 */
@DoNotImplement
interface MemberLookupService : CordaFlowInjectable, CordaServiceInjectable {

    /** Returns our own [MemberInfo] **/
    fun myInfo(): MemberInfo

    /** Returns the [MemberInfo]  inside Membership Group with the given [name] name, or null if no such identity is found.**/
    fun lookup(name: MemberX500Name): MemberInfo?

    /** Returns the [MemberInfo] inside Membership Group with the given [key] public key, or null if no such identity is found.**/
    fun lookup(key: PublicKey): MemberInfo?

    /** Returns all [MemberInfo]s in the Group the node is currently aware of (including ourselves). **/
    fun lookup(): List<MemberInfo>

}

