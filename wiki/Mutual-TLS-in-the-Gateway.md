## What is Mutual TLS in Corda 5
Corda uses TLS to secure a connection between two clusters. While establishing a TLS connection between two gateways, the server gateway will send its certificate to the client gateway. The client gateway will verify the server certificate using its trust root certificate. In mutual TLS, in addition to that, the server gateway will also ask the client gateway to send a client certificate and verify it’s using its trust root certificate.

Since the gateway manages the TLS connections for an entire cluster, the TLS mode (i.e. mutual or one-way) will be managed by the gateway configuration and will apply to the cluster. That means any group hosted in a mutual TLS cluster has to be a mutual TLS group, and all its members must be hosted on a mutual TLS cluster (see limitation below).

The server gateway will have a set of accepted certificate subjects. The server will reject a connection with a certificate with a subject not in the allowed list as part of the client certificate verification.

Mutual TLS is relevant only for dynamic networks, as static networks can only span a single cluster.

Mutual TLS will only work with a Corda-5 network.

## How to onboard an MGM and a member with mutual TLS
There are a few steps in the [MGM-Onboarding](MGM-Onboarding) and [Member-Onboarding](Member-Onboarding-(Dynamic-Networks)) guide that need to changed in order to onboard an MGM and a member to a mutual TLS cluster.

### Change the cluster configuration
_Note: Mutual TLS is set per cluster. It has to apply to all the groups that the cluster will host and all the clusters that those groups will be hosted on. One can not onboard a member unless the TLS type of the MGM cluster is aligned with the TLS type of the member cluster_

To configure the cluster to use mutual TLS, set the `sslConfig.tlsType` flag in the `corda.p2p.gateway` configuration to `MUTUAL`. For example:
```
curl -k -u admin:admin -X PUT -d '{"section":"corda.p2p.gateway", "version":"'$CONFIG_VERSION'", "config":"{ \"sslConfig\": { \"tlsType\": \"MUTUAL\"  }  }", "schemaVersion": {"major": 1, "minor": 0}}' $API_URL"/config"
```
_Note: This will overwrite the renovation check setting. If you chose to disable revocation checks do:_
```
curl -k -u admin:admin -X PUT -d '{"section":"corda.p2p.gateway", "version":"'$CONFIG_VERSION'", "config":"{ \"sslConfig\": { \"tlsType\": \"MUTUAL\" , \"revocationCheck\": {\"mode\" : \"OFF\"} } }", "schemaVersion": {"major": 1, "minor": 0}}' $API_URL"/config"
```

This must be done in the MGM cluster before registering the MGM and in all the members clusters before uploading the CPI. 

### Set the TLS type in the MGM context to mutual 

To register an MGM in a mutual TLS cluster, the TLS type must be explicitly set in the registration context. That is, the `corda.group.tls.type` field must be `Mutual`. If the field is not set, it will default to one-way TLS. For example:
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
### Add the member TLS certificate subject to the MGM allowed list
To add a certificate subject to the MGM allowed list, use something like:
```
curl -k -u admin:admin -X PUT  "$MGM_API_URL/mgm/$MGM_HOLDING_ID/mutual-tls/allowed-client-certificate-subjects/CN=CordaOperator,C=GB,L=London,O=Org"
```
Where `CN=CordaOperator,C=GB,L=London,O=Org` is the subject of the TLS certificate created during the [member onboarding](Member-Onboarding-(Dynamic-Networks)#set-up-the-tls-key-pair-and-certificate). 

The `allowed-client-certificate-subjects` API also supports a `DELETE` and `GET` to manage the accepted list of certificates by the MGM. For example:
```
curl -k -u admin:admin -X DELETE  "$MGM_API_URL/mgm/$MGM_HOLDING_ID/mutual-tls/allowed-client-certificate-subjects/CN=CordaOperatorTwo,C=GB,L=London,O=Org"
curl -k -u admin:admin -X PUT  "$MGM_API_URL/mgm/$MGM_HOLDING_ID/mutual-tls/allowed-client-certificate-subjects/CN=CordaOperatorThree,C=GB,L=London,O=Org"
curl -k -u admin:admin "$MGM_API_URL/mgm/$MGM_HOLDING_ID/mutual-tls/allowed-client-certificate-subjects"
```

Before a member tries to register with a network that is configured with mutual TLS, the MGM must have added the certificate subject that will be used by that member to the allowed list. Otherwise, the connection will be rejected by the MGM’s gateway and the member will not be able to register.  During registration, the MGM can only accept a member with a certificate that was added to the list. When the member is successfully onboarded, the MGM will then distribute the members certificate subject to all the other members in the group. The gateway in each member cluster will use this to accept TLS connections from any onboarded member.

## Known limitations of the mutual TLS implementation
* Changing the TLS type after a member or an MGM was onboarded will make any TLS connection with that member unusable. 
* The entire cluster's TLS type can be either mutual TLS or one-way TLS.
* All members of the same group must be hosted in a cluster with the same TLS type.
* In the mutual TLS mode, a virtual node can only be set up with a single TLS certificate that will be used as both a client and a server certificate.
* In mutual TLS mode, a gateway will accept a TLS connection that uses a certificate associated with any member of any application network (partially) hosted in this cluster.