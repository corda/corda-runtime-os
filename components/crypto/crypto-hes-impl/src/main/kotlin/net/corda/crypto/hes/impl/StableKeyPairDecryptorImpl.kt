package net.corda.crypto.hes.impl

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.crypto.hes.core.impl.decryptWithStableKeyPair
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.AbstractComponentNotReadyException
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.security.PublicKey
import net.corda.utilities.trace

@Component(service = [StableKeyPairDecryptor::class])
class StableKeyPairDecryptorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient
) : StableKeyPairDecryptor {

    companion object {
        private val logger = LoggerFactory.getLogger(StableKeyPairDecryptor::class.java)
    }

    // VisibleForTesting
    val lifecycleCoordinator =  coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<StableKeyPairDecryptor>(),
        DependentComponents.of(
            ::cryptoOpsClient
        ),
        ::eventHandler
    )

    private var _impl: Impl? = null
    val impl: Impl
        get() {
            val tmp = _impl
            if (tmp == null || lifecycleCoordinator.status != LifecycleStatus.UP) {
                throw AbstractComponentNotReadyException("Component ${StableKeyPairDecryptor::class.simpleName} is not ready.")
            }
            return tmp
        }

    override fun decrypt(
        tenantId: String,
        salt: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray =
        impl.decrypt(tenantId, salt, publicKey, otherPublicKey, cipherText, aad)

    class Impl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val cryptoOpsClient: CryptoOpsClient
    ) {
        @Suppress("LongParameterList")
        fun decrypt(
            tenantId: String,
            salt: ByteArray,
            publicKey: PublicKey,
            otherPublicKey: PublicKey,
            cipherText: ByteArray,
            aad: ByteArray?
        ): ByteArray = decryptWithStableKeyPair(
            schemeMetadata = schemeMetadata,
            salt = salt,
            publicKey = publicKey,
            otherPublicKey = otherPublicKey,
            cipherText = cipherText,
            aad = aad
        ) { cryptoOpsClient.deriveSharedSecret(tenantId, it, otherPublicKey) }
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    if (_impl == null) {
                        doActivate(coordinator)
                    }
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
        }
    }

    private fun doActivate(coordinator: LifecycleCoordinator) {
        logger.trace { "Creating active implementation" }
        _impl = Impl(schemeMetadata, cryptoOpsClient)
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.trace { "Starting..." }
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.trace  { "Stopping..." }
        lifecycleCoordinator.stop()
    }
}