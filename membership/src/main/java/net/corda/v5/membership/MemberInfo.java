package net.corda.v5.membership;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;

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
 */
@CordaSerializable
public interface MemberInfo {
    /**
     * @return Context representing the member set data regarding this members information. Required data from this
     * context is parsed and returned via other class properties or extension properties internally.
     */
    @NotNull MemberContext getMemberProvidedContext();

    /**
     * @return Context representing the MGM set data regarding this members information. Required data from this context
     * is parsed and returned via other class properties or extension properties internally.
     */
    @NotNull MGMContext getMgmProvidedContext();

    /**
     * @return Member's X500 name. X500 name is unique within the group and cannot be changed while the membership
     * exists.
     */
    @NotNull MemberX500Name getName();

    /**
     * @return Member's session initiation key.
     */
    @NotNull PublicKey getSessionInitiationKey();

    /**
     * @return List of current and previous (rotated) ledger keys, which member can still use to sign unspent
     * transactions on ledger. The key at index 0 is always the latest added ledger key.
     */
    @NotNull List<PublicKey> getLedgerKeys();

    /**
     * @return Corda platform version that the member is running on.
     */
    int getPlatformVersion();

    /**
     * @return An arbitrary number incremented each time the [MemberInfo] is changed.
     */
    long getSerial();

    /**
     * @return True if the member is active. Otherwise, false.
     */
    boolean isActive();
}
