package net.corda.cli.commands.preinstall

import java.util.concurrent.Callable
import net.corda.sdk.preinstall.checker.LimitsChecker
import picocli.CommandLine
import picocli.CommandLine.Parameters

@CommandLine.Command(
    name = "check-limits",
    description = ["Check the resource limits have been assigned correctly."],
    mixinStandardHelpOptions = true
)
class CheckLimits : Callable<Int> {

    @Parameters(index = "0", description = ["YAML file containing resource limit overrides for the Corda install"])
    lateinit var path: String

    override fun call(): Int = LimitsChecker(path).check()
}
