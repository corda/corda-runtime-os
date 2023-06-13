package net.corda.testing.driver.sandbox

import java.net.URL
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import java.time.Duration
import java.util.Collections.unmodifiableSet
import java.util.Hashtable
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpk.read.CpkReadService
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.orm.DatabaseTypeProvider
import net.corda.sandbox.SandboxCreationService
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE_FILTER
import net.corda.testing.driver.node.EmbeddedNodeService
import net.corda.testing.driver.sandbox.VirtualNodeLoader.Companion.VNODE_LOADER_NAME
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.ComponentConstants.COMPONENT_NAME
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(
    reference = [
        Reference(
            name = EmbeddedNodeServiceImpl.CPK_READER_NAME,
            service = CpkReadService::class,
            target = DRIVER_SERVICE_FILTER,
            cardinality = OPTIONAL,
            policy = DYNAMIC
        )
    ],
    property = [ DRIVER_SERVICE ],
    immediate = true
)
class EmbeddedNodeServiceImpl @Activate constructor(
    @Reference
    private val configAdmin: ConfigurationAdmin,
    @Reference
    private val scr: ServiceComponentRuntime,
    @Reference
    private val sandboxCreator: SandboxCreationService,
    private val componentContext: ComponentContext
) : EmbeddedNodeService {
    companion object {
        const val CPK_READER_NAME = "CpkReadService"

        private const val NON_DRIVER_COMPONENT_FILTER = "(&($COMPONENT_NAME=*)(!$DRIVER_SERVICE_FILTER))"
        private const val VNODE_LOADER_FILTER = "($COMPONENT_NAME=$VNODE_LOADER_NAME)"
        private const val DRIVER_CACHE_NAME = "corda-driver-cache"

        // The names of the bundles to place as public bundles in the sandbox service's platform sandbox.
        private val PLATFORM_PUBLIC_BUNDLE_NAMES: Set<String> = unmodifiableSet(setOf(
            "co.paralleluniverse.quasar-core.framework.extension",
            "com.esotericsoftware.reflectasm",
            "javax.persistence-api",
            "jcl.over.slf4j",
            "net.corda.application",
            "net.corda.base",
            "net.corda.crypto",
            "net.corda.crypto-extensions",
            "net.corda.ledger-consensual",
            "net.corda.ledger-utxo",
            "net.corda.membership",
            "net.corda.persistence",
            "net.corda.serialization",
            "org.apache.aries.spifly.dynamic.framework.extension",
            "org.apache.felix.framework",
            "org.hibernate.orm.core",
            "org.jetbrains.kotlin.osgi-bundle",
            "slf4j.api"
        ))

        private val REPLACEMENT_SERVICES = unmodifiableSet(setOf(
            CpiInfoReadService::class.java,
            CpkReadService::class.java,
            DatabaseTypeProvider::class.java,
            DbConnectionManager::class.java,
            GroupPolicyProvider::class.java,
            MembershipGroupReaderProvider::class.java,
            SandboxGroupContextComponent::class.java,
            WrappingRepository::class.java
        ))
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val serviceFactory = ServiceFactoryImpl(componentContext.bundleContext)
    private lateinit var virtualNodeService: VirtualNodeService

    /**
     * Disables the [CpkReadService] component to unload all CPIs.
     * We must ensure this happens before JUnit tries to remove the
     * temporary directory.
     */
    @Deactivate
    fun shutdown() {
        serviceFactory.shutdown()

        /**
         * Deactivate the [CpkReadService] and then wait
         * for the framework to unregister it.
         */
        with(componentContext) {
            disableComponent(CpiLoader.COMPONENT_NAME)
            while (locateService<CpkReadService>(CPK_READER_NAME) != null) {
                Thread.sleep(WAIT_MILLIS)
            }
        }

        logger.info("Shutdown complete")
    }

    override fun configure(frameworkDirectory: Path, timeout: Duration) {
        // We are replacing these Corda services with our own versions.
        REPLACEMENT_SERVICES.forEach(::disableNonDriverServices)

        configAdmin.getConfiguration(CpiLoader.COMPONENT_NAME)?.also { config ->
            val properties = Hashtable<String, Any?>()
            properties[CpiLoader.FRAMEWORK_DIRECTORY_KEY] = frameworkDirectory.toString()
            properties[CpiLoader.CACHE_DIRECTORY_KEY] = frameworkDirectory.parent.resolve(DRIVER_CACHE_NAME).toString()
            config.update(properties)
        }

        val (publicBundles, privateBundles) = componentContext.bundleContext.bundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreator.createPublicSandbox(publicBundles, privateBundles)

        virtualNodeService = serviceFactory.getService(VirtualNodeService::class.java, null, timeout)
    }

    private fun disableNonDriverServices(serviceType: Class<*>) {
        with(componentContext) {
            bundleContext.getServiceReferences(serviceType, NON_DRIVER_COMPONENT_FILTER).forEach { svcRef ->
                val componentName = svcRef.properties[COMPONENT_NAME] ?: return@forEach
                scr.getComponentDescriptionDTO(svcRef.bundle, componentName.toString())?.also { dto ->
                    logger.info("Found and disabling service: {}", componentName)
                    scr.disableComponent(dto)
                }
            }
        }
    }

    override fun loadVirtualNodes(fileURL: URL): Set<VirtualNodeInfo> {
        return virtualNodeService.loadVirtualNodes(fileURL.toString())
            .mapTo(linkedSetOf(), net.corda.virtualnode.VirtualNodeInfo::toAvro)
    }

    override fun setMembershipGroup(network: Map<MemberX500Name, PublicKey>) {
        configAdmin.getConfiguration(CORDA_MEMBERSHIP_PID)?.also { config ->
            val properties = Hashtable<String, Any>()
            properties[CORDA_MEMBER_COUNT] = network.size
            network.entries.forEachIndexed { idx, entry ->
                properties["$CORDA_MEMBER_X500_NAME.$idx"] = entry.key.toString()
                properties["$CORDA_MEMBER_PUBLIC_KEY.$idx"] = entry.value.encoded
            }
            config.update(properties)
        }
    }

    override fun setLocalIdentities(localMembers: Set<MemberX500Name>, localKeys: Map<MemberX500Name, KeyPair>) {
        configAdmin.getConfiguration(CORDA_LOCAL_IDENTITY_PID, "?")?.also { config ->
            val properties = Hashtable<String, Any>()
            properties[CORDA_MEMBER_COUNT] = localMembers.size
            localMembers.forEachIndexed { idx, localMember ->
                properties["$CORDA_MEMBER_X500_NAME.$idx"] = localMember.toString()
                localKeys[localMember]?.also { keyPair ->
                    properties["$CORDA_MEMBER_PRIVATE_KEY.$idx"] = keyPair.private.encoded
                    properties["$CORDA_MEMBER_PUBLIC_KEY.$idx"] = keyPair.public.encoded
                }
            }
            config.update(properties)
        }
    }

    override fun configureLocalTenants(timeout: Duration) {
        val localTenants = serviceFactory.getService(VirtualNodeInfoReadService::class.java, VNODE_LOADER_FILTER, timeout).getAll()
            .map(net.corda.virtualnode.VirtualNodeInfo::holdingIdentity)
            .associate { hid ->
                hid.shortHash.value to hid.x500Name
            }
        if (localTenants.isNotEmpty()) {
            configAdmin.getConfiguration(CORDA_LOCAL_TENANCY_PID, "?")?.also { config ->
                val properties = Hashtable<String, Any>()
                properties[CORDA_TENANT_COUNT] = localTenants.size
                localTenants.entries.forEachIndexed { idx, entry ->
                    properties["$CORDA_TENANT.$idx"] = entry.key
                    properties["$CORDA_TENANT_MEMBER.$idx"] = entry.value.toString()
                }
                config.update(properties)
            }
        }
    }
}
