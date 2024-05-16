package net.corda.cli.commands.typeconverter

import net.corda.crypto.core.ShortHash
import picocli.CommandLine

class ShortHashConverter : CommandLine.ITypeConverter<ShortHash> {

    override fun convert(value: String): ShortHash {
        return ShortHash.of(value)
    }
}
