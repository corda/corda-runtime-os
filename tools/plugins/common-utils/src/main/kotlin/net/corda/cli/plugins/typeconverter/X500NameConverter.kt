package net.corda.cli.plugins.typeconverter

import net.corda.v5.base.types.MemberX500Name
import picocli.CommandLine

class X500NameConverter : CommandLine.ITypeConverter<MemberX500Name?> {

    override fun convert(value: String?): MemberX500Name? {
        return value?.let { MemberX500Name.parse(it) }
    }
}
