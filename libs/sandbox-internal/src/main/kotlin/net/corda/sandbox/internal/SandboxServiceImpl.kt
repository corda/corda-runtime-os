@file:JvmName("SandboxServiceUtils")
package net.corda.sandbox.internal

import net.corda.libs.packaging.Cpk
import net.corda.sandbox.RequireSandboxHooks
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
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.Bundle
import org.osgi.framework.Bundle.RESOLVED
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleException
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.InputStream
import java.security.AccessController.doPrivileged
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.PrivilegedAction
import java.util.UUID
import kotlin.streams.asSequence


/** An implementation of [SandboxCreationService] and [SandboxContextService]. */
@Suppress("TooManyFunctions")
@Component(service = [SandboxCreationService::class, SandboxContextService::class])
@RequireSandboxHooks
internal class SandboxServiceImpl @Activate constructor(
    @Reference
    private val bundleUtils: BundleUtils,
    private val bundleContext: BundleContext
) : SandboxCreationService, SandboxContextService, SingletonSerializeAsToken {
    private val serviceComponentRuntimeBundleId = bundleUtils.getServiceRuntimeComponentBundle()?.bundleId
        ?: throw SandboxException(
            "The sandbox service cannot run without the Service Component Runtime bundle installed."
        )

    // Maps each bundle ID to the sandbox that the bundle is part of.
    private val bundleIdToSandbox = mutableMapOf<Long, Sandbox>()

    // Maps each bundle ID to the sandbox group that the bundle is part of.
    private val bundleIdToSandboxGroup = mutableMapOf<Long, SandboxGroup>()

    // The public sandboxes that have been created.
    private val publicSandboxes = mutableSetOf<Sandbox>()

    // The symbolic names of our public "platform" bundles.
    private val publicSymbolicNames = mutableSetOf<String>()

    // Bundles that failed to uninstall when a sandbox group was unloaded.
    private val zombieBundles = mutableSetOf<Bundle>()

    private val logger = loggerFor<SandboxServiceImpl>()

    override fun createPublicSandbox(publicBundles: Iterable<Bundle>, privateBundles: Iterable<Bundle>) {
        if (publicSandboxes.isNotEmpty()) {
            val publicSandbox = publicSandboxes.first()
            check(publicBundles.toSet() == publicSandbox.publicBundles
                    && privateBundles.toSet() == publicSandbox.privateBundles) {
                "Public sandbox was already created with different bundles"
            }
            logger.warn("Public sandbox was already created")
        }
        val publicSandbox = SandboxImpl(UUID.randomUUID(), publicBundles.toSet(), privateBundles.toSet())
        publicSandbox.allBundles.forEach { bundle ->
            bundleIdToSandbox[bundle.bundleId] = publicSandbox
        }
        publicSandbox.publicBundles.forEach { bundle ->
            publicSymbolicNames.add(bundle.symbolicName)
        }
        publicSandboxes.add(publicSandbox)
    }

    override fun createSandboxGroup(cpks: Iterable<Cpk>, securityDomain: String) =
        createSandboxes(cpks, securityDomain, startBundles = true)

    override fun createSandboxGroupWithoutStarting(cpks: Iterable<Cpk>, securityDomain: String) =
        createSandboxes(cpks, securityDomain, startBundles = false)

    override fun unloadSandboxGroup(sandboxGroup: SandboxGroup) {
        val sandboxGroupInternal = sandboxGroup as SandboxGroupInternal

        sandboxGroupInternal.cpkSandboxes.forEach { sandbox ->
            val unloaded = sandbox.unload()
            unloaded[false]?.also(zombieBundles::addAll)
            unloaded[true]?.forEach { bundle ->
                val bundleId = bundle.bundleId
                bundleIdToSandbox.remove(bundleId)
                bundleIdToSandboxGroup.remove(bundleId)
            }
        }

        // Tell the OSGi framework that these bundles need updating (i.e. removing, in this case).
        bundleUtils.refreshBundles(sandboxGroup.metadata.keys) { evt ->
            logger.debug("Refreshed bundle {}", evt.bundle)
        }
    }

    @Suppress("ComplexMethod")
    override fun hasVisibility(lookingBundle: Bundle, lookedAtBundle: Bundle): Boolean {
        val lookingSandbox = bundleIdToSandbox[lookingBundle.bundleId]
        val lookedAtSandbox = bundleIdToSandbox[lookedAtBundle.bundleId]

        return when {
            lookingBundle in zombieBundles || lookedAtBundle in zombieBundles -> false
            // These two framework bundles require full visibility.
            lookingBundle.bundleId in listOf(SYSTEM_BUNDLE_ID, serviceComponentRuntimeBundleId) -> true
            // Do both bundles belong to the same sandbox, or is neither bundle in a sandbox?
            lookedAtSandbox === lookingSandbox -> true
            // Does only one of the bundles belong to a sandbox?
            lookedAtSandbox == null || lookingSandbox == null -> false
            // Is the looked-at bundle a public bundle in a public sandbox?
            lookedAtBundle in publicSandboxes.flatMap { sandbox -> sandbox.publicBundles } -> true
            // Does the looking sandbox not have visibility of the looked at sandbox?
            !lookingSandbox.hasVisibility(lookedAtSandbox) -> false
            // Is the looked-at bundle a public bundle in the looked-at sandbox?
            lookedAtBundle in lookedAtSandbox.publicBundles -> true

            else -> false
        }
    }

    override fun getCallingSandboxGroup(): SandboxGroup? {
        return doPrivileged(PrivilegedAction {
            val stackWalkerInstance = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

            stackWalkerInstance.walk { stackFrameStream ->
                stackFrameStream
                    .asSequence()
                    .mapNotNull { stackFrame ->
                        val bundle = bundleUtils.getBundle(stackFrame.declaringClass)
                        if (bundle != null) {
                            bundleIdToSandboxGroup[bundle.bundleId]
                        } else null
                    }.firstOrNull()
            }
        })
    }

    override fun isSandboxed(bundle: Bundle) = bundleIdToSandbox[bundle.bundleId] != null

    override fun areInSameSandbox(bundleOne: Bundle, bundleTwo: Bundle): Boolean {
        val sandboxOne = bundleIdToSandbox[bundleOne.bundleId]
        val sandboxTwo = bundleIdToSandbox[bundleTwo.bundleId]
        return sandboxOne != null && sandboxOne === sandboxTwo
    }

    /**
     * Creates a [SandboxGroup] in the [securityDomain], containing a [Sandbox] for each of the [cpks]. On the first
     * run, also initialises a public sandbox. [startBundles] controls whether the CPK bundles are also started.
     *
     * Grants each sandbox visibility of the public sandboxes and of the other sandboxes in the group.
     */
    private fun createSandboxes(
        cpks: Iterable<Cpk>,
        securityDomain: String,
        startBundles: Boolean
    ): SandboxGroup {
        sandboxForbidsThat(securityDomain.contains('/')) {
            "Security domain cannot contain a '/' character."
        }

        // Verify that CPK files were not tampered with
        // TODO there is a small time window between verification and installation during which CPK files might still be modified
        verifyCpks(cpks)

        // We track the bundles that are being created, so that we can start them all at once at the end if needed.
        val bundles = mutableSetOf<Bundle>()

        val newSandboxes = cpks.map { cpk ->
            val sandboxId = UUID.randomUUID()

            val mainBundle = installBundle(
                "${cpk.metadata.cpkId.name}-${cpk.metadata.cpkId.version}/${cpk.metadata.mainBundle}",
                // TODO - only pass in metadata and inject in service to get binary
                cpk.getMainBundle(),
                sandboxId,
                securityDomain
            )
            sandboxForbidsThat(mainBundle.isFragment) {
                "CPK main bundle $mainBundle cannot be a fragment"
            }

            val libraryBundles = cpk.metadata.libraries.mapTo(LinkedHashSet()) { libraryJar ->
                installBundle(
                    "${cpk.metadata.cpkId.name}-${cpk.metadata.cpkId.version}/$libraryJar",
                    cpk.getResourceAsStream(libraryJar),
                    sandboxId,
                    securityDomain
                )
            }

            bundles.addAll(libraryBundles)
            bundles.add(mainBundle)

            val sandbox = CpkSandboxImpl(
                sandboxId,
                cpk.metadata,
                mainBundle,
                libraryBundles)

            (libraryBundles + mainBundle).forEach { bundle ->
                bundleIdToSandbox[bundle.bundleId] = sandbox
            }

            sandbox
        }

        newSandboxes.forEach { newSandbox ->
            // Each sandbox requires visibility of the sandboxes of the other CPKs and of the public sandboxes.
            newSandbox.grantVisibility(newSandboxes - newSandbox)
        }

        // Ensure that all of these bundles are resolved before we start them.
        if (!bundleUtils.resolveBundles(bundles)) {
            val allFailed = bundles.filter { !it.isFragment && it.state < RESOLVED }
            val ex = SandboxException("Failed to resolve bundles: ${allFailed.joinToString()}")
            for (failed in allFailed) {
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

        // We only start the bundles once all the CPKs' bundles have been installed and sandboxed, since there are
        // likely dependencies between the CPKs' bundles.
        if (startBundles) {
            startBundles(bundles)
        }

        val sandboxGroup = SandboxGroupImpl(newSandboxes, publicSandboxes, ClassTagFactoryImpl(), bundleUtils)

        bundles.forEach { bundle ->
            bundleIdToSandboxGroup[bundle.bundleId] = sandboxGroup
        }

        return sandboxGroup
    }

    /**
     * Verifies that [cpks] haven't been tampered with by calculating their checksum and validating them against
     * expected values.
     */
    private fun verifyCpks(cpks: Iterable<Cpk>) {
        cpks.forEach(::verifyCpkChecksum)
    }

    /**
     * Calculates [cpk]'s checksum and validates it against expected value
     */
    private fun verifyCpkChecksum(cpk: Cpk) {
        sandboxRequiresThat(checksum(cpk.getInputStream()) == cpk.metadata.fileChecksum) {
            "File checksum validation failed for CPK ${cpk.metadata.cpkId.name} during sandbox creation"
        }
    }

    /**
     * Calculates [inputStream]'s checksum
     */
    private fun checksum(inputStream: InputStream): SecureHash {
        val digest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        DigestInputStream(inputStream, digest).use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (it.read(buffer) != -1) {
                // read all bytes to calculate digest
            }
        }
        return SecureHash(digest.algorithm, digest.digest())
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
            inputStream.use {
                bundleContext.installBundle(sandboxLocation.toString(), it)
            }
        } catch (e: BundleException) {
            if (bundleUtils.allBundles.none { bundle -> bundle.symbolicName == SANDBOX_HOOKS_BUNDLE }) {
                logger.warn(
                    "The \"$SANDBOX_HOOKS_BUNDLE\" bundle is not installed. This can cause failures when installing " +
                            "sandbox bundles."
                )
            }
            throw SandboxException("Could not install $bundleSource as a bundle in sandbox $sandboxId.", e)
        }

        sandboxForbidsThat(bundle.symbolicName == null) {
            "Bundle at $bundleSource does not have a symbolic name, which would prevent serialisation."
        }
        sandboxForbidsThat(bundle.symbolicName in publicSymbolicNames) {
            "Bundle ${bundle.symbolicName} shadows a Corda platform bundle."
        }
        return bundle
    }

    /**
     * Starts each of the [bundles].
     *
     * Throws [SandboxException] if a bundle cannot be started.
     * */
    private fun startBundles(bundles: Collection<Bundle>) {
        // OSGi merges fragments with their host bundle,
        // and so we only start non-fragment bundles.
        bundles.filterNot(Bundle::isFragment).forEach { bundle ->
            try {
                bundle.start()
            } catch (e: BundleException) {
                throw SandboxException("Bundle $bundle could not be started.", e)
            }
        }
    }
}

// "Syntactic sugar" around throwing a SandboxException, just to shut Detekt up.
private inline fun sandboxForbidsThat(condition: Boolean, message: () -> String) {
    if (condition) {
        throw SandboxException(message())
    }
}

// "Syntactic sugar" around throwing a SandboxException, just to shut Detekt up.
private inline fun sandboxRequiresThat(condition: Boolean, message: () -> String)
    = sandboxForbidsThat(!condition, message)
