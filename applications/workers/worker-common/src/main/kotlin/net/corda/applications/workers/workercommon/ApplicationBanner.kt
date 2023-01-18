package net.corda.applications.workers.workercommon

import net.corda.application.addon.CordaAddonResolver
import net.corda.application.banner.ConsolePrinter
import net.corda.application.banner.StartupBanner
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ApplicationBanner::class])
class ApplicationBanner(
    val startupBanner: StartupBanner,
    val addonResolver: CordaAddonResolver,
    private val consolePrinter: ConsolePrinter
)
{
    @Activate
    constructor(
        @Reference(service = StartupBanner::class)
        startupBanner: StartupBanner,
        @Reference(service = CordaAddonResolver::class)
        addonResolver: CordaAddonResolver,
    ):this(startupBanner, addonResolver, ConsolePrinter())

    private companion object {
        private val logger = contextLogger()
    }

    fun show(name: String, platformInfoProvider: PlatformInfoProvider) {
        consolePrinter.println(
            startupBanner.get(name, platformInfoProvider.localWorkerSoftwareVersion))
        val addons = addonResolver.findAll()
        if(addons.isEmpty()) return

        consolePrinter.printPaddedLine("Available add-ons:")

        addons.forEach {
            logger.info("Addon available: ${it.name} (${it.version})")
            consolePrinter.printEmptyLine()
            consolePrinter.printPaddedLine("${it.name} (${it.version})")
            consolePrinter.printLeftPad("${it.description}")
            consolePrinter.println()
            consolePrinter.printLeftPad("${it.vendor}")
            consolePrinter.println()
            consolePrinter.printLeftPad("${it.licence}")
        }
        consolePrinter.printEmptyLine()
    }
}
