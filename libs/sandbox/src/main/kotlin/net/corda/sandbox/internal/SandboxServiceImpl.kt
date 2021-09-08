package net.corda.sandbox.internal

import net.corda.install.InstallService
import net.corda.packaging.Cpk
import net.corda.sandbox.ClassInfo
import net.corda.sandbox.CpkClassInfo
import net.corda.sandbox.CpkSandbox
import net.corda.sandbox.NonCpkClassInfo
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import net.corda.sandbox.internal.sandbox.CpkSandboxInternal
import net.corda.sandbox.internal.sandbox.SandboxImpl
import net.corda.sandbox.internal.sandbox.SandboxInternal
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.asSequence

/**
 * An implementation of the [SandboxCreationService] and [SandboxContextService] OSGi service interfaces.
 */
@Component(service = [SandboxCreationService::class, SandboxContextService::class, SandboxServiceInternal::class])
@Suppress("TooManyFunctions")
internal class SandboxServiceImpl @Activate constructor(
    @Reference
    private val installService: InstallService,
    @Reference
    private val bundleUtils: BundleUtils
) : SandboxServiceInternal, SingletonSerializeAsToken {

    // These sandboxes are not persisted in any way; they are recreated on node startup.
    private val sandboxes = ConcurrentHashMap<UUID, SandboxInternal>()

    // Maps each sandbox ID to the sandbox group that the sandbox is part of.
    private val sandboxGroups = ConcurrentHashMap<UUID, SandboxGroup>()

    private val logger = loggerFor<SandboxServiceImpl>()

    // Made lazy because we only want to create the platform sandbox once all the platform bundles are installed.
    private val platformSandbox by lazy(::createPlatformSandbox)

    override fun createSandboxes(cpkFileHashes: Iterable<SecureHash>) =
        createSandboxes(cpkFileHashes, startBundles = true)

    override fun createSandboxesWithoutStarting(cpkFileHashes: Iterable<SecureHash>) =
        createSandboxes(cpkFileHashes, startBundles = false)

    override fun getClassInfo(klass: Class<*>): ClassInfo {
        val sandbox = sandboxes.values.find { sandbox -> sandbox.containsClass(klass) }
            ?: throw SandboxException("Class $klass is not contained in any sandbox.")
        return getClassInfo(klass, sandbox)
    }

    override fun getClassInfo(className: String): ClassInfo {
        for (sandbox in sandboxes.values.filterIsInstance<CpkSandboxImpl>()) {
            try {
                val klass = sandbox.loadClassFromCordappBundle(className) ?: continue
                val bundle = bundleUtils.getBundle(klass)
                    ?: throw SandboxException("Class $klass is not loaded from any bundle.")
                val matchingSandbox = sandboxes.values.find { it.containsBundle(bundle) }
                matchingSandbox?.let { return getClassInfo(klass, matchingSandbox) }
                    ?: logger.trace("Class $className not found in sandbox $sandbox. ")
            } catch (ex: SandboxException) {
                logger.trace("Class $className not found in sandbox $sandbox. ")
            }
        }
        throw SandboxException("Class $className is not contained in any sandbox.")
    }

    override fun getSandbox(bundle: Bundle) = sandboxes.values.find { sandbox -> sandbox.containsBundle(bundle) }

    override fun isPlatformSandbox(sandbox: Sandbox) = sandbox === platformSandbox

    override fun hasVisibility(lookingBundle: Bundle, lookedAtBundle: Bundle): Boolean {
        val lookingSandbox = getSandbox(lookingBundle)
        val lookedAtSandbox = getSandbox(lookedAtBundle)

        return when {
            // Do both bundles belong to the same sandbox, or is neither bundle in a sandbox?
            lookedAtSandbox === lookingSandbox -> true
            // Does only one of the bundles belong to a sandbox?
            lookedAtSandbox == null || lookingSandbox == null -> false
            // Does the looking sandbox not have visibility of the looked at sandbox?
            !lookingSandbox.hasVisibility(lookedAtSandbox) -> false
            // Is the looking bundle a public bundle in the platform sandbox?
            lookingSandbox === platformSandbox && lookingBundle in lookingSandbox.publicBundles -> true
            // Is the looked-at bundle a public bundle in the looked-at sandbox?
            lookedAtSandbox.publicBundles.any { bundle -> bundle == lookedAtBundle } -> true

            else -> false
        }
    }

    override fun getCallingSandbox(): Sandbox? {
        val stackWalkerInstance = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

        val sandboxBundleLocation = stackWalkerInstance.walk { stackFrameStream ->
            stackFrameStream
                .asSequence()
                .mapNotNull { stackFrame -> bundleUtils.getBundle(stackFrame.declaringClass)?.location }
                .find { bundleLocation -> bundleLocation.startsWith("sandbox/") }
        } ?: return null

        val sandboxId = SandboxLocation.fromString(sandboxBundleLocation).id

        return sandboxes[sandboxId] ?: throw SandboxException(
            "A sandbox was found on the stack, but it did not " +
                    "match any sandbox known to this SandboxService."
        )
    }

    override fun getCallingSandboxGroup(): SandboxGroup? {
        val sandboxId = getCallingSandbox()?.id ?: return null

        return sandboxGroups[sandboxId] ?: throw SandboxException(
            "A sandbox was found, but it was not part of any sandbox group."
        )
    }

    override fun getCallingCpk(): Cpk.Identifier? {
        val callingSandbox = getCallingSandbox()
        return if (callingSandbox is CpkSandbox) {
            callingSandbox.cpk.id
        } else {
            null
        }
    }

    /**
     * Retrieves the CPKs from the [installService] based on their [cpkFileHashes], and verifies the CPKs.
     *
     * Creates a [SandboxGroup], containing a [Sandbox] for each of the CPKs. On the first run, also initialises the
     * platform sandbox. [startBundles] controls whether the CPK bundles are also started.
     *
     * Grants each sandbox visibility of the platform sandbox and of the other sandboxes in the group.
     */
    private fun createSandboxes(cpkFileHashes: Iterable<SecureHash>, startBundles: Boolean): SandboxGroup {
        val cpks = cpkFileHashes.mapTo(LinkedHashSet()) { cpkFileHash ->
            installService.getCpk(cpkFileHash)
                ?: throw SandboxException("No CPK is installed for CPK file hash $cpkFileHash.")
        }
        installService.verifyCpkGroup(cpks)

        // We track the bundles that are being created, so that we can start them all at once at the end.
        val bundles = mutableSetOf<Bundle>()

        // We track which sandbox was created for which CPK identifier, so that we can pass this information during
        // the construction of the `SandboxGroup`.
        val cpkSandboxMapping = mutableMapOf<Cpk.Identifier, CpkSandboxImpl>()

        cpks.forEach { cpk ->
            val sandboxId = UUID.randomUUID()

            val cordappBundle = installBundle(cpk.mainJar.toUri(), sandboxId)
            val libraryBundles = cpk.libraries.mapTo(LinkedHashSet()) { libraryJar ->
                installBundle(libraryJar.toUri(), sandboxId)
            }
            val sandbox = CpkSandboxImpl(bundleUtils, sandboxId, cpk, cordappBundle, libraryBundles)
            sandboxes[sandboxId] = sandbox

            // Every sandbox requires visibility of the platform sandbox.
            sandbox.grantVisibility(platformSandbox)

            // The "platform" sandbox contains the OSGi framework itself,
            // and so it must be allowed to "see" every sandbox too.
            platformSandbox.grantVisibility(sandbox)

            bundles.addAll(libraryBundles)
            bundles.add(cordappBundle)

            cpkSandboxMapping[cpk.id] = sandbox
        }

        val sandboxes = cpkSandboxMapping.values

        // Each sandbox requires visibility of the sandboxes of the other CPKs.
        sandboxes.forEach { sandbox ->
            sandboxes.forEach { otherSandbox ->
                sandbox.grantVisibility(otherSandbox)
            }
        }

        // We only start the bundles once all the CPKs' bundles have been installed and sandboxed, since there are
        // likely dependencies between the CPKs' bundles.
        if (startBundles) {
            startBundles(bundles)
        }

        val sandboxGroup = SandboxGroupImpl(
            bundleUtils,
            cpkSandboxMapping,
            platformSandbox
        )

        // We update the mapping from sandbox IDs to the sandbox group that the sandbox is part of.
        sandboxes.forEach { sandbox ->
            sandboxGroups[sandbox.id] = sandboxGroup
        }

        return sandboxGroup
    }

    /**
     * Creates the platform sandbox. This sandbox's public bundles are those bundles that CPKs require visibility of
     * (including the public Corda API). The sandbox's private bundles are all other platform bundles.
     */
    private fun createPlatformSandbox(): SandboxImpl {
        val (publicBundles, privateBundles) = bundleUtils.allBundles.partition { bundle ->
            bundle.symbolicName in PUBLIC_PLATFORM_BUNDLE_NAMES
        }

        val platformSandbox = SandboxImpl(bundleUtils, UUID.randomUUID(), publicBundles.toSet(), privateBundles.toSet())

        sandboxes[platformSandbox.id] = platformSandbox

        return platformSandbox
    }

    /**
     * Installs the contents of the [jarLocation] as a bundle and returns the bundle.
     *
     * The bundle's location is a unique value generated by the combination of the JAR's location and the sandbox ID.
     *
     * A [SandboxException] is thrown if a bundle fails to install.
     */
    private fun installBundle(jarLocation: URI, sandboxId: UUID): Bundle {
        val sandboxedBundleLocation = SandboxLocation(sandboxId, jarLocation)
        return try {
            bundleUtils.installAsBundle(sandboxedBundleLocation.toString(), jarLocation)
        } catch (e: BundleException) {
            throw SandboxException("Could not install $jarLocation as a bundle in sandbox $sandboxId.", e)
        }
    }

    /**
     * Starts each of the [bundles].
     *
     * Throws [SandboxException] if a bundle cannot be started.
     * */
    private fun startBundles(bundles: Collection<Bundle>) {
        bundles.forEach { bundle ->
            try {
                bundleUtils.startBundle(bundle)
            } catch (e: BundleException) {
                throw SandboxException("Bundle $bundle could not be started.", e)
            }
        }
    }

    /** Contains the logic that is shared between the two public `getClassInfo` methods. */
    private fun getClassInfo(klass: Class<*>, sandbox: SandboxInternal): ClassInfo {
        val bundle = bundleUtils.getBundle(klass)
            ?: throw SandboxException("Class $klass is not loaded from any bundle.")

        val cpk = when (sandbox) {
            is CpkSandboxInternal -> sandbox.cpk
            else -> return NonCpkClassInfo(bundle.symbolicName, bundle.version)
        }

        // This lookup is required because a CPK's dependencies are only given as <name, version, public key hashes>
        // trios in CPK files.
        val cpkDependencyHashes = cpk.dependencies.mapTo(LinkedHashSet()) { cpkIdentifier ->
            (installService.getCpk(cpkIdentifier) ?: throw SandboxException(
                "CPK $cpkIdentifier is listed as a dependency of ${cpk.id}, but is not installed."
            )).cpkHash
        }

        return CpkClassInfo(
            bundle.symbolicName,
            bundle.version,
            sandbox.cordappBundle.symbolicName,
            sandbox.cordappBundle.version,
            cpk.cpkHash,
            cpk.id.signers,
            cpkDependencyHashes
        )
    }
}
