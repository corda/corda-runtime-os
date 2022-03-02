package net.corda.testdoubles.streams

import net.corda.testdoubles.GlobalVariableNotInitialised
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

internal class StandardStreamsExtension : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext?) {
        streams = Array(3) { InMemoryStream() }
    }

    override fun afterEach(context: ExtensionContext?) {
        streams.filterNotNull().forEach(InMemoryStream::close)
        streams = arrayOf(null, null, null)
    }
}

val inMemoryStdin: InMemoryStream
    get() = streams[0] ?: throw GlobalVariableNotInitialised(::inMemoryStdin)

val inMemoryStdout: InMemoryStream
    get() = streams[1] ?: throw GlobalVariableNotInitialised(::inMemoryStdout)

val inMemoryStderr: InMemoryStream
    get() = streams[2] ?: throw GlobalVariableNotInitialised(::inMemoryStderr)

private var streams: Array<InMemoryStream?> = arrayOf(null, null, null)

class InMemoryStandardStreamNotInitException(stream: String) : Exception("Global in-memory $stream has not been init")
