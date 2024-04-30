package net.corda.cli.plugins.typeconverter

import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class X500NameConverterTest {

    @Test
    fun `converts x500 name string to x500 name`() {
        val x500NameConverter = X500NameConverter()
        val x500NameString = "O=PartyA, L=London, C=GB"

        val x500Name = x500NameConverter.convert(x500NameString)
        assertThat(MemberX500Name.parse(x500NameString)).isEqualTo(x500Name)
    }
}
