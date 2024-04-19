package net.corda.cli.plugins.preinstall

import java.util.concurrent.Callable
import net.corda.sdk.preinstall.checker.KafkaChecker
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@CommandLine.Command(
    name = "check-kafka",
    description = ["Check that Kafka is up and that the credentials work."],
    mixinStandardHelpOptions = true
)
class CheckKafka : Callable<Int> {

    @Parameters(
        index = "0",
        description = ["YAML file containing the Kafka, SASL, and TLS configurations"]
    )
    lateinit var path: String

    @Option(
        names = ["-n", "--namespace"],
        description = ["The namespace in which to look for both the Kafka secrets"]
    )
    var namespace: String? = null

    @Option(
        names = ["-t", "--timeout"],
        description = ["The timeout in milliseconds for testing the Kafka connection - defaults to 3000"]
    )
    var timeout: Int = 3000

    override fun call(): Int = KafkaChecker(path, namespace, timeout).check()
}
