package net.corda.testing.sandboxes.impl

import java.security.PublicKey
import java.util.Collections.singletonList
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.SandboxSetup.Companion.CORDA_MEMBERSHIP_PID
import net.corda.testing.sandboxes.SandboxSetup.Companion.CORDA_MEMBER_COUNT
import net.corda.testing.sandboxes.SandboxSetup.Companion.CORDA_MEMBER_PUBLIC_KEY
import net.corda.testing.sandboxes.SandboxSetup.Companion.CORDA_MEMBER_X500_NAME
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Component.NAME
import org.osgi.service.component.annotations.Modified
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(configurationPid = [ CORDA_MEMBERSHIP_PID, NAME ])
@ServiceRanking(SandboxSetup.SANDBOX_SERVICE_RANKING)
class MembershipGroupReaderProviderImpl @Activate constructor(
    @Reference
    private val schemeMetadata: CipherSchemeMetadata,
    properties: Map<String, Any?>
): MembershipGroupReaderProvider {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val membership = LinkedHashMap(parseNetwork(properties))

    @Modified
    fun modified(properties: Map<String, Any?>) {
        val newGroupMembership = parseNetwork(properties)
        synchronized(membership) {
            membership.clear()
            membership.putAll(newGroupMembership)
        }
    }

    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader {
        val membershipSnapshot = synchronized(membership) {
            LinkedHashMap(membership)
        }
        return MembershipGroupReaderImpl(holdingIdentity, membershipSnapshot)
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Starting")
    }

    override fun stop() {
        logger.info("Stopping")
    }

    private fun parseNetwork(properties: Map<String, Any?>): Map<MemberX500Name, PublicKey> {
        return buildMap {
            val memberCount = properties[CORDA_MEMBER_COUNT] as? Int ?: 0
            for (idx in 0 until memberCount) {
                val memberX500Name = (properties["$CORDA_MEMBER_X500_NAME.$idx"] as? String)?.let(MemberX500Name::parse)
                val publicKey = (properties["$CORDA_MEMBER_PUBLIC_KEY.$idx"] as? ByteArray)?.let(schemeMetadata::decodePublicKey)

                if (memberX500Name != null && publicKey != null) {
                    this[memberX500Name] = publicKey
                }
            }
        }
    }

    private class MembershipGroupReaderImpl(
        holdingIdentity: HoldingIdentity,
        membership: Map<MemberX500Name, PublicKey>
    ) : MembershipGroupReader {
        private val members = membership.mapValues {
            object : MemberInfo {
                override fun isActive(): Boolean {
                    return true
                }
                override fun getLedgerKeys(): List<PublicKey> {
                    return singletonList(it.value)
                }
                override fun getMemberProvidedContext(): MemberContext {
                    TODO("Not yet implemented")
                }
                override fun getMgmProvidedContext(): MGMContext {
                    TODO("Not yet implemented")
                }
                override fun getName(): MemberX500Name {
                    return it.key
                }
                override fun getPlatformVersion(): Int {
                    return 999
                }
                override fun getSerial(): Long {
                    TODO("Not yet implemented")
                }
            }
        }
        override val groupId: String = holdingIdentity.groupId
        override val owningMember: MemberX500Name = holdingIdentity.x500Name

        override val groupParameters: InternalGroupParameters
            get() = TODO("groupParameters: Not yet implemented")

        override val signedGroupParameters: SignedGroupParameters
            get() = TODO("groupParameters: Not yet implemented")

        override fun lookup(filter: MembershipStatusFilter): Collection<MemberInfo> {
            return members.values
        }

        override fun lookupByLedgerKey(ledgerKeyHash: SecureHash, filter: MembershipStatusFilter): MemberInfo? {
            return null
        }

        override fun lookup(name: MemberX500Name, filter: MembershipStatusFilter): MemberInfo? {
            return members[name]
        }

        override fun lookupBySessionKey(sessionKeyHash: SecureHash, filter: MembershipStatusFilter): MemberInfo? {
            return null
        }

        override val notaryVirtualNodeLookup: NotaryVirtualNodeLookup
            get() = throw IllegalStateException("TEST MODULE: Membership not supported")
    }
}
