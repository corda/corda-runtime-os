package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class ConfigurationHandlerStorageTest {

    @Test
    fun `an added callback is invoked when subscription happens after registration`() {
        val storage = ConfigurationHandlerStorage()
        val sub = ConfigReaderStub()
        val keys = setOf("foo", "bar")
        val configMap = buildConfigMap(keys)
        var handlerCalled = false
        storage.add { changedKeys, newConfig ->
            assertEquals(keys, changedKeys)
            assertEquals(configMap, newConfig)
            handlerCalled = true
        }
        sub.start()
        storage.addSubscription(sub)
        sub.postEvent(keys, configMap)
        assertTrue(handlerCalled)
    }

    @Test
    fun `an added callback is invoked when subscription happens before registration`() {
        val storage = ConfigurationHandlerStorage()
        val sub = ConfigReaderStub()
        sub.start()
        storage.addSubscription(sub)
        val keys = setOf("foo", "bar")
        val configMap = buildConfigMap(keys)
        var handlerCalled = false
        storage.add { changedKeys, newConfig ->
            assertEquals(keys, changedKeys)
            assertEquals(configMap, newConfig)
            handlerCalled = true

        }
        sub.postEvent(keys, configMap)
        assertTrue(handlerCalled)
    }

    @Test
    fun `closing a callback results in it not being invoked`() {
        val storage = ConfigurationHandlerStorage()
        val sub = ConfigReaderStub()
        sub.start()
        storage.addSubscription(sub)
        val keys = setOf("foo", "bar")
        val configMap = buildConfigMap(keys)
        val handle = storage.add { _, _ ->
            fail("Should not invoke this callback after handle is closed.")
        }
        handle.close()
        sub.postEvent(keys, configMap)
        sub.assertNoListeners()
    }

    @Test
    fun `removing the subscription stops any events being posted to listeners`() {
        val storage = ConfigurationHandlerStorage()
        val sub = ConfigReaderStub()
        sub.start()
        storage.addSubscription(sub)
        val keys = setOf("foo", "bar")
        val configMap = buildConfigMap(keys)
        storage.add { _, _ ->
            fail("Should not invoke this callback after subscription is removed.")
        }
        storage.removeSubscription()
        sub.postEvent(keys, configMap)
    }

    private fun buildConfigMap(keys: Set<String>) : Map<String, SmartConfig> {
        var counter = 0
        val configFactory = SmartConfigFactory.create(ConfigFactory.empty())
        return keys.associateWith {
            counter++
            configFactory.create(ConfigFactory.parseMap(mapOf(it to counter)))
        }
    }
}