@file:JvmName("DriverDSLUtils")
package net.corda.testing.driver.impl

import aQute.bnd.header.OSGiHeader
import java.io.IOException
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.time.Duration
import java.util.IdentityHashMap
import java.util.ServiceLoader
import java.util.Collections.unmodifiableMap
import java.util.Collections.unmodifiableSet
import java.util.concurrent.TimeoutException
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import net.corda.data.KeyValuePair
import net.corda.libs.packaging.PackagingConstants.CPB_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPB_NAME_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_FORMAT_ATTRIBUTE
import net.corda.testing.driver.Framework
import net.corda.testing.driver.MembershipGroupDSL
import net.corda.testing.driver.Service
import net.corda.testing.driver.function.ThrowingConsumer
import net.corda.testing.driver.function.ThrowingSupplier
import net.corda.testing.driver.node.EmbeddedNodeService
import net.corda.testing.driver.node.Member
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toCorda
import org.osgi.framework.Bundle
import org.osgi.framework.Bundle.RESOLVED
import org.osgi.framework.Bundle.STARTING
import org.osgi.framework.BundleException
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.EXPORT_PACKAGE
import org.osgi.framework.Constants.FRAMEWORK_STORAGE
import org.osgi.framework.Constants.FRAMEWORK_STORAGE_CLEAN
import org.osgi.framework.Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT
import org.osgi.framework.Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.launch.FrameworkFactory
import org.osgi.framework.wiring.BundleRevision
import org.osgi.framework.wiring.BundleRevision.TYPE_FRAGMENT
import org.osgi.framework.wiring.FrameworkWiring
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler

internal class DriverDSLImpl(
    private val members: Map<MemberX500Name, KeyPair>,
    private val notaries: Map<MemberX500Name, KeyPair>,
    private val groupParameters: Set<KeyValuePair>
) : DriverInternalDSL, AutoCloseable {
    private companion object {
        private const val CORDA_API_ATTRIBUTE = "Corda-Api"
        private const val JAR_PROTOCOL = "jar"
        private const val PAUSE_MILLIS = 100L
        private const val QUASAR_CACHE_LOCATIONS = "FLOW/*"

        private val TIMEOUT = Duration.ofSeconds(60)

        private val quasarExcludePackages = setOf(
            "co.paralleluniverse**",
            "com.esotericsoftware.**",
            "com.fasterxml.jackson.**",
            "com.github.benmanes.caffeine**",
            "com.networknt.schema**",
            "com.typesafe.**",
            "com.zaxxer.hikari**",
            "de.javakaffee.kryoserializers**",
            "io.micrometer.**",
            "javax.**",
            "kotlin**",
            "liquibase**",
            "net.bytebuddy**",
            "org.apache.**",
            "org.hibernate**",
            "org.hsqldb**",
            "org.jboss.logging",
            "org.objectweb.asm",
            "org.objenesis**",
            "org.osgi.**"
        ).joinToString()

        private val quasarExcludeLocations = setOf(
            "PERSISTENCE/*",
            "VERIFICATION/*"
        ).joinToString()

        // These bundles are not loaded into the embedded framework.
        // We export their packages from the application classloader
        // via the system bundle instead, which means that they are
        // shared by all embedded frameworks.
        private val systemBundleNames = setOf(
            "avro",
            "bcpkix",
            "bcprov",
            "bcutil",
            "javax.persistence-api",
            "jcl.over.slf4j",
            "net.corda.driver",
            "org.osgi.service.component",
            "org.osgi.service.jdbc",
            "org.osgi.service.log",
            "org.osgi.util.function",
            "org.osgi.util.promise",
            "slf4j.api"
        )
        private val forbiddenSymbolicNames = setOf(
            "de.javakaffee.kryo-serializers",
            "net.corda.configuration-read-service-impl",
            "net.corda.lifecycle-impl",
            "org.apache.commons.logging",
            "org.apache.felix.framework",
            "org.osgi.namespace.extender",
            "org.osgi.service.component.annotations",
            "org.osgi.test.common",
            "osgi.annotation",
            "osgi.core"
        )
        private val notaryPluginNames = setOf(
            "notary-plugin-non-validating-server"
        )
        private val logger = LoggerFactory.getLogger(DriverDSLImpl::class.java)
        private val cordaBundles: Map<URI, Manifest>
        private val systemPackagesExtra: String
        private val notaryCPBs: Set<URI>
        private val testCPBs: Set<URI>

        init {
            SLF4JBridgeHandler.removeHandlersForRootLogger()
            SLF4JBridgeHandler.install()

            // HSQLDB refuses to use Java functions unless this system property is set correctly.
            System.setProperty("hsqldb.method_class_names", "net.corda.db.hsqldb.json.HsqldbJsonExtension.*")

            val systemPackages = linkedSetOf<String>()
            val bundles = mutableMapOf<URI, Manifest>()
            val notaryPlugins = linkedSetOf<URI>()
            val cpbs = linkedSetOf<URI>()

            this::class.java.classLoader.getResources(MANIFEST_NAME).asSequence()
                .filter { url ->
                    url.protocol == JAR_PROTOCOL
                }.forEach { url ->
                    try {
                        val mf = url.openStream().use(::Manifest)
                        val mainAttributes = mf.mainAttributes
                        if (mainAttributes.getValue(CPK_FORMAT_ATTRIBUTE) != null) {
                            return@forEach
                        } else if (mainAttributes.getValue(CPB_FORMAT_ATTRIBUTE) != null) {
                            val cpbName = mainAttributes.getValue(CPB_NAME_ATTRIBUTE) ?: ""
                            val cpb = url.fileURI
                            if (cpbName in notaryPluginNames) {
                                if (notaryPlugins.add(cpb)) {
                                    logger.info("Found Notary plugin: {}", cpb)
                                }
                            } else if (cpbs.add(cpb)) {
                                logger.info("Found CPB: {}", cpb)
                            }
                            return@forEach
                        }

                        val cordaApi = mainAttributes.getValue(CORDA_API_ATTRIBUTE)
                        val bsn = mainAttributes.getValue(BUNDLE_SYMBOLICNAME)
                        if (((cordaApi != null) && acceptCordaApi(bsn)) || bsn in systemBundleNames) {
                            logger.debug("System Bundle: {}", bsn)
                            systemPackages += getPackageEntries(mainAttributes.getValue(EXPORT_PACKAGE))
                        } else if (acceptBundle(bsn)) {
                            bundles[url.fileURI] = mf
                        }
                    } catch (e: IOException) {
                        logger.warn("Failed to read resource $url", e)
                    }
                }
            systemPackagesExtra = systemPackages.joinToString(separator = ",")
            cordaBundles = unmodifiableMap(bundles)
            notaryCPBs = unmodifiableSet(notaryPlugins)
            testCPBs = unmodifiableSet(cpbs)
        }

        private fun getPackageEntries(header: String?): Set<String> {
            return OSGiHeader.parseHeader(header)?.entries?.mapTo(linkedSetOf()) { (header, attrs) ->
                "$header;$attrs"
            } ?: emptySet()
        }

        private fun acceptBundle(symbolicName: String?): Boolean {
            return symbolicName != null
                && symbolicName !in forbiddenSymbolicNames
                && !symbolicName.startsWith("junit-")
                && !symbolicName.startsWith("assertj")
                && !symbolicName.startsWith("org.opentest4j")
                && !symbolicName.startsWith("org.mockito.")
                && !symbolicName.startsWith("slf4j.")
                && !symbolicName.startsWith("biz.aQute.bnd")
        }

        private fun acceptCordaApi(symbolicName: String?): Boolean {
            return symbolicName != null && symbolicName.startsWith("net.corda.")
                // Some Corda APIs have @Suspendable methods, and so Quasar must instrument those bundles.
                // We therefore only share the bare minimum between the Driver and its Embedded OSGi framework.
                && symbolicName.substring(10).let { it == "base" || it.startsWith("crypto") || it == "avro-schema" }
        }

        private fun createFrameworkFactory(): FrameworkFactory {
            return ServiceLoader.load(FrameworkFactory::class.java, this::class.java.classLoader).single()
        }
    }

    @Suppress("NestedBlockDepth")
    private inner class DriverFrameworkImpl @Throws(BundleException::class) constructor(
        properties: Map<String, String>
    ): Framework, AutoCloseable {
        private val framework = createFrameworkFactory().newFramework(properties)
        private val cleanups = IdentityHashMap<Any, Service<*>>()

        init {
            framework.start()
            try {
                val bundles = cordaBundles.keys.map { uri ->
                    framework.bundleContext.installBundle(uri.toString(), uri.toURL().openStream())
                }

                if (!framework.adapt(FrameworkWiring::class.java).resolveBundles(null)) {
                    // Find the bundles which could not resolve and try to start them.
                    // This should throw a useful BundleException.
                    val failedBundles = bundles.filter { !it.isFragment && it.state < RESOLVED }
                    val ex = BundleException("Bundles [${failedBundles.joinToString()}] did not resolve.")
                    for (failed in failedBundles) {
                        try {
                            // We expect this to throw a BundleException.
                            failed.start()

                            // We don't expect to reach here, but just in case...
                            failed.stop()
                        } catch (e: BundleException) {
                            ex.addSuppressed(e)
                        }
                    }
                    throw ex
                }

                // Everything is now resolved, so start it up!
                bundles.filterNot(Bundle::isFragment).forEach { bundle ->
                    logger.debug("Starting: {} from {}", bundle, bundle.location)
                    bundle.start()
                }
            } catch (e: Exception) {
                framework.stop()
                throw e
            }
        }

        @Throws(BundleException::class)
        @Synchronized
        override fun close() {
            for (cleanup in ArrayList(cleanups.values)) {
                try {
                    cleanup.close()
                } catch (e: Exception) {
                    logger.warn("Error releasing $cleanup during shutdown: ${e.message}", e)
                }
            }

            if (framework.state >= STARTING) {
                framework.stop()
                framework.waitForStop(0)
            }
        }

        @Suppress("ComplexMethod")
        @Throws(InterruptedException::class, TimeoutException::class)
        override fun <T> getService(serviceType: Class<T>, filter: String?, timeout: Duration): Service<T> {
            val ctx = framework.bundleContext

            // Show every service registered inside this framework.
            // This can help diagnose any OSGi resolving errors.
            if (logger.isTraceEnabled) {
                ctx.getAllServiceReferences(null, null)?.forEach { ref ->
                    logger.trace("Has service {},{}", ref, ref.properties)
                }
            }

            var remainingMillis = timeout.toMillis().coerceAtLeast(0)
            while (true) {
                try {
                    ctx.getServiceReferences(serviceType, filter)
                } catch(e: InvalidSyntaxException) {
                    throw IllegalArgumentException(e.message, e)
                }.maxOrNull()?.let { ref ->
                    val svc = ServiceImpl((ctx.getService(ref) ?: return@let null), cleanups.keys) {
                        ctx.ungetService(ref)
                    }
                    return cleanups.putIfAbsent(svc.get(), svc)?.let {
                        svc.close()

                        @Suppress("unchecked_cast")
                        it as Service<T>
                    } ?: svc
                }

                if (remainingMillis <= 0) {
                    break
                }
                val waitMillis = remainingMillis.coerceAtMost(PAUSE_MILLIS)
                Thread.sleep(waitMillis)
                remainingMillis -= waitMillis
            }

            val serviceDescription = serviceType.name + (filter?.let { f -> ", filter=$f" } ?: "")
            throw TimeoutException("Service $serviceDescription did not arrive in ${timeout.toMillis()} milliseconds")
        }

        fun loadCordapps(memberNames: Set<MemberX500Name>): List<VirtualNodeInfo> {
            val vNodes = mutableListOf<VirtualNodeInfo>()

            getService(EmbeddedNodeService::class.java, null, Duration.ZERO).andAlso { ens ->
                for (testCPB in testCPBs) {
                    logger.info("Uploading CPB: {}", Path.of(testCPB).fileName)
                    vNodes += ens.loadVirtualNodes(memberNames, testCPB).map(net.corda.data.virtualnode.VirtualNodeInfo::toCorda)
                }

                if (notaries.isNotEmpty()) {
                    if (notaryCPBs.size == 1) {
                        ens.loadSystemCpi(notaries.keys, notaryCPBs.first())
                    } else if (notaryCPBs.isEmpty()) {
                        logger.warn("No system CPB not found on classpath")
                    } else {
                        logger.warn("Multiple system CPBs found on classpath: {}", notaryCPBs.joinToString())
                    }
                }
            }

            return java.util.List.copyOf(vNodes)
        }
    }

    private val virtualNodeInfo = mutableMapOf<VirtualNodeKey, VirtualNodeInfo>()
    private val driverFramework = createDriverFramework()

    init {
        driverFramework.getService(EmbeddedNodeService::class.java, null, Duration.ZERO).andAlso { ens ->
            val network = members + notaries
            ens.setGroupParameters(groupParameters)
            ens.setMembershipGroup(network)
            ens.configure(TIMEOUT)
        }
    }

    private fun createDriverFramework(): DriverFrameworkImpl {
        try {
            val frameworkDirectory = Files.createTempDirectory("corda-driver-")
            val quasarCacheDirectory = Files.createDirectories(frameworkDirectory.parent.resolve("quasar-cache"))
            return DriverFrameworkImpl(linkedMapOf(
                "co.paralleluniverse.quasar.suspendableAnnotation" to Suspendable::class.java.name,
                "co.paralleluniverse.quasar.cacheDirectory" to quasarCacheDirectory.toString(),
                "co.paralleluniverse.quasar.cacheLocations" to QUASAR_CACHE_LOCATIONS,
                "co.paralleluniverse.quasar.excludeLocations" to quasarExcludeLocations,
                "co.paralleluniverse.quasar.excludePackages" to quasarExcludePackages,
                "co.paralleluniverse.quasar.verbose" to false.toString(),
                FRAMEWORK_STORAGE to frameworkDirectory.toString(),
                FRAMEWORK_STORAGE_CLEAN to FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT,
                FRAMEWORK_SYSTEMPACKAGES_EXTRA to systemPackagesExtra
            ))
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw CordaRuntimeException(e::class.java.name, e.message, e)
        }
    }

    override fun getFramework(): Framework {
        return driverFramework
    }

    override fun startNodes(memberNames: Set<MemberX500Name>): List<VirtualNodeInfo> {
        val networkMembers = memberNames.filterTo(linkedSetOf(), members::containsKey)
        with(memberNames - networkMembers) {
            require(isEmpty()) {
                "Non-member X500 names: $this"
            }
        }
        require(networkMembers.isNotEmpty()) {
            "Each node needs at least one member."
        }
        try {
            return driverFramework.loadCordapps(networkMembers).onEach { vNode ->
                virtualNodeInfo[VirtualNodeKey(vNode.cpiIdentifier.name, vNode.holdingIdentity.x500Name)] = vNode
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw CordaRuntimeException(e::class.java.name, e.message, e)
        }
    }

    override fun runFlow(
        virtualNodeInfo: VirtualNodeInfo,
        flowClass: Class<out ClientStartableFlow>,
        flowArgMapper: ThrowingSupplier<String>
    ): String? {
        return FlowRunner(this).runFlow(virtualNodeInfo, flowClass, flowArgMapper, TIMEOUT)
    }

    override fun nodeFor(groupName: String, member: MemberX500Name): VirtualNodeInfo {
        return virtualNodeInfo[VirtualNodeKey(groupName, member)]
            ?: throw AssertionError("VirtualNodeInfo not found for member=${member.commonName}, group=$groupName")
    }

    override fun nodesFor(groupName: String): Map<MemberX500Name, VirtualNodeInfo> {
        return java.util.Map.copyOf(
            virtualNodeInfo.filterValues { vNode ->
                vNode.cpiIdentifier.name == groupName
            }.ifEmpty {
                throw AssertionError("No nodes found for group $groupName")
            }.mapKeys { entry ->
                entry.key.member
            }
        )
    }

    override fun node(virtualNodeInfo: VirtualNodeInfo, action: ThrowingConsumer<Member>) {
        MembershipGroupManager(this).forVirtualNode(virtualNodeInfo.holdingIdentity, TIMEOUT, action)
    }

    override fun groupFor(virtualNodeInfo: VirtualNodeInfo, action: ThrowingConsumer<MembershipGroupDSL>) {
        MembershipGroupManager(this).forMembershipGroup(virtualNodeInfo.holdingIdentity, TIMEOUT, action)
    }

    override fun group(groupName: String, action: ThrowingConsumer<MembershipGroupDSL>) {
        val anyMemberNode = virtualNodeInfo.values.firstOrNull { vNode ->
            vNode.cpiIdentifier.name == groupName
        } ?: throw AssertionError("Group '$groupName' not found")
        return groupFor(anyMemberNode, action)
    }

    @Throws(InterruptedException::class)
    override fun close() {
        driverFramework.close()
    }
}

private data class VirtualNodeKey(val groupName: String, val member: MemberX500Name)

private class ServiceImpl<T : Any>(
    private val service: T,
    private val owner: MutableSet<*>,
    private val closeable: AutoCloseable
) : Service<T> {

    @Throws(Exception::class)
    override fun close() {
        owner.remove(service)
        closeable.close()
    }

    override fun get(): T = service

    override fun toString(): String = service.toString()
}

private val Bundle.isFragment: Boolean
    get() = (adapt(BundleRevision::class.java).types and TYPE_FRAGMENT) != 0

private val URL.fileURI: URI
    get() {
        val idx = path.indexOf("!/")
        return URI(if (idx >= 0) {
            path.substring(0, idx)
        } else {
            path
        })
    }
