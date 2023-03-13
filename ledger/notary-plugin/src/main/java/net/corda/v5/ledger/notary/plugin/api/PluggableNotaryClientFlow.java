package net.corda.v5.ledger.notary.plugin.api;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.application.flows.SubFlow;

import java.util.List;

/**
 * A basic interface that needs to be implemented by the client-side logic of the notary plugin.
 * This interface has a single method called {@link #call} which contains the main logic for the client.
 * The client flow must implement this interface in order for it to be instantiated in the provider.
 * If the client flow doesn't implement this interface the plugin selection will not work. Implementations
 * must:
 * <ul>
 *     <li>
 *         Specify the {@link net.corda.v5.application.flows.InitiatingFlow InitiatingFlow} annotation.
 *     </li>
 *     <li>
 *         Provide a constructor which takes exactly two arguments: a
 *        {@link net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction UtxoSignedTransaction}
 *        representing the transaction, and a {@link net.corda.v5.ledger.common.Party Party} which is
 *        populated with the notary virtual node representing the notary service.
 *     </li>
 *     <li>
 *         The {@link #call} method must specify the {@link net.corda.v5.base.annotations.Suspendable Suspendable}
 *         annotation.
 *     </li>
 * </ul>
 * <p>
 * The server side will not have an interface like the client, it will only implement the
 * {@link net.corda.v5.application.flows.ResponderFlow ResponderFlow} interface.
 * <p>
 * For an example client and server implementation please refer to the non-validating notary plugin under
 * the `notary-plugins/notary-plugin-non-validating` module in the `corda-runtime-os` repository,
 * or the quick start guide.
 */
public interface PluggableNotaryClientFlow extends SubFlow<List<DigitalSignatureAndMetadata>> {
}
