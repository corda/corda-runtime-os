package net.corda.blobinspector

import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Callable

@CommandLine.Command(name = "blobinspector", mixinStandardHelpOptions = true, version = ["blobinspector 1.0"],
        description = ["Prints the content of a blob."])
class Command : Callable<Int> {
    @CommandLine.Parameters(arity = "0..1", description = ["The file containing a blob to inspect. Will use stdin if not specified."])
    var inputFile: File? = null

    @CommandLine.Option(names = ["-s", "--start"], description = ["The byte offset of the encoded bytes from the start of the blob."])
    var encodingStart: Int? = null

    @CommandLine.Option(names = ["-e", "--encoding"], description = ["The type of encoding of the bytes."])
    var encoding: String? = null

    @CommandLine.Option(names = ["-f", "--format"], description = ["The type of format of the bytes that is encoded."])
    var format: String? = null

    override fun call(): Int {
        val bytes = (inputFile?.let { FileInputStream(it) } ?: System.`in`).readFully().sequence()
        val decoded = Encoding.decodedBytes(bytes, encoding, encodingStart, format)
        println(decoded.result)
        return 0
    }
}