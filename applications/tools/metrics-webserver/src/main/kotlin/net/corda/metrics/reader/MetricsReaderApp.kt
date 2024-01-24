package net.corda.metrics.reader

import picocli.CommandLine
import kotlin.concurrent.thread
import kotlin.system.exitProcess

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    val exitCode = CommandLine(MetricsReaderApp()).execute(*args)
    exitProcess(exitCode)
}

@CommandLine.Command(name = "metrics-reader")
class MetricsReaderApp : Runnable {

    @CommandLine.Option(names = ["-f", "--file"],
        required = false,
        defaultValue = "metrics.txt",
        description = ["Specify the path to the file containing metrics and measurements. Default filename is metrics.txt"]
    )
    private lateinit var fileName: String

    @CommandLine.Option(names = ["-p", "--port"],
        required = false,
        description = ["Webserver port"]
    )
    private var port: Int = 7004

    @CommandLine.Option(names = ["-h", "--help", "-help"], usageHelp = true, description = ["Display help and exit."])
    @Suppress("unused")
    var help = false

    override fun run() {
        MetricsReaderMain().apply {
            Runtime.getRuntime().addShutdownHook(thread(start = false, name = "Shutdownhook") {
                close()
            })
        }.start(fileName, port)
    }
}