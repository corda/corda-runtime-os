package net.corda.v5.application.flows;

import net.corda.v5.base.annotations.DoNotImplement;

/**
 * Marker interface from which all flow types inherit.
 * <p>
 * This should not be implemented directly when writing a flow. Instead, one of the more specialised types of flow
 * should be implemented:
 * <ul><li>{@link ClientStartableFlow} for flows that are started via remote client, e.g. REST.</li>
 * <li>{@link ResponderFlow} for flows that are started by a peer session.</li>
 * <li>{@link SubFlow} for flows that are started from within other flows.</li></ul>
 */
@DoNotImplement
public interface Flow {
}
