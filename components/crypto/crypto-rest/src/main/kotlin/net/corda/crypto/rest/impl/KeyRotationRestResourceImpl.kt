package net.corda.crypto.rest.impl

import net.corda.crypto.rest.KeyRotationRestResource
import net.corda.crypto.rest.response.KeyRotationResponse
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.rest.PluggableRestResource
import net.corda.rest.response.ResponseEntity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
//import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [PluggableRestResource::class])
class KeyRotationRestResourceImpl @Activate constructor(
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : KeyRotationRestResource, PluggableRestResource<KeyRotationRestResource>, Lifecycle {

    private companion object {
        //private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

    }

    override fun getKeyRotationStatus(): List<Pair<String, List<String>>> {
        TODO("Not yet implemented")
    }

    override fun rotateWrappingKey(oldKeyAlias: String, newKeyAlias: String): ResponseEntity<KeyRotationResponse> {
        TODO("Not yet implemented")
    }

    override val targetInterface: Class<KeyRotationRestResource> = KeyRotationRestResource::class.java

    override val protocolVersion: Int = platformInfoProvider.localWorkerPlatformVersion
    override val isRunning: Boolean
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}
