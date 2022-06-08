package net.corda.configuration.read.reconcile

import net.corda.data.config.Configuration
import net.corda.reconciliation.ReconcilerReader

/**
 * A message bus reconciler reader for [Configuration].
 *
 * Fetches all <ConfigurationSection: String, [Configuration]> from [CONFIG_TOPIC] to be used for reconciliation.
 */
interface ConfigReconcilerReader : ReconcilerReader<String, Configuration>