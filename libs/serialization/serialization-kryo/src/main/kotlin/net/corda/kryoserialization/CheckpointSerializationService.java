package net.corda.kryoserialization;

import net.corda.v5.application.services.serialization.SerializationService;
import net.corda.v5.base.annotations.DoNotImplement;

/**
 * Marker interface for checkpoint serialization.
 */
@DoNotImplement
public interface CheckpointSerializationService extends SerializationService { }
