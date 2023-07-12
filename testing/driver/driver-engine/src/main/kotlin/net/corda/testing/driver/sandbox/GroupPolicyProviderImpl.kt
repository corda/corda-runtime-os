package net.corda.testing.driver.sandbox

import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(
    service = [ GroupPolicyProvider::class ],
    property = [ DRIVER_SERVICE ]
)
@ServiceRanking(DRIVER_SERVICE_RANKING)
class GroupPolicyProviderImpl @Activate constructor(
    @Reference(target = DRIVER_SERVICE_FILTER)
    private val cryptoService: CryptoService,
    @Reference
    private val privateKeyService: PrivateKeyService,
    @Reference
    private val schemeMetadata: CipherSchemeMetadata
): GroupPolicyProvider {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val keyScheme = schemeMetadata.findKeyScheme(DEFAULT_KEY_SCHEME)

    private val groupPolicies = ConcurrentHashMap<HoldingIdentity, GroupPolicy>()

    private fun getKeySpec(tenantId: String): KeyGenerationSpec {
        return KeyGenerationSpec(keyScheme, "$tenantId-sessionInit", WRAPPING_KEY_ALIAS)
    }

    override fun getGroupPolicy(holdingIdentity: HoldingIdentity): GroupPolicy {
        return groupPolicies.computeIfAbsent(holdingIdentity) { hid ->
            val tenantId = hid.shortHash.value
            val sessionKey = cryptoService.generateKeyPair(getKeySpec(tenantId), mapOf(
                CRYPTO_TENANT_ID to tenantId,
                CRYPTO_CATEGORY to SESSION_INIT
            ))
            privateKeyService.store(sessionKey)
            val sessionContext = mapOf(
                FIRST_SESSION_KEY to schemeMetadata.encodeAsString(sessionKey.publicKey),
                CRYPTO_TENANT_ID to tenantId
            )

            object : GroupPolicy {
                override val fileFormatVersion: Int
                    get() = TODO("Not yet implemented")
                override val groupId = hid.groupId
                override val registrationProtocol: String
                    get() = TODO("Not yet implemented")
                override val synchronisationProtocol: String
                    get() = TODO("Not yet implemented")
                override val protocolParameters: GroupPolicy.ProtocolParameters
                    get() = TODO("Not yet implemented")
                override val p2pParameters: GroupPolicy.P2PParameters
                    get() = TODO("Not yet implemented")
                override val mgmInfo: GroupPolicy.MGMInfo = DriverMGMInfo(sessionContext)
                override val cipherSuite: GroupPolicy.CipherSuite = DriverCipherSuite(emptyMap())
            }
        }
    }

    override fun registerListener(name: String, callback: (HoldingIdentity, GroupPolicy) -> Unit) {
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }

    private class DriverMGMInfo(private val map: Map<String, String>)
        : GroupPolicy.MGMInfo, Map<String, String> by map

    private class DriverCipherSuite(private val map: Map<String, String>)
        : GroupPolicy.CipherSuite, Map<String, String> by map
}
