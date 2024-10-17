package net.corda.uniqueness.backingstore.osgi

import net.corda.ledger.libs.uniqueness.backingstore.BackingStore
import net.corda.lifecycle.Lifecycle


interface BackingStoreLifecycle : BackingStore, Lifecycle