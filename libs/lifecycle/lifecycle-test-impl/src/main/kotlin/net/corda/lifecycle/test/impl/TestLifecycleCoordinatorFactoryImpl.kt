package net.corda.lifecycle.test.impl

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryCoordinatorAccess
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl

class TestLifecycleCoordinatorFactoryImpl(
    val registry: LifecycleRegistryCoordinatorAccess = LifecycleRegistryImpl(),
    private val factory: LifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(
        registry,
        TestLifecycleCoordinatorSchedulerFactory()
    )
) : LifecycleCoordinatorFactory by factory
