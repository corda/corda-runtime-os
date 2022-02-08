package net.corda.example.vnode

import java.nio.file.Paths
import java.util.Hashtable
import net.corda.install.InstallService
import net.corda.sandbox.SandboxCreationService
import org.osgi.annotation.bundle.Capability
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SERVICE_RANKING
import org.osgi.framework.ServiceRegistration
import org.osgi.namespace.service.ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE
import org.osgi.namespace.service.ServiceNamespace.SERVICE_NAMESPACE
import org.osgi.resource.Namespace.EFFECTIVE_ACTIVE
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Capability(
    namespace = SERVICE_NAMESPACE,
    attribute = [ "${CAPABILITY_OBJECTCLASS_ATTRIBUTE}:List<String>=\""
        + "net.corda.install.InstallService,"
        + "net.corda.example.vnode.LoaderService"
        + '\"' ],
    uses = [ InstallService::class, LoaderService::class ],
    effective = EFFECTIVE_ACTIVE
)
@Component(immediate = true, service = [])
class InstallServiceFactory @Activate constructor(
    @Reference
    configAdmin: ConfigurationAdmin,

    @Reference
    sandboxCreator: SandboxCreationService,

    bundleContext: BundleContext
) {
    private val installServiceRegistration: ServiceRegistration<*>

    init {
        val baseDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath()
        configAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
            val properties = Hashtable<String, Any?>()
            properties[BASE_DIRECTORY_KEY] = baseDir.toString()
            config.update(properties)
        }

        val installProperties = Hashtable<String, Any?>().apply {
            put(SERVICE_RANKING, Int.MAX_VALUE)
        }

        installServiceRegistration = bundleContext.registerService(
            arrayOf(InstallService::class.java.name, LoaderService::class.java.name),
            InstallServiceImpl(baseDir.resolve("cpk")),
            installProperties
        )

        val (publicBundles, privateBundles) = bundleContext.bundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreator.createPublicSandbox(publicBundles, privateBundles)
    }

    @Suppress("unused")
    @Deactivate
    fun done() {
        installServiceRegistration.unregister()
    }
}
