package net.corda.internal.serialization;

import net.corda.v5.application.services.serialization.SerializationService;
import net.corda.v5.base.annotations.DoNotImplement;

/**
 * Marker interface for RPC client serialization.
 */
@DoNotImplement
public interface RpcClientSerializationService extends SerializationService { }
