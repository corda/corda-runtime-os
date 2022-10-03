package com.example.singletons

import net.corda.v5.application.flows.SubFlow
import net.corda.v5.testing.MapProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component
class TestDataProvider @Activate constructor(
    @Reference(target = "(corda.sandbox=*)")
    private val maps: List<MapProvider>
): SubFlow<Map<String, Any?>> {
    override fun call(): Map<String, Any?> {
        return LinkedHashMap<String, Any?>().apply {
            maps.map(MapProvider::getMap).forEach(::putAll)
        }
    }
}
