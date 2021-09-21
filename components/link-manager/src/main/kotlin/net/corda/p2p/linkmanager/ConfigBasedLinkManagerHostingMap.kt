package net.corda.p2p.linkmanager

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.CONFIG_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITIES_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_GPOUP_ID
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_X500_NAME
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger
import java.lang.IllegalStateException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Reads the set of holding identities hosted locally from configuration.
 */
class ConfigBasedLinkManagerHostingMap(private val configReadService: ConfigReadService): LinkManagerHostingMap, Lifecycle {

    private val locallyHostedIdentities = mutableSetOf<LinkManagerNetworkMap.HoldingIdentity>()
    private val configListener = HostingMapConfigListener(locallyHostedIdentities)

    private var configRegistration: AutoCloseable? = null

    private val lock = ReentrantReadWriteLock()
    @Volatile
    private var running: Boolean = false

    override val isRunning: Boolean
        get() = running

    override fun start() {
        lock.write {
            if (!running) {
                configRegistration = configReadService.registerCallback(configListener)
                configListener.waitUntilFirstConfig()
                running = true
            }
        }
    }

    override fun stop() {
        lock.write {
            if (running) {
                configRegistration!!.close()
                running = false
            }
        }
    }

    override fun isHostedLocally(identity: LinkManagerNetworkMap.HoldingIdentity): Boolean {
        lock.read {
            if (!running) {
                throw IllegalStateException("isHostedLocally operation invoked while component was stopped.")
            }

            return locallyHostedIdentities.contains(identity)
        }
    }

    private class HostingMapConfigListener(val locallyHostedIdentities: MutableSet<LinkManagerNetworkMap.HoldingIdentity>):
        ConfigListener {

        companion object {
            private val log = contextLogger()
        }

        private val latch = CountDownLatch(1)

        override fun onUpdate(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config>) {
            if (changedKeys.contains(CONFIG_KEY)) {
                val linkManagerConfig = currentConfigurationSnapshot[CONFIG_KEY]!!
                val holdingIdentities = linkManagerConfig.getConfigList(LOCALLY_HOSTED_IDENTITIES_KEY).map { config ->
                    val x500name = config.getString(LOCALLY_HOSTED_IDENTITY_X500_NAME)
                    val groupId = config.getString(LOCALLY_HOSTED_IDENTITY_GPOUP_ID)
                    LinkManagerNetworkMap.HoldingIdentity(x500name, groupId)
                }
                locallyHostedIdentities.clear()
                locallyHostedIdentities.addAll(holdingIdentities)
                latch.countDown()
                log.info("Received new configuration. Locally hosted identities are: $holdingIdentities.")
            }
        }

        /**
         * Waits until the first config is received.
         * This will eventually be converted to be completely reactive, as part of: https://r3-cev.atlassian.net/browse/CORE-2463
         * Until then, this is a temporary workaround.
         */
        fun waitUntilFirstConfig() {
            latch.await()
        }

    }

}