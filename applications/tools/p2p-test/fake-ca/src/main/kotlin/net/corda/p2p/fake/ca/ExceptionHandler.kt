package net.corda.p2p.fake.ca

import picocli.CommandLine
import picocli.CommandLine.IExecutionExceptionHandler
import java.lang.Exception

internal class ExceptionHandler : IExecutionExceptionHandler {
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
