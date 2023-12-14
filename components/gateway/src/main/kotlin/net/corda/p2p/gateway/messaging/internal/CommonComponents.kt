package net.corda.p2p.gateway.messaging.internal

import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.messaging.DynamicKeyStore
import net.corda.p2p.gateway.messaging.http.TrustStoresMap

internal class CommonComponents(
    subscriptionFactory: SubscriptionFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig,
    cryptoOpsClient: CryptoOpsClient,
) : LifecycleWithDominoTile {
    val dynamicKeyStore = DynamicKeyStore(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        messagingConfiguration,
        cryptoOpsClient,
    )
    val trustStoresMap = TrustStoresMap(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        messagingConfiguration
    )
    private val children: Collection<DominoTile> =
        listOf(
            dynamicKeyStore.dominoTile,
            trustStoresMap.dominoTile,
        )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = children.map { it.coordinatorName },
        managedChildren = children.map { it.toNamedLifecycle() }
    )
}
