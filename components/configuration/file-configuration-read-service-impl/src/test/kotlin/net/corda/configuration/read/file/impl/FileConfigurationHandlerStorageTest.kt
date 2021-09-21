package net.corda.configuration.read.file.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class FileConfigurationHandlerStorageTest {

    @Test
    fun `an added callback is invoked when subscription happens after registration`() {
        val storage = FileConfigurationHandlerStorage()
        val sub = FileConfigReadServiceStub()
        val keys = setOf("foo", "bar")
        val configMap = buildConfigMap(keys)
        storage.add { changedKeys, newConfig ->
            assertEquals(keys, changedKeys)
            assertEquals(configMap, newConfig)
        }
        sub.start()
        storage.addSubscription(sub)
        sub.postEvent(keys, configMap)
    }

    @Test
    fun `an added callback is invoked when subscription happens before registration`() {
        val storage = FileConfigurationHandlerStorage()
        val sub = FileConfigReadServiceStub()
        sub.start()
        storage.addSubscription(sub)
        val keys = setOf("foo", "bar")
        val configMap = buildConfigMap(keys)
        storage.add { changedKeys, newConfig ->
            assertEquals(keys, changedKeys)
            assertEquals(configMap, newConfig)
        }
        sub.postEvent(keys, configMap)
    }

    @Test
    fun `closing a callback results in it not being invoked`() {
        val storage = FileConfigurationHandlerStorage()
        val sub = FileConfigReadServiceStub()
        sub.start()
        storage.addSubscription(sub)
        val keys = setOf("foo", "bar")
        val configMap = buildConfigMap(keys)
        val handle = storage.add { changedKeys, newConfig ->
            assertEquals(keys, changedKeys)
            assertEquals(configMap, newConfig)
        }
        handle.close()
        sub.postEvent(keys, configMap)
        sub.assertNoListeners()
    }

    @Test
    fun `removing the subscription stops any events being posted to listeners`() {
        val storage = FileConfigurationHandlerStorage()
        val sub = FileConfigReadServiceStub()
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

    private fun buildConfigMap(keys: Set<String>): Map<String, Config> {
        var counter = 0
        return keys.associateWith {
            counter++
            ConfigFactory.parseMap(mapOf(it to counter))
        }
    }
}