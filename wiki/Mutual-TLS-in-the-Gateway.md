## What is Mutual TLS in Corda 5
Corda is using TLS to secure a connection between two clusters. While establishing a TLS connection between two gateways, the server gateway will send its certificate to the client gateway. The client gateway will verify the server certificate using its trust root certificate. In mutual TLS, in addition to that, the server gateway will also ask the client gateway to send a client certificate and verify it using its trust root certificate.

Since the gateway manages the TLS connections for an entire cluster, the TLS mode (i.e. mutual or one-way) will be managed by the gateway configuration and will apply to the cluster. That means any group hosted in a mutual TLS cluster has to be a mutual TLS group, and all its members must be hosted on a mutual TLS cluster (see limitation below).

The server gateway will have a set of accepted certificate subjects. The server will reject a connection with a certificate with a subject not in the allowed list as part of the client certificate verification.

Mutual TLS is relevant only for dynamic networks. Static networks do not have TLS connections and are relevant only for a single cluster.

Mutual TLS will only work with a Corda-5 network.

## How to onboard an MGM and a member with mutual TLS
For more details, see [MGM-Onboarding](MGM-Onboarding) and [Member-Onboarding](Member-Onboarding-(Dynamic-Networks)).

To configure the cluster to use mutual TLS, set the `sslConfig.tlsType` flag in the `corda.p2p.gateway` configuration to `MUTUAL`. For example:
```bash
curl -k -u admin:admin -X PUT -d '{"section":"corda.p2p.gateway", "version":"'$CONFIG_VERSION'", "config":"{ "sslConfig": { "tlsType": "MUTUAL" , "revocationCheck": {"mode" : "OFF"} } }", "schemaVersion": {"major": 1, "minor": 0}}' $API_URL"/config"
```

To register an MGM to a mutual TLS cluster, the TLS type must be explicitly set in the registration context. That is, the `corda.group.tls.type` files must be `Mutual`. If the field is not set, it will default to one-way TLS. For example:
```
export REGISTRATION_CONTEXT='{
  "corda.session.key.id": "'$SESSION_KEY_ID'",
  "corda.ecdh.key.id": "'$ECDH_KEY_ID'",
  "corda.group.protocol.registration": "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
  "corda.group.protocol.synchronisation": "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
  "corda.group.protocol.p2p.mode": "Authenticated_Encryption",
  "corda.group.key.session.policy": "Combined",
  "corda.group.pki.session": "NoPKI",
  "corda.group.pki.tls": "Standard",
  "corda.group.tls.type": "Mutual",
  "corda.group.tls.version": "1.3",
  "corda.endpoints.0.connectionURL": "https://'$P2P_GATEWAY_HOST':'$P2P_GATEWAY_PORT'",
  "corda.endpoints.0.protocolVersion": "1",
  "corda.group.truststore.tls.0" : "'$TLS_CA_CERT'"
}'

```

To add a certificate subject to the MGM allowed list, use something like:
```
curl -k -u admin:admin -X PUT  "$MGM_API_URL/mgm/$MGM_HOLDING_ID/mutual-tls/allowed-client-certificate-subjects/CN=CordaOperator,C=GB,L=London,O=Org"
```
The `allowed-client-certificate-subjects` API also supports a `DELETE` and `GET` to manage the accepted list of certificates by the MGM. For example:
```
curl -k -u admin:admin -X DELETE  "$MGM_API_URL/mgm/$MGM_HOLDING_ID/mutual-tls/allowed-client-certificate-subjects/CN=CordaOperatorTwo,C=GB,L=London,O=Org"
curl -k -u admin:admin -X PUT  "$MGM_API_URL/mgm/$MGM_HOLDING_ID/mutual-tls/allowed-client-certificate-subjects/CN=CordaOperatorThree,C=GB,L=London,O=Org"
curl -k -u admin:admin "$MGM_API_URL/mgm/$MGM_HOLDING_ID/mutual-tls/allowed-client-certificate-subjects"
```

The MGM will accept members with that certificate and distribute the members with their client certificate subject to all the other members in the group to be accepted by the Gateway of their hosted cluster.

## Known limitations of the mutual TLS implementation
* Changing the TLS type after a member or an MGM was onboarded will make any TLS connection with that member unusable. 
* The entire cluster can either be mutual TLS or one-way TLS.
* Any group hosted in a mutual TLS cluster must be mutual TLS, and all its members must be hosted in a mutual TLS cluster.
* Corda uses the same set of server and client certificates.
* The gateway will accept connections with any certificate of any group hosted on the cluster.