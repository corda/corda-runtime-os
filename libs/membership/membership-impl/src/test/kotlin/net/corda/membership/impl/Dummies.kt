package net.corda.membership.impl

import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.v5.base.exceptions.ValueNotFoundException

data class DummyObjectWithText(val text: String)

data class DummyObjectWithNumberAndText(val number: Int, val text: String)

class DummyConverter : CustomPropertyConverter<DummyObjectWithNumberAndText> {
    override val type: Class<DummyObjectWithNumberAndText>
        get() = DummyObjectWithNumberAndText::class.java

    override fun convert(context: ConversionContext): DummyObjectWithNumberAndText {
        return DummyObjectWithNumberAndText(
            context.value("number")?.toInt()
                ?: throw ValueNotFoundException("'number' is not present or null"),
            context.value("text")
                ?: throw ValueNotFoundException("'text' is not present or null")
        )
    }
}