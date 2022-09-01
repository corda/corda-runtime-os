package net.corda.cli.plugins.packaging

import picocli.CommandLine
import picocli.CommandLine.TypeConversionException

class VersionConverter: CommandLine.ITypeConverter<Int> {
    @Throws(Exception::class)
    override fun convert(value: String): Int {
        val x= value.toInt()
        if (x<1) {
            throw TypeConversionException("Version number must be greater than zero")
        }
        return x
    }

}