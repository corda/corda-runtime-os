package net.corda.sandbox.internal

import net.corda.install.InstallService
import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.internal.classtag.ClassTagFactoryImpl
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import net.corda.sandbox.internal.sandbox.Sandbox
import net.corda.sandbox.internal.sandbox.SandboxImpl
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.Bundle
import org.osgi.framework.BundleException
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.asSequence

/** An implementation of [SandboxCreationService] and [SandboxContextService]. */
@Component(service = [SandboxCreationService::class, SandboxContextService::class])
@Suppress("TooManyFunctions")
internal class SandboxServiceImpl @Activate constructor(
    @Reference
    private val installService: InstallService,
    @Reference
    private val bundleUtils: BundleUtils
) : SandboxCreationService, SandboxContextService, SingletonSerializeAsToken {
    private val serviceComponentRuntimeBundleId = bundleUtils.getServiceRuntimeComponentBundle()?.bundleId
        ?: throw SandboxException(
            "The sandbox service cannot run without the Service Component Runtime bundle installed."
        )

    // These sandboxes are not persisted in any way; they are recreated on node startup.
    private val sandboxes = ConcurrentHashMap<UUID, Sandbox>()

    // Maps each sandbox ID to the sandbox group that the sandbox is part of.
    private val sandboxGroups = ConcurrentHashMap<UUID, SandboxGroup>()

    // The created public sandboxes.
    private val publicSandboxes = mutableListOf<Sandbox>()

    // Bundles that failed to uninstall when a sandbox group was unloaded.
    private val zombieBundles = mutableListOf<Bundle>()

    private val logger = loggerFor<SandboxServiceImpl>()

    override fun createPublicSandbox(publicBundles: Iterable<Bundle>, privateBundles: Iterable<Bundle>) {
        val publicSandbox = SandboxImpl(UUID.randomUUID(), publicBundles.toSet(), privateBundles.toSet())
        sandboxes[publicSandbox.id] = publicSandbox
        publicSandboxes.add(publicSandbox)
    }

    override fun createSandboxGroup(cpkHashes: Iterable<SecureHash>, securityDomain: String) =
        createSandboxes(cpkHashes, securityDomain, startBundles = true)

    override fun createSandboxGroupWithoutStarting(cpkHashes: Iterable<SecureHash>, securityDomain: String) =
        createSandboxes(cpkHashes, securityDomain, startBundles = false)

    override fun unloadSandboxGroup(sandboxGroup: SandboxGroup) {
        val sandboxGroupInternal = sandboxGroup as SandboxGroupInternal
        sandboxGroupInternal.cpkSandboxes.forEach { sandbox ->
            sandboxes.remove(sandbox.id)
            sandboxGroups.remove(sandbox.id)
            zombieBundles.addAll((sandbox as Sandbox).unload())
        }
    }

    @Suppress("ComplexMethod")
    override fun hasVisibility(lookingBundle: Bundle, lookedAtBundle: Bundle): Boolean {
        val lookingSandbox = sandboxes.values.find { sandbox -> sandbox.containsBundle(lookingBundle) }
        val lookedAtSandbox = sandboxes.values.find { sandbox -> sandbox.containsBundle(lookedAtBundle) }

        return when {
            lookingBundle in zombieBundles || lookedAtBundle in zombieBundles -> false
            // These two framework bundles require full visibility.
            lookingBundle.bundleId in listOf(SYSTEM_BUNDLE_ID, serviceComponentRuntimeBundleId) -> true
            // Do both bundles belong to the same sandbox, or is neither bundle in a sandbox?
            lookedAtSandbox === lookingSandbox -> true
            // Does only one of the bundles belong to a sandbox?
            lookedAtSandbox == null || lookingSandbox == null -> false
            // Does the looking sandbox not have visibility of the looked at sandbox?
            !lookingSandbox.hasVisibility(lookedAtSandbox) -> false
            // Is the looked-at bundle a public bundle in the looked-at sandbox?
            lookedAtBundle in lookedAtSandbox.publicBundles -> true

            else -> false
        }
    }

    override fun getCallingSandboxGroup(): SandboxGroup? {
        val sandbox = getCallingSandbox() ?: return null
        return sandboxGroups[sandbox.id] ?: throw SandboxException(
            "A sandbox was found, but it was not part of any sandbox group."
        )
    }

    override fun isSandboxed(bundle: Bundle) = sandboxes.values.any { sandbox -> sandbox.containsBundle(bundle) }

    override fun areInSameSandbox(bundleOne: Bundle, bundleTwo: Bundle): Boolean {
        val sandboxOne = sandboxes.values.find { sandbox -> sandbox.containsBundle(bundleOne) }
        val sandboxTwo = sandboxes.values.find { sandbox -> sandbox.containsBundle(bundleTwo) }
        return sandboxOne != null && sandboxOne === sandboxTwo
    }

    /**
     * Retrieves the CPKs from the [installService] based on their [cpkFileHashes], and verifies the CPKs.
     *
     * Creates a [SandboxGroup] in the [securityDomain], containing a [Sandbox] for each of the CPKs. On the first run,
     * also initialises a public sandbox. [startBundles] controls whether the CPK bundles are also started.
     *
     * Grants each sandbox visibility of the public sandboxes and of the other sandboxes in the group.
     */
    private fun createSandboxes(
        cpkFileHashes: Iterable<SecureHash>,
        securityDomain: String,
        startBundles: Boolean
    ): SandboxGroup {
        if (securityDomain.contains('/'))
            throw SandboxException("Security domain cannot contain a '/' character.")

        val cpks = cpkFileHashes.mapTo(LinkedHashSet()) { cpkFileHash ->
            installService.getCpk(cpkFileHash)
                ?: throw SandboxException("No CPK is installed for CPK file hash $cpkFileHash.")
        }
        installService.verifyCpkGroup(cpks)

        // We track the bundles that are being created, so that we can start them all at once at the end if needed.
        val bundles = mutableSetOf<Bundle>()

        val newSandboxes = cpks.map { cpk ->
            val sandboxId = UUID.randomUUID()

            val mainBundle = installBundle(
                "${cpk.metadata.id.name}-${cpk.metadata.id.version}/${cpk.metadata.mainBundle}",
                cpk.getResourceAsStream(cpk.metadata.mainBundle),
                sandboxId,
                securityDomain
            )
            val libraryBundles = cpk.metadata.libraries.mapTo(LinkedHashSet()) { libraryJar ->
                installBundle(
                    "${cpk.metadata.id.name}-${cpk.metadata.id.version}/$libraryJar",
                    cpk.getResourceAsStream(libraryJar),
                    sandboxId,
                    securityDomain
                )
            }
            bundles.addAll(libraryBundles)
            bundles.add(mainBundle)

            val sandbox = CpkSandboxImpl(sandboxId, cpk, mainBundle, libraryBundles)
            sandboxes[sandboxId] = sandbox

            sandbox
        }

        publicSandboxes.forEach { publicSandbox ->
            // The public sandboxes have visibility of all sandboxes.
            publicSandbox.grantVisibility(newSandboxes)
        }

        newSandboxes.forEach { newSandbox ->
            // Each sandbox requires visibility of the sandboxes of the other CPKs and of the public sandboxes.
            newSandbox.grantVisibility(newSandboxes - newSandbox + publicSandboxes)
        }

        // We only start the bundles once all the CPKs' bundles have been installed and sandboxed, since there are
        // likely dependencies between the CPKs' bundles.
        if (startBundles) {
            startBundles(bundles)
        }

        val sandboxGroup = SandboxGroupImpl(newSandboxes, publicSandboxes, ClassTagFactoryImpl(), bundleUtils)

        newSandboxes.forEach { sandbox ->
            sandboxGroups[sandbox.id] = sandboxGroup
        }

        return sandboxGroup
    }

    /**
     * Installs the contents of the [bundleSource] as a bundle and returns the bundle.
     *
     * The bundle's location is a unique value generated by combining the security domain, the sandbox ID, and the
     * JAR's location.
     *
     * A [SandboxException] is thrown if a bundle fails to install, or does not have a symbolic name.
     */
    private fun installBundle(
        bundleSource: String,
        inputStream: InputStream,
        sandboxId: UUID,
        securityDomain: String
    ): Bundle {

        val sandboxLocation = SandboxLocation(securityDomain, sandboxId, bundleSource)
        val bundle = try {
            bundleUtils.installAsBundle(sandboxLocation.toString(), inputStream)
        } catch (e: BundleException) {
            if (bundleUtils.allBundles.none { bundle -> bundle.symbolicName == SANDBOX_HOOKS_BUNDLE }) {
                logger.warn(
                    "The \"$SANDBOX_HOOKS_BUNDLE\" bundle is not installed. This can cause failures when installing " +
                            "sandbox bundles."
                )
            }
            throw SandboxException("Could not install $bundleSource as a bundle in sandbox $sandboxId.", e)
        }

        if (bundle.symbolicName == null)
            throw SandboxException(
                "Bundle at $bundleSource does not have a symbolic name, which would prevent serialisation."
            )
        return bundle
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

    /**
     * Returns the non-public [Sandbox] lowest in the stack of calls to this function, or null if no sandbox is found
     * on the stack.
     */
    private fun getCallingSandbox(): Sandbox? {
        val stackWalkerInstance = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

        return stackWalkerInstance.walk { stackFrameStream ->
            stackFrameStream
                .asSequence()
                .mapNotNull { stackFrame ->
                    val bundle = bundleUtils.getBundle(stackFrame.declaringClass)
                    if (bundle != null) {
                        (sandboxes.values - publicSandboxes).find { sandbox -> sandbox.containsBundle(bundle) }
                    } else null
                }
                .firstOrNull()
        }
    }
}
