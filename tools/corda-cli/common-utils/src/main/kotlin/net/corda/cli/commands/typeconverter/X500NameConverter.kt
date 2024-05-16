package net.corda.cli.commands.typeconverter

import net.corda.v5.base.types.MemberX500Name
import picocli.CommandLine

class X500NameConverter : CommandLine.ITypeConverter<MemberX500Name> {

    override fun convert(value: String): MemberX500Name {
        return MemberX500Name.parse(value)
    }
}
