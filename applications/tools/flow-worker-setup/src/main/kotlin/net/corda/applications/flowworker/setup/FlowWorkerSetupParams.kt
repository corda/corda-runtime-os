package net.corda.applications.flowworker.setup

import net.corda.tools.setup.common.DefaultSetupParams
import picocli.CommandLine

class FlowWorkerSetupParams {
    @CommandLine.Mixin
    var defaultParams = DefaultSetupParams()

    @CommandLine.Option(
        names = ["--scheduleCleanup"],
        description = ["Schedule cleanup of the flow mapper state"]
    )
    var scheduleCleanup: Boolean = false
}
