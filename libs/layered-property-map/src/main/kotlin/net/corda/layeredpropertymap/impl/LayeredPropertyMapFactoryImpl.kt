package net.corda.layeredpropertymap.impl

import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Component(
    service = [ LayeredPropertyMapFactory::class ],
    reference = [
        Reference(
            name = LayeredPropertyMapFactoryImpl.CUSTOM_CONVERTERS_REFERENCE_NAME,
            service = CustomPropertyConverter::class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
        )
    ]
)
class LayeredPropertyMapFactoryImpl @Activate constructor(
    private val componentContext: ComponentContext
) : LayeredPropertyMapFactory {
    companion object {
        const val CUSTOM_CONVERTERS_REFERENCE_NAME = "customConverters"
        private val logger = contextLogger()
    }

    @Suppress("unchecked_cast", "SameParameterValue")
    private fun <T> fetchServices(name: String): List<T> {
        return (componentContext.locateServices(name) as? Array<T>)?.toList() ?: emptyList()
    }

    private val customConverters: List<CustomPropertyConverter<out Any>>
        get() = fetchServices(CUSTOM_CONVERTERS_REFERENCE_NAME)

    private val converter
        get() = PropertyConverter(customConverters.associateBy(CustomPropertyConverter<*>::type))

    override fun createMap(properties: Map<String, String?>): LayeredPropertyMap {
        logger.debug("Creating new instance of LayeredPropertyMapImpl")
        return LayeredPropertyMapImpl(properties, converter)
    }
}