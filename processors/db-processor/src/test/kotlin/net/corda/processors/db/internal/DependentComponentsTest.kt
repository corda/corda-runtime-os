package net.corda.processors.db.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.write.ConfigWriteService
import net.corda.lifecycle.LifecycleCoordinatorName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DependentComponentsTest {

    @Test
    fun test() {

        data class Container(private val configWriteService: ConfigWriteService,
                             private val configurationReadService: ConfigurationReadService) {

            val dependentComponents = DependentComponents.of(::configWriteService, ::configurationReadService)
        }

        val configWriteService = mock<ConfigWriteService>()
        val configurationReadService = mock<ConfigurationReadService>()

        val container = Container(configWriteService, configurationReadService)
        assertEquals(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigWriteService>(),
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
            ), container.dependentComponents.coordinatorNames
        )

        container.dependentComponents.startAll()
        verify(configWriteService).start()
        verify(configurationReadService).start()

        container.dependentComponents.stopAll()
        verify(configWriteService).stop()
        verify(configurationReadService).stop()
    }
}