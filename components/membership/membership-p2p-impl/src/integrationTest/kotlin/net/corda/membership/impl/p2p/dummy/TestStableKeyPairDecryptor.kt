package net.corda.membership.impl.p2p.dummy

import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory
import java.security.PublicKey

interface TestStableKeyPairDecryptor : StableKeyPairDecryptor

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [StableKeyPairDecryptor::class, TestStableKeyPairDecryptor::class])
internal class TestStableKeyPairDecryptorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : TestStableKeyPairDecryptor {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator =
        coordinatorFactory.createCoordinator(LifecycleCoordinatorName.forComponent<StableKeyPairDecryptor>()) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("TestStableKeyPairDecryptor starting.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("TestStableKeyPairDecryptor starting.")
        coordinator.stop()
    }

    override fun decrypt(
        tenantId: String,
        salt: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray = cipherText
}