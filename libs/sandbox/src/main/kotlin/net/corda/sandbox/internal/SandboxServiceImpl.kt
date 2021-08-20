package net.corda.sandbox.internal

import net.corda.install.InstallService
import net.corda.packaging.Cpb
import net.corda.packaging.Cpk
import net.corda.sandbox.ClassInfo
import net.corda.sandbox.CpkClassInfo
import net.corda.sandbox.PlatformClassInfo
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.SandboxService
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.Bundle
import org.osgi.framework.BundleException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.net.URI
import java.util.Collections
import java.util.NavigableMap
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * An implementation of the [SandboxService] OSGi service interface.
 */
@Component(service = [SandboxService::class, SandboxServiceInternal::class])
@Suppress("TooManyFunctions")
internal class SandboxServiceImpl @Activate constructor(
        @Reference
        private val installService: InstallService,
        @Reference
        private val bundleUtils: BundleUtils) : SandboxServiceInternal, SingletonSerializeAsToken {

    // These sandboxes are not persisted in any way; they are recreated on node startup.
    private val sandboxes = ConcurrentHashMap<UUID, SandboxInternal>()
    private val logger = loggerFor<SandboxServiceImpl>()

    // This field is lazy because we don't want to create the platform sandboxes until all the platform bundles are installed.
    private val platformSandboxes by lazy(::createPlatformSandboxes)

    override fun createSandboxes(cpbIdentifier: Cpb.Identifier): SandboxGroup {
        val cpb = installService.getCpb(cpbIdentifier) ?: throw SandboxException("CPB$cpbIdentifier is not installed.")
        return createSandboxes(cpb.cpks)
    }

    override fun createSandboxesWithoutStarting(cpbIdentifier: Cpb.Identifier): SandboxGroup {
        val cpb = installService.getCpb(cpbIdentifier) ?: throw SandboxException("CPB $cpbIdentifier is not installed.")
        return createSandboxes(cpb.cpks, false)
    }

    override fun createSandboxesFromIdentifiers(cpkIdentifiers: Iterable<Cpk.Identifier>): SandboxGroup {
        val cpks = cpkIdentifiers.mapTo(LinkedHashSet()) { cpkIdentifier ->
            installService.getCpk(cpkIdentifier) ?: throw SandboxException("CPK $cpkIdentifier is not installed.")
        }
        installService.verifyCpkGroup(cpks)
        return createSandboxes(cpks)
    }

    override fun createSandboxesFromHashes(cpkHashes: Iterable<SecureHash>): SandboxGroup {
        val cpks = cpkHashes.mapTo(LinkedHashSet()) { cpkHash ->
            installService.getCpk(cpkHash) ?: throw SandboxException("No CPK is installed for hash $cpkHash.")
        }
        installService.verifyCpkGroup(cpks)
        return createSandboxes(cpks)
    }

    override fun createSandboxesFromHashesWithoutStarting(cpkHashes: Iterable<SecureHash>): SandboxGroup {
        val cpks = cpkHashes.mapTo(LinkedHashSet()) { cpkHash ->
            installService.getCpk(cpkHash) ?: throw SandboxException("No CPK is installed for hash $cpkHash.")
        }
        installService.verifyCpkGroup(cpks)
        return createSandboxes(cpks, false)
    }

    override fun getSandbox(id: UUID) = sandboxes[id]

    override fun getClassInfo(klass: Class<*>): ClassInfo {
        val sandbox = sandboxes.values.find { sandbox -> sandbox.containsClass(klass) }
            ?: throw SandboxException("Class $klass is not contained in any sandbox.")
        return getClassInfo(klass, sandbox)
    }

    override fun getClassInfo(className: String): ClassInfo {
        sandboxes.values.forEach { it ->
            try {
                val classForName = it.loadClass(className)
                val bundle = it.getBundle(classForName)
                val sandbox = sandboxes.values.find { it.containsBundle(bundle) }
                sandbox?.let {return getClassInfo(classForName, sandbox)} ?: logger.trace("Class $className not found in sandbox $it. ")
            } catch (ex: SandboxException) {
                logger.trace("Class $className not found in sandbox $it. ")
            }
        }
        throw SandboxException("Class $className is not contained in any sandbox.")
    }

    override fun getSandbox(bundle: Bundle) = sandboxes.values.find { sandbox -> sandbox.containsBundle(bundle) }

    override fun isPlatformSandbox(sandbox: Sandbox) = sandbox in platformSandboxes

    override fun hasVisibility(lookingBundle: Bundle, lookedAtBundle: Bundle): Boolean {
        val lookingSandbox = getSandbox(lookingBundle)
        val lookedAtSandbox = getSandbox(lookedAtBundle)

        return when {
            // Do both bundles belong to the same sandbox?
            // Or neither bundle belongs to any sandbox?
            lookedAtSandbox === lookingSandbox -> true

            // Does only one of the bundles belong to a sandbox?
            lookedAtSandbox == null || lookingSandbox == null -> false

            else ->
                // The "core" sandbox can always both see and be seen.
                // Other sandboxes can only see each other's "main" jars.
                lookingSandbox.hasVisibility(lookedAtSandbox)
                    && (isCoreSandbox(lookingSandbox)
                        || isCoreSandbox(lookedAtSandbox)
                        || lookedAtSandbox.isCordappBundle(lookedAtBundle))
        }
    }

    override fun deleteSandbox(id: UUID) {
        val sandbox = sandboxes[id] ?: throw SandboxException("No sandbox found with ID $id.")
        sandbox.uninstallBundles()
        sandboxes.remove(id)

        // The "core" sandbox can now safely forget about this one.
        if (!platformSandboxes.core.revokeVisibility(sandbox)) {
            logger.warn("Sandbox $id was invisible to the core sandbox.")
        }
    }

    /**
     * Creates a sandbox containing a fresh copy of the CPK's bundles for each of the [cpks].
     *
     * Grants each sandbox visibility of the core sandbox and of the other [cpks]' sandboxes.
     *
     * On the first run, also initialises the core and non-core sandboxes.
     */
    private fun createSandboxes(cpks: Iterable<Cpk.Expanded>, start: Boolean = true): SandboxGroup {
        // We track the bundles that are being created so we can start them all at once at the end.
        val bundles = mutableSetOf<Bundle>()

        // This line forces the lazy creation of the platform sandboxes. This *must* happen before the creation of any
        // CPK sandboxes.
        val corePlatformSandbox = platformSandboxes.core

        val sandboxGroupMapping : NavigableMap<Cpk.Identifier, SandboxInternal> = TreeMap()

        cpks.forEach { cpk ->
            val sandboxId = UUID.randomUUID()

            val cordappBundle = installBundle(cpk.mainJar.toUri(), sandboxId)
            val libraryBundles = cpk.libraries.mapTo(LinkedHashSet()) { libraryJar ->
                installBundle(libraryJar.toUri(), sandboxId)
            }
            val sandbox = SandboxImpl(bundleUtils, sandboxId, cpk, cordappBundle, libraryBundles)
            sandboxes[sandboxId] = sandbox

            // Every sandbox requires visibility of the core sandbox.
            sandbox.grantVisibility(corePlatformSandbox)

            // The "core" sandbox contains the OSGi framework itself,
            // and so it must be allowed to "see" every sandbox too.
            corePlatformSandbox.grantVisibility(sandbox)

            bundles.addAll(libraryBundles)
            bundles.add(cordappBundle)

            sandboxGroupMapping[cpk.id] = sandbox
        }

        // Each sandbox requires visibility of the sandboxes of the other CPKs.
        val sandboxes = sandboxGroupMapping.values
        sandboxes.forEach { sandbox -> sandbox.grantVisibility(sandboxes) }

        // We only start the bundles once all the CPKs' bundles have been installed and sandboxed, since there are
        // likely dependencies between the CPKs' bundles.
        if (start) {
            bundles.forEach { bundle ->
                try {
                    bundleUtils.startBundle(bundle)
                } catch (e: BundleException) {
                    throw SandboxException("Bundle $bundle could not be started.", e)
                }
            }
        }

        return SandboxGroupImpl(Collections.unmodifiableNavigableMap(sandboxGroupMapping))
    }

    /**
     * Creates and stores the two default platform sandboxes. The former contains the bundles that CPKs require
     * visibility of (including the public Corda API). The latter contains all other platform bundles.
     */
    private fun createPlatformSandboxes(): PlatformSandboxes {
        val (coreBundles, nonCoreBundles) = bundleUtils.allBundles.partition { bundle ->
            bundle.symbolicName in CORE_BUNDLE_NAMES
        }

        val coreSandbox = SandboxImpl(bundleUtils, UUID.randomUUID(), null, null, coreBundles.toSet())
        val nonCoreSandbox = SandboxImpl(bundleUtils, UUID.randomUUID(), null, null, nonCoreBundles.toSet())
        nonCoreSandbox.grantVisibility(coreSandbox)
        coreSandbox.grantVisibility(nonCoreSandbox)

        listOf(coreSandbox, nonCoreSandbox).forEach { sandbox ->
            sandboxes[sandbox.id] = sandbox
        }

        return PlatformSandboxes(coreSandbox, nonCoreSandbox)
    }

    /**
     * Installs the contents of the [jarLocation] as a bundle and returns the bundle.
     *
     * The bundle's location is a unique value generated by the combination of the JAR's location and the sandbox ID.
     *
     * A [SandboxException] is thrown if a bundle fails to install.
     */
    private fun installBundle(jarLocation: URI, sandboxId: UUID): Bundle {
        val sandboxedBundleLocation = SandboxInternal.getLocation(sandboxId, jarLocation)
        return try {
            bundleUtils.installAsBundle(sandboxedBundleLocation, jarLocation)
        } catch (e: BundleException) {
            throw SandboxException("Could not install $jarLocation as a bundle in sandbox $sandboxId.", e)
        }
    }

    /** Checks whether a sandbox is the platform's core sandbox. */
    private fun isCoreSandbox(sandbox: Sandbox) = sandbox == platformSandboxes.core

    /** Contains the logic that is shared between the two public `getClassInfo` methods. */
    private fun getClassInfo(klass: Class<*>, sandbox: SandboxInternal): ClassInfo {
        val cpk = sandbox.cpk
        val bundle = sandbox.getBundle(klass)

        if (cpk == null) {
            return PlatformClassInfo(bundle.symbolicName, bundle.version)
        }

        // This lookup is required because a CPK's dependencies are only given as <name, version, public key hashes>
        // trios in CPK files.
        val cpkDependencyHashes = cpk.dependencies.mapTo(LinkedHashSet()) { cpkIdentifier ->
            (installService.getCpk(cpkIdentifier) ?: throw SandboxException(
                    "CPK $cpkIdentifier is listed as a dependency of ${cpk.id}, but is not installed.")).cpkHash
        }

        return CpkClassInfo(bundle.symbolicName, bundle.version, cpk.cpkHash, cpk.id.signers, cpkDependencyHashes)
    }
}

/**
 * The two platform sandboxes:
 *  * The [core] sandbox, to which all CPKs are granted visibility, that contains key bundles including the public Corda API
 *  * The [nonCore] sandbox, that contains all other platform bundles
 */
private data class PlatformSandboxes(val core: SandboxInternal, val nonCore: SandboxInternal) {
    operator fun contains(sandbox: Sandbox): Boolean {
        return sandbox === core || sandbox === nonCore
    }
}
