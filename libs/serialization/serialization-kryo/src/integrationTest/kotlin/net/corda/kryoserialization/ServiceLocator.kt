package net.corda.kryoserialization

import net.corda.classinfo.ClassInfoService
import net.corda.install.InstallService
import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxCreationService
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(immediate = true, service = [ServiceLocator::class])
class ServiceLocator {

    companion object {
        private val serviceMap = mutableMapOf<String, Any>()

        fun getInstallService(): InstallService = getService()
        fun getSandboxCreationService(): SandboxCreationService = getService()
        fun getSandboxContextService(): SandboxContextService = getService()
        fun getClassInfoService(): ClassInfoService = getService()
        fun getConfigurationService(): ConfigurationAdmin = getService()

        private inline fun <reified T> getService(): T = serviceMap[T::class.java.name] as T

        private inline fun <reified T : Any> setService(service: T) {
            serviceMap[T::class.java.name] = service
        }
    }

    @Reference
    @Suppress("UNUSED")
    private fun setInstallService(installService: InstallService) = setService(installService)

    @Reference
    @Suppress("UNUSED")
    private fun setSandboxCreationService(sandboxCreationService: SandboxCreationService) = setService(sandboxCreationService)

    @Reference
    @Suppress("UNUSED")
    private fun setSandboxContextService(sandboxContextService: SandboxContextService) = setService(sandboxContextService)

    @Reference
    @Suppress("UNUSED")
    private fun setClassInfoService(classInfoService: ClassInfoService) = setService(classInfoService)

    @Reference
    @Suppress("UNUSED")
    private fun setConfigurationAdmin(configurationAdmin: ConfigurationAdmin) = setService(configurationAdmin)
}
