Session certificates can be optionally used when sending messages using the p2p layer.
To use session certificates some additional steps must be followed when onboarding an MGM or member into a dynamic network.
Throughout this document we assume the environment variable HOLDING_ID is set to the holding identity short hash of the virtual node of either the MGM or member.

## Generate a Certificate Signing Request (CSR) for the Session Certificate

This step must be done after creating the session key pair and before build registration context and configure virtual node as network participant
```bash
curl --fail-with-body -s -S -k -u admin:admin  -X POST -H "Content-Type: application/json" -d '{"x500Name": "'$X500_NAME'"}' $API_URL"/certificates/"$HOLDING_ID/$SESSION_KEY_ID > $WORK_DIR/request.csr
```
Where X500_NAME is set to the x500Name of the MGM or member. Similarly to the TLS certificate, the CSR can be processed, to issue a certificate, by either a real CA of your choice or the fake CA dev tool.
To use the fake CA Tool:

```bash
cd $RUNTIME_OS
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca-5.0.0.0-SNAPSHOT.jar -m /tmp/ca csr $WORK_DIR/request.csr
cd $WORK_DIR
````
This should output the location of the signed certificate. For example, `Wrote certificate to /tmp/ca/request/certificate.pem`

At this point, you should now have a certificate based on the CSR exported from Corda issued either by a real CA or by the fake CA tool. You need to upload the certificate chain to the Corda cluster. You can optionally omit the root certificate. To upload the certificate chain, run:
```bash
curl -k -u admin:admin -X PUT  -F certificate=@/tmp/ca/request/certificate.pem -F alias=session-certificate $API_URL/certificates/vnode/$HOLDING_ID/p2p-session
````

Note: If you upload a certificate chain consisting of more than one certificates, you need to ensure that `-----END CERTIFICATE-----` and `-----BEGIN CERTIFICATE-----` from the next certificate are separated by a new line and no empty spaces in between.

### Optional: Disable revocation checks
If the used CA has not been configured with revocation (e.g. via CRL or OCSP), you can disable revocation checks. By default, revocation checks are enabled. Note that the fake CA dev tool does not support revocation, so if you are using that you will need to disable revocation checks. First we need to get the current link manager configuration version.
```
curl --insecure -u admin:admin -X GET $API_URL/config/corda.p2p.linkManager
```
Stores the version number (in the `version` field) from the response
```
export CONFIG_VERSION=<configuration version>
```

Using that config version, send the following request, which disables revocation checks for the link manager worker.
```
curl -k -u admin:admin -X PUT -d '{"section":"corda.p2p.linkManager", "version":"'$CONFIG_VERSION'", "config": { "revocationCheck": { "mode": "OFF" } }, "schemaVersion": {"major": 1, "minor": 0}}' $API_URL"/config"
```
## Build registration context (only for MGM registration)

You must add an extra json field `corda.group.truststore.session.0` with the trust store of the CA to the registration context (in the same way as `corda.group.truststore.tls.0`).
To enable session certificates you must set the json field `corda.group.pki.session` to be "Standard" instead of "NoPKI".

## Configure virtual node as network participant

You should add the extra json field (sessionCertificateChainAlias) to the network setup RPC request e.g.:
 
```bash
curl -k -u admin:admin -X PUT -d '{"p2pTlsCertificateChainAlias": "p2p-tls-cert", "useClusterLevelTlsCertificateAndKey": true, "sessionKeyId": "'$SESSION_KEY_ID'", "sessionCertificateChainAlias": "session-certificate"}' $API_URL/network/setup/$HOLDING_ID
```
