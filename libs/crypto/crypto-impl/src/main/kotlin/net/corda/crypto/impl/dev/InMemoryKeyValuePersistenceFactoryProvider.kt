package net.corda.crypto.impl.dev

import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactoryProvider
import org.osgi.service.component.annotations.Component

@Component(service = [KeyValuePersistenceFactoryProvider::class])
class InMemoryKeyValuePersistenceFactoryProvider : KeyValuePersistenceFactoryProvider {
    companion object {
        const val NAME = "dev"
    }

    private val factory = InMemoryKeyValuePersistenceFactory()

    override val name: String = NAME

    override fun create(): KeyValuePersistenceFactory = factory
}