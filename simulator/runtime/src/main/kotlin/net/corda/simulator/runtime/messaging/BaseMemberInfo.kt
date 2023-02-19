package net.corda.simulator.runtime.messaging

import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import java.security.PublicKey

/**
 * Simple implementation of the MemberInfo class. Note most methods are unimplemented. This class is also
 * immutable (as with real Corda); if keys are added for a particular member then this will need to be re-retrieved from
 * [net.corda.v5.application.membership.MemberLookup].
 *
 * @param name The name of the member.
 * @param ledgerKeys Ledger keys generated for this member.
 */
data class BaseMemberInfo(
    private val name: MemberX500Name,
    private val ledgerKeys: List<PublicKey> = listOf(),
    private val memberContext: Map<String, String> = mapOf()
) : MemberInfo {

    override fun getMemberProvidedContext(): MemberContext {
        val layeredPropertyMap = SimLayeredPropertyMap(memberContext)
        return SimMemberContext(layeredPropertyMap)
    }

    override fun getMgmProvidedContext(): MGMContext {
        TODO("Not yet implemented")
    }

    override fun getName(): MemberX500Name = name

    override fun getSessionInitiationKey(): PublicKey {
        TODO("Not yet implemented")
    }

    override fun getLedgerKeys(): MutableList<PublicKey> = ledgerKeys.toMutableList()

    override fun getPlatformVersion(): Int {
        TODO("Not yet implemented")
    }

    override fun getSerial(): Long {
        TODO("Not yet implemented")
    }

    override fun isActive(): Boolean = true
}

class SimMemberContext(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map, MemberContext

class SimLayeredPropertyMap(
    private val properties: Map<String, String?>,
): LayeredPropertyMap {
    override fun getEntries(): MutableSet<MutableMap.MutableEntry<String, String>> {
        TODO("Not yet implemented")
    }

    override fun get(key: String): String? {
        return properties[key]
    }

    override fun <T : Any?> parse(key: String, clazz: Class<out T>): T & Any {
        TODO("Not yet implemented")
    }

    override fun <T> parseList(itemKeyPrefix: String, clazz: Class<out T>): List<T> {
        TODO("Not yet implemented")
    }
    override fun <T> parseOrNull(key: String, clazz: Class<out T>): T? {
        TODO("Not yet implemented")
    }
    override fun <T> parseSet(itemKeyPrefix: String, clazz: Class<out T>): Set<T> {
        TODO("Not yet implemented")
    }
}