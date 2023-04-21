package net.corda.sandboxgroupcontext.service.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextComponentImpl.Companion.SANDBOX_CACHE_SIZE_DEFAULT
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SandboxGroupContextComponentImplTest {

    @Test
    fun `correct cache size is created when provided by config`() {
        val sandboxGroupContextService = mock<SandboxGroupContextServiceImpl>()
        val handler = argumentCaptor<LifecycleEventHandler>()
        val coordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
            whenever(it.createCoordinator(any(), handler.capture())).thenReturn(mock())
        }
        val config = SmartConfigFactory.createWithoutSecurityServices().create(
            ConfigFactory.parseString(
                """
              flow {
                cache {
                  size = 4
                }
              }
              persistence {
                cache {
                  size = 3
                }
              }
              verification {
                cache {
                  size = 2
                }
              }
        """.trimIndent()
            )
        )

        @Suppress("UNUSED_VARIABLE") val sandboxGroupContextComponentImpl = SandboxGroupContextComponentImpl(
            mock(),
            mock(),
            coordinatorFactory,
            sandboxGroupContextService,
            mock(),
            mock(),
        )

        handler.lastValue.processEvent(
            ConfigChangedEvent(
                setOf(ConfigKeys.SANDBOX_CONFIG),
                mapOf(ConfigKeys.SANDBOX_CONFIG to config)
            ),
            mock()
        )

        verify(sandboxGroupContextService).initCache(eq(SandboxGroupType.FLOW), eq(4))
        verify(sandboxGroupContextService).initCache(eq(SandboxGroupType.PERSISTENCE), eq(3))
        verify(sandboxGroupContextService).initCache(eq(SandboxGroupType.VERIFICATION), eq(2))
    }

    @Test
    fun `default cache size is created when no config is given`() {
        val sandboxGroupContextService = mock<SandboxGroupContextServiceImpl>()
        val handler = argumentCaptor<LifecycleEventHandler>()
        val coordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
            whenever(it.createCoordinator(any(), handler.capture())).thenReturn(mock())
        }
        val config = SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseString(""))

        @Suppress("UNUSED_VARIABLE") val sandboxGroupContextComponentImpl = SandboxGroupContextComponentImpl(
            mock(),
            mock(),
            coordinatorFactory,
            sandboxGroupContextService,
            mock(),
            mock(),
        )

        handler.lastValue.processEvent(
            ConfigChangedEvent(
                setOf(ConfigKeys.SANDBOX_CONFIG),
                mapOf(ConfigKeys.SANDBOX_CONFIG to config)
            ),
            mock()
        )

        verify(sandboxGroupContextService).initCache(eq(SandboxGroupType.FLOW), eq(SANDBOX_CACHE_SIZE_DEFAULT))
        verify(sandboxGroupContextService).initCache(eq(SandboxGroupType.PERSISTENCE), eq(SANDBOX_CACHE_SIZE_DEFAULT))
        verify(sandboxGroupContextService).initCache(eq(SandboxGroupType.VERIFICATION), eq(SANDBOX_CACHE_SIZE_DEFAULT))
    }
}