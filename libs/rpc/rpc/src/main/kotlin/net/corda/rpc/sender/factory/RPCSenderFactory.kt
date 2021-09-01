package net.corda.rpc.sender.factory

import com.typesafe.config.Config
import net.corda.rpc.sender.RPCSender

interface RPCSenderFactory {

    /**
     * Create an instance of the [RPCSender]
     * @param bootstrapConfig configuration object used to bootstrap the service
     */
    fun createRPCSender(bootstrapConfig: Config): RPCSender

}