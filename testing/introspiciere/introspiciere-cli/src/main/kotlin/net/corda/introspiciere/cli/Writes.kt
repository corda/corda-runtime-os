package net.corda.introspiciere.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import java.io.OutputStream

fun Any.writeToYaml(outputStream: OutputStream) {
    val factory = YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ObjectMapper(factory).writeValue(outputStream, this)
}

fun <T> Iterable<T>.writeOnePerLine(outputStream: OutputStream, toStr: (T) -> String = { it.toString() }) {
    outputStream.bufferedWriter().autoFlush { writer -> this.map(toStr).forEach(writer::appendLine) }
}