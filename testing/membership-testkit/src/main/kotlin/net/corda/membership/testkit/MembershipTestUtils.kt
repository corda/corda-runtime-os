package net.corda.membership.testkit

import net.corda.layeredpropertymap.LayeredPropertyMapImpl
import net.corda.v5.membership.conversion.ConversionContext
import net.corda.v5.membership.conversion.CustomPropertyConverter
import net.corda.v5.membership.conversion.LayeredPropertyMap
import net.corda.v5.membership.conversion.PropertyConverter
import org.osgi.service.component.annotations.Component
import java.util.SortedMap

data class DummyObjectWithText(val text: String)

data class DummyNumber(val number: Int)

data class DummyObjectWithNumberAndText(val number: Int, val text: String)

@Component(service = [CustomPropertyConverter::class])
class DummyConverter: CustomPropertyConverter<DummyObjectWithNumberAndText> {
    override val type: Class<DummyObjectWithNumberAndText>
        get() = DummyObjectWithNumberAndText::class.java

    override fun convert(context: ConversionContext): DummyObjectWithNumberAndText {
        return DummyObjectWithNumberAndText(
            context.findValueByPattern("number")?.toInt() ?: throw NullPointerException(),
            context.findValueByPattern("text") ?: throw NullPointerException()
        )
    }
}

fun createContext(
    map: SortedMap<String, String?>,
    converter: PropertyConverter,
    storeClass: Class<out LayeredPropertyMap>,
    key: String
) = ConversionContext(
    LayeredPropertyMapImpl(
        map,
        converter
    ),
    storeClass,
    key
)