package net.corda.v5.membership

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

/**
 * The member information consists of two parts:
 * - Member provided context: Parameters added and signed by member as part of the initial MemberInfo proposal.
 * - MGM provided context: Parameters added by MGM as a part of member acceptance.
 *
 * Internally visible properties are accessible via extension properties.
 *
 * Example usages:
 *
 * ```java
 * MGMContext mgmContext = memberInfo.getMgmProvidedContext();
 * MemberContext memberContext = memberInfo.getMemberProvidedContext();
 * MemberX500Name x500Name = memberInfo.getName();
 * List<PublicKey> ledgerKeys = memberInfo.getLedgerKeys();
 * Long serial = memberInfo.getSerial();
 * int platformVersion = memberInfo.getPlatformVersion();
 * PublicKey sessionKey = memberInfo.getSessionInitiationKey();
 * Boolean isActive = memberInfo.isActive();
 * ```
 *
 * ```kotlin
 * val mgmContext: MGMContext = memberInfo.mgmProvidedContext
 * val memberContext: MemberContext = memberInfo.memberProvidedContext
 * val x500Name: MemberX500Name = memberInfo.name
 * val ledgerKeys: kotlin.collections.List<PublicKey> = memberInfo.ledgerKeys
 * val serial: Long = memberInfo.serial
 * val platformVersion: Int = memberInfo.platformVersion
 * val sessionKey: PublicKey = memberInfo.sessionInitiationKey
 * val isActive: Boolean = memberInfo.isActive
 * ```
 *
 * @property memberProvidedContext Context representing the member set data regarding this members information.
 * Required data from this context is parsed and returned via other class properties or extension properties
 * internally.
 * @property mgmProvidedContext Context representing the MGM set data regarding this members information.
 * Required data from this context is parsed and returned via other class properties or extension properties
 * internally.
 * @property name Member's X500 name.
 * X500 name is unique within the group and cannot be changed while the membership exists.
 * @property sessionInitiationKey Member's session initiation key.
 * @property ledgerKeys List of current and previous (rotated) ledger keys, which member can still use to sign unspent
 * transactions on ledger.
 * Key at index 0 is always the latest added ledger key.
 * @property platformVersion Corda platform version that the member is running on.
 * @property serial An arbitrary number incremented each time the [MemberInfo] is changed.
 * @property isActive True if the member is active. Otherwise, false.
 */
@CordaSerializable
interface MemberInfo {
    val memberProvidedContext: MemberContext
    val mgmProvidedContext: MGMContext
    val name: MemberX500Name
    val sessionInitiationKey: PublicKey
    val ledgerKeys: List<PublicKey>
    val platformVersion: Int
    val serial: Long
    val isActive: Boolean
}
