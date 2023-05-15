#!/usr/bin/env bash
set -e

context=docker-desktop
namespace=corda
groupPolicyFile=GroupPolicy1
cpi=demo1
identity1=("C=GB, L=London, O=Alice" "C=GB, L=London, O=Simon" "C=GB, L=London, O=Notary")
GROUP1_NOTARY_SERVICE_NAME="C=GB, L=London, O=Notary Service"

SECONDS=0

# Kill any clusters that are already running
echo "Stopping running clusters."
cluster_pids=($(lsof -i tcp:8888 | cut -d" " -f2 | tr '\n' ' '))
if [ -z "${cluster_pids[@]:0:1}" ]; then
    echo "No clusters appear to be running, nothing to stop."
else
    kill ${cluster_pids[@]:0:1}
    echo "Clusters stopped."
fi

cd corda-cli-plugin-host
git fetch
git switch release/5.1
git pull
cd ../corda-dev-helm
git pull
cd ../corda-dev-prereqs
git pull
cd ..

kubectl config use-context "$context"
kubectl delete ns "$namespace" || true
kubectl create namespace "$namespace"
kubectl config set-context --current "--namespace=$namespace"
echo "Installing prereqs..."
cd corda-dev-prereqs
helm install prereqs -n "$namespace" charts/corda-dev-prereqs --timeout 10m --wait
echo "Installing Corda..."
cd ../corda-runtime-os
./gradlew publishOSGiImage -PbaseImage=docker-remotes.software.r3.com/azul/zulu-openjdk
for VARIABLE in corda-os-rest-worker corda-os-p2p-link-manager-worker corda-os-p2p-gateway-worker \
 corda-os-member-worker corda-os-flow-worker corda-os-db-worker corda-os-crypto-worker corda-os-combined-worker corda-os-app-simulator corda-os-plugins
do
 docker tag "corda-os-docker-dev.software.r3.com/"$VARIABLE":latest-local-5.1.0-INTEROP" "corda-os-docker-dev.software.r3.com/"$VARIABLE":latest-local"
done
rm charts/corda/Chart.lock
helm dependency build charts/corda
helm install corda -n "$namespace" charts/corda --values values-prereqs.yaml --wait
cd ..
kubectl get secret corda-rest-api-admin -o go-template='{{ .data.username | base64decode }}'
kubectl get secret corda-rest-api-admin -o go-template='{{ .data.password | base64decode }}'
sleep 10
kubectl port-forward --namespace "$namespace" deployment/corda-rest-worker 8888 &
rm -r register-member
mkdir -p register-member
cd corda-runtime-os
./gradlew :tools:plugins:mgm:build -x test
cd ../corda-cli-plugin-host
./gradlew build -x test
cp ../corda-runtime-os/tools/plugins/mgm/build/libs/mgm-5.1.0-INTEROP.0-SNAPSHOT.jar ./build/plugins
cd ../corda-runtime-os && ./gradlew :tools:plugins:package:build -x test :tools:plugins:mgm:build -x test
cd ../corda-cli-plugin-host
cp ../corda-runtime-os/tools/plugins/package/build/libs/package-cli-plugin-*.jar ../corda-runtime-os/tools/plugins/mgm/build/libs/mgm-cli*.jar ./build/plugins/
echo "Step Group Policy for $cpi and Identity ${identity1[@]}"
./build/generatedScripts/corda-cli.sh mgm groupPolicy "--name=${identity1[@]:0:1}" "--name=${identity1[@]:1:1}" "--name=${identity1[@]:2:1}" --endpoint-protocol=1 --endpoint="http://localhost:1080" > "../register-member/$groupPolicyFile.json"
cd ..
echo "Installing cpi for group $cpi"
cd corda-runtime-os
./gradlew testing:cpbs:interop-delivery-demo-workflows:cpb -x test
./gradlew notary-plugins:notary-plugin-non-validating:notary-plugin-non-validating-server:cpb -x test

cd ../register-member
cp ../corda-runtime-os/testing/cpbs/interop-delivery-demo-workflows/build/libs/interop-delivery-demo-workflows-5.1.0-INTEROP.0-SNAPSHOT-package.cpb ./
cp ../corda-runtime-os/notary-plugins/notary-plugin-non-validating/notary-plugin-non-validating-server/build/libs/notary-plugin-non-validating-server-5.1.0-INTEROP.0-SNAPSHOT-package.cpb ./

cat > gradle-plugin-default-key.pem << EOF
-----BEGIN CERTIFICATE-----
MIIB7zCCAZOgAwIBAgIEFyV7dzAMBggqhkjOPQQDAgUAMFsxCzAJBgNVBAYTAkdC
MQ8wDQYDVQQHDAZMb25kb24xDjAMBgNVBAoMBUNvcmRhMQswCQYDVQQLDAJSMzEe
MBwGA1UEAwwVQ29yZGEgRGV2IENvZGUgU2lnbmVyMB4XDTIwMDYyNTE4NTI1NFoX
DTMwMDYyMzE4NTI1NFowWzELMAkGA1UEBhMCR0IxDzANBgNVBAcTBkxvbmRvbjEO
MAwGA1UEChMFQ29yZGExCzAJBgNVBAsTAlIzMR4wHAYDVQQDExVDb3JkYSBEZXYg
Q29kZSBTaWduZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQDjSJtzQ+ldDFt
pHiqdSJebOGPZcvZbmC/PIJRsZZUF1bl3PfMqyG3EmAe0CeFAfLzPQtf2qTAnmJj
lGTkkQhxo0MwQTATBgNVHSUEDDAKBggrBgEFBQcDAzALBgNVHQ8EBAMCB4AwHQYD
VR0OBBYEFLMkL2nlYRLvgZZq7GIIqbe4df4pMAwGCCqGSM49BAMCBQADSAAwRQIh
ALB0ipx6EplT1fbUKqgc7rjH+pV1RQ4oKF+TkfjPdxnAAiArBdAI15uI70wf+xlL
zU+Rc5yMtcOY4/moZUq36r0Ilg==
-----END CERTIFICATE-----
EOF

keytool -genkeypair -alias "signing key 1" -keystore signingkeys.pfx -storepass "keystore password" -dname "cn=CPI Plugin Example - Signing Key 1, o=R3, L=London, c=GB" -keyalg RSA -storetype pkcs12 -validity 4000
keytool -importcert -keystore signingkeys.pfx -storepass "keystore password" -noprompt -alias gradle-plugin-default-key -file gradle-plugin-default-key.pem
curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@gradle-plugin-default-key.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
keytool -exportcert -rfc -alias "signing key 1" -keystore signingkeys.pfx -storepass "keystore password" -file signingkey1.pem
curl --insecure -u admin:admin -X PUT -F alias="signingkey1-2022" -F certificate=@signingkey1.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
pwd
../corda-cli-plugin-host/build/generatedScripts/corda-cli.sh package create-cpi \
    --cpb interop-delivery-demo-workflows-5.1.0-INTEROP.0-SNAPSHOT-package.cpb \
    --group-policy "$groupPolicyFile.json" \
    --cpi-name "$cpi" \
    --cpi-version "1.0.0.0-SNAPSHOT" \
    --file "$cpi.cpi" \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1"

sleep 20
CPI_ID=$(curl --insecure -u admin:admin -F "upload=@./$cpi.cpi" https://localhost:8888/api/v1/cpi/ | jq -r .id)
echo "CPI_ID FOR $cpi=$CPI_ID"
printf "\n"
sleep 10
CPI_CHECKSUM=$(curl --insecure -u admin:admin "https://localhost:8888/api/v1/cpi/status/$CPI_ID" | jq -r .cpiFileChecksum)
echo "CPI_CHECKSUM=$CPI_CHECKSUM"
printf "\n"
echo "Installing Identities of group $cpi and nodes ${identity1[@]:0:2}"
printf "\n"
for VARIABLE in "${identity1[@]:0:2}"
do
    echo "Installing identity $VARIABLE"
    printf "\n"
    REQUEST_BODY=$(printf '{ "request": { "cpiFileChecksum": "%s", "x500Name": "%s" }}' "$CPI_CHECKSUM" "$VARIABLE")
    REQUEST_ID=$(curl --insecure -u admin:admin -d "$REQUEST_BODY" https://localhost:8888/api/v1/virtualnode | jq -r '.requestId')
    echo "REQUEST_ID=$REQUEST_ID"
    printf "\n"
    sleep 20
    SHORT=$(curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/virtualnode/status/$REQUEST_ID" -H 'accept: application/json' | jq -r .resourceId)
    echo "$identity1=$SHORT"
    printf "\n"
    curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "context": { "corda.key.scheme": "CORDA.ECDSA.SECP256R1" } } }' "https://localhost:8888/api/v1/membership/$SHORT"
    curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/members/$SHORT"
done
printf "\n"
ALICE_HASH="$SHORT"
printf "\nALICE_HASH=%s\n" "$ALICE_HASH"


VARIABLE="${identity1[@]:2:1}"
echo "Installing notary=$VARIABLE, service name=$GROUP1_NOTARY_SERVICE_NAME"
../corda-cli-plugin-host/build/generatedScripts/corda-cli.sh package create-cpi \
    --cpb notary-plugin-non-validating-server-5.1.0-INTEROP.0-SNAPSHOT-package.cpb \
    --group-policy "$groupPolicyFile.json" \
    --cpi-name "notary$cpi" \
    --cpi-version "1.0.0.0-SNAPSHOT" \
    --file "notary$cpi.cpi" \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1"

NOTARY_CPI_ID=$(curl --insecure -u admin:admin -F "upload=@./notary$cpi.cpi" https://localhost:8888/api/v1/cpi/ | jq -r .id)
sleep 20
echo "CPI_ID FOR notary$cpi=$NOTARY_CPI_ID"
printf "\n"
NOTARY_CPI_CHECKSUM=$(curl --insecure -u admin:admin "https://localhost:8888/api/v1/cpi/status/$NOTARY_CPI_ID" | jq -r .cpiFileChecksum)
echo "NOTARY_CPI_CHECKSUM=$NOTARY_CPI_CHECKSUM"

printf "\n"
REQUEST_BODY=$(printf '{ "request": { "cpiFileChecksum": "%s", "x500Name": "%s" }}' "$NOTARY_CPI_CHECKSUM" "$VARIABLE")
REQUEST_ID=$(curl --insecure -u admin:admin -d "$REQUEST_BODY" https://localhost:8888/api/v1/virtualnode | jq -r '.requestId')
echo "REQUEST_ID=$REQUEST_ID"
printf "\n"
sleep 20
SHORT=$(curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/virtualnode/status/$REQUEST_ID" -H 'accept: application/json' | jq -r .resourceId)
echo "$VARIABLE=$SHORT"
printf "\n"
curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "context": { "corda.key.scheme": "CORDA.ECDSA.SECP256R1", "corda.roles.0" : "notary", "corda.notary.service.name" : "'"$GROUP1_NOTARY_SERVICE_NAME"'", "corda.notary.service.flow.protocol.name" : "com.r3.corda.notary.plugin.nonvalidating", "corda.notary.service.flow.protocol.version.0": "1" } } }' "https://localhost:8888/api/v1/membership/$SHORT"
curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/members/$SHORT"




groupPolicyFile=GroupPolicy2
cpi=demo2
identity1=("C=GB, L=London, O=Bob")
echo "Step Group Policy for $cpi and Identity ${identity1[@]:0:1}"
printf "\n"
cd ../corda-cli-plugin-host
./build/generatedScripts/corda-cli.sh mgm groupPolicy "--name=${identity1[@]:0:1}" --endpoint-protocol=1 --endpoint="http://localhost:1080" > "../register-member/$groupPolicyFile.json"

echo "Installing cpi for group $cpi"
printf "\n"
cd ../corda-runtime-os
./gradlew testing:cpbs:interop-payment-demo-workflows:cpb -x test
cd ../register-member
cp ../corda-runtime-os/testing/cpbs/interop-payment-demo-workflows/build/libs/interop-payment-demo-workflows-5.1.0-INTEROP.0-SNAPSHOT-package.cpb ./
cd ../register-member
../corda-cli-plugin-host/build/generatedScripts/corda-cli.sh package create-cpi \
    --cpb interop-payment-demo-workflows-5.1.0-INTEROP.0-SNAPSHOT-package.cpb \
    --group-policy "$groupPolicyFile.json" \
    --cpi-name "$cpi" \
    --cpi-version "1.0.0.0-SNAPSHOT" \
    --file "$cpi.cpi" \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1"
sleep 20
CPI_ID=$(curl --insecure -u admin:admin -F "upload=@./$cpi.cpi" https://localhost:8888/api/v1/cpi/ | jq -r .id)
echo "CPI_ID=$CPI_ID"
echo "CPI_ID FOR $cpi=$CPI_ID"
sleep 10
CPI_CHECKSUM=$(curl --insecure -u admin:admin "https://localhost:8888/api/v1/cpi/status/$CPI_ID" | jq -r .cpiFileChecksum)
echo "CPI_CHECKSUM=$CPI_CHECKSUM"
echo "Installing Identities of group $cpi"
for VARIABLE in "${identity1[@]}"
do
    echo "Installing identity $VARIABLE"
    REQUEST_BODY=$(printf '{ "request": { "cpiFileChecksum": "%s", "x500Name": "%s" }}' "$CPI_CHECKSUM" "$VARIABLE")
    REQUEST_ID=$(curl --insecure -u admin:admin -d "$REQUEST_BODY" https://localhost:8888/api/v1/virtualnode | jq -r .requestId)
    echo "REQUEST_ID=$REQUEST_ID"
    sleep 20
    SHORT=$(curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/virtualnode/status/$REQUEST_ID" -H 'accept: application/json' | jq -r .resourceId)
    echo "$identity1=$SHORT"
    curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "context": { "corda.key.scheme": "CORDA.ECDSA.SECP256R1" } } }' "https://localhost:8888/api/v1/membership/$SHORT"
    curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/members/$SHORT"
done
BOB_HASH="$SHORT"
printf "\nBOB_HASH=%s\n" "$BOB_HASH"

duration="$SECONDS"
printf "\n$(($duration / 60)) minutes and $(($duration % 60)) seconds elapsed.\n"
printf "Running interop flow ...\n"
clientRequestId="$RANDOM"
curl --insecure -u admin:admin -X POST \
  "https://localhost:8888/api/v1/flow/$ALICE_HASH" \
  -H 'accept: application/json' -H 'Content-Type: application/json' \
  -d '{"clientRequestId": "'$clientRequestId'",
  "flowClassName": "com.r3.corda.demo.interop.delivery.workflows.IssueFlow",
  "requestBody": {
  "amount" : 100
}}}'
printf "\n"
sleep 15
curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/flow/$ALICE_HASH/$clientRequestId" -H 'accept: application/json'
printf "\n"
