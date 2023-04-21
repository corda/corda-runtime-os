package net.corda.internal.serialization;

import net.corda.v5.application.serialization.SerializationService;
import net.corda.v5.base.annotations.DoNotImplement;

/**
 * Marker interface for P2P serialization.
 */
@DoNotImplement
public interface P2pSerializationService extends SerializationService { }
