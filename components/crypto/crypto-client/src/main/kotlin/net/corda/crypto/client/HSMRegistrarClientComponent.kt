package net.corda.crypto.client

import net.corda.crypto.clients.HSMRegistrarClient
import net.corda.lifecycle.Lifecycle

interface HSMRegistrarClientComponent : HSMRegistrarClient, Lifecycle