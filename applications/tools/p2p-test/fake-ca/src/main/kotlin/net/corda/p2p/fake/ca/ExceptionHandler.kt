package net.corda.p2p.fake.ca

import picocli.CommandLine
import picocli.CommandLine.IExecutionExceptionHandler
import java.lang.Exception

internal class ExceptionHandler : IExecutionExceptionHandler {
    override fun handleExecutionException(
        ex: Exception,
        commandLine: CommandLine,
        parseResult: CommandLine.ParseResult,
    ): Int {
        System.err.println(ex.message)
        return -1
    }
}
