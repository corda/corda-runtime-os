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
    override val name: MemberX500Name,
    override val ledgerKeys: List<PublicKey> = listOf(),
    private val memberContext: Map<String, String> = mapOf()
) : MemberInfo {

    override val isActive: Boolean = true
    override val memberProvidedContext: MemberContext
        get() {
            val layeredPropertyMap = SimLayeredPropertyMap(memberContext)
            return SimMemberContext(layeredPropertyMap)
        }
    override val mgmProvidedContext: MGMContext
        get() { TODO("Not yet implemented") }
    override val platformVersion: Int
        get() { TODO("Not yet implemented") }
    override val serial: Long
        get() { TODO("Not yet implemented") }
    override val sessionInitiationKey: PublicKey
        get() { TODO("Not yet implemented") }
}

class SimMemberContext(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map, MemberContext

class SimLayeredPropertyMap(
    private val properties: Map<String, String?>,
): LayeredPropertyMap {
    override val entries: Set<Map.Entry<String, String?>>
        get() = TODO("Not yet implemented")

    override fun get(key: String): String? {
        return properties[key]
    }
    override fun <T> parse(key: String, clazz: Class<out T>): T {
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