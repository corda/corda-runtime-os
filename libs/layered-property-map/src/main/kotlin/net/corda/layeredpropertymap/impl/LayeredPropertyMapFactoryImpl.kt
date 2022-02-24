package net.corda.layeredpropertymap.impl

import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption

@Component(service = [LayeredPropertyMapFactory::class])
class LayeredPropertyMapFactoryImpl @Activate constructor(
    @Reference(
        service = CustomPropertyConverter::class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    val customConverters: List<CustomPropertyConverter<out Any>>
) : LayeredPropertyMapFactory {

    private val converter = PropertyConverter(customConverters.associateBy { it.type })

    companion object {
        val logger = contextLogger()
    }

    init {
        logger.info(
            "{}", customConverters.joinToString(
                prefix = "Loaded custom property converters: [",
                postfix = "]",
                transform = { it.javaClass.name }
            )
        )
    }

    override fun create(properties: Map<String, String?>): LayeredPropertyMap {
        logger.debug("Creating new instance of LayeredPropertyMapImpl")
        return LayeredPropertyMapImpl(properties, converter)
    }
}