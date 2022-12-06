package net.corda.layeredpropertymap.testkit

import java.lang.reflect.Proxy
import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.impl.LayeredPropertyMapFactoryImpl
import net.corda.v5.base.types.LayeredPropertyMap
import org.osgi.service.component.ComponentContext

object LayeredPropertyMapMocks {
    /**
     * Creates a new instance of the [LayeredPropertyMapFactory] with the specified list of custom converters.
     */
    @JvmStatic
    fun createFactory(
        customConverters: List<CustomPropertyConverter<out Any>> = emptyList()
    ): LayeredPropertyMapFactory = LayeredPropertyMapFactoryImpl(
        Proxy.newProxyInstance(LayeredPropertyMapMocks::class.java.classLoader, arrayOf(ComponentContext::class.java)) { _, method, args ->
            if (method.name == "locateServices" && args.size == 1 && args[0] == "customConverters") {
                return@newProxyInstance customConverters.toTypedArray()
            }
            throw UnsupportedOperationException("Not implemented - $method")
        } as ComponentContext
    )

    /**
     * Creates a new instance of [ConversionContext] with a new instance of [T] for specified [map] and [key]
     * where [T] is concrete class implementing LayeredPropertyMap, such as MemberContextImpl, MGMContextImpl, etc.
     */
    @JvmStatic
    inline fun <reified T: LayeredPropertyMap> createConversionContext(
        map: Map<String, String?>,
        customConverters: List<CustomPropertyConverter<out Any>>,
        key: String
    ) = ConversionContext(create<T>(map, customConverters), key)

    /**
     * Creates a new instance of [T] where [T] is concrete class implementing LayeredPropertyMap,
     * such as MemberContextImpl, MGMContextImpl, etc
     */
    @JvmStatic
    inline fun <reified T: LayeredPropertyMap> create(
        map: Map<String, String?>,
        customConverters: List<CustomPropertyConverter<out Any>> = emptyList()
    ): T = createFactory(customConverters).create(map)
}