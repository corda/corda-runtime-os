package net.corda.v5.testing.uuid;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public interface UUIDProvider {
    @NotNull
    UUID getUUID();
}
