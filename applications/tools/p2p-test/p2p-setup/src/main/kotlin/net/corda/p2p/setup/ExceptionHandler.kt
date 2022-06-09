package net.corda.p2p.setup

import picocli.CommandLine
import java.lang.Exception

internal class ExceptionHandler : CommandLine.IExecutionExceptionHandler {
    override fun handleExecutionException(
        exception: Exception,
        commandLine: CommandLine,
        parseResult: CommandLine.ParseResult,
    ): Int {
        if (parseResult.hasMatchedOption("--stacktrace")) {
            throw exception
        }
        System.err.println(exception.message)
        return -1
    }
}
