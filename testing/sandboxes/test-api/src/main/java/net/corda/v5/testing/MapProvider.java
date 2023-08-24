package net.corda.v5.testing;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

public interface MapProvider {
    @NotNull
    Map<String, ?> getMap();
}
