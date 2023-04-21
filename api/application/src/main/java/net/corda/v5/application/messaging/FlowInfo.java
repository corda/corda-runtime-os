package net.corda.v5.application.messaging;

import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;

@CordaSerializable
public interface FlowInfo {
    /**
     * @return The protocol name of the flow is running.
     */
    @NotNull
    String protocol();

    /**
     * @return The protocol version the flow is running.
     */
    @NotNull
    Integer protocolVersion();
}
