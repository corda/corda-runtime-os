context=docker-desktop
namespace=mathernamespace



groupPolicyFile=GroupPolicy1
cpi=demo1
identity1=("C=GB, L=London, O=Alice" "C=GB, L=London, O=Simon" "C=GB, L=London, O=Notary")

GROUP1_NOTARY_SERVICE_NAME="C=GB, L=London, O=Notary Service"

echo "Context="$context

cd corda-cli-plugin-host
git fetch
git switch release/5.1
git pull

cd ../corda-dev-prereqs
git pull



cd ../corda-runtime-os
git checkout interop/CORE-13161
git pull
cd ..

SECONDS=0

#kill $(ls -i tcp:8888 | cut -d" " -f2 | tr '\n' ' ')
echo "killing the process running on port 8888"
powershell -File kill-port-process.ps1 8888
sleep 2
kubectl config use-context $context
kubectl delete ns $namespace
sleep 20
kubectl create namespace $namespace
sleep 20
kubectl config set-context --current --namespace=$namespace
sleep 5
echo "Installing prereqs..."
cd corda-dev-prereqs
sleep 1
helm install prereqs -n $namespace charts/corda-dev-prereqs --timeout 10m --wait
sleep 5
echo "Installing Corda..."
cd ../corda-runtime-os

echo "run ./gradlew publishOSGiImage ..."
#./gradlew publishOSGiImage -PbaseImage=docker-remotes.software.r3.com/azul/zulu-openjdk

# other gradle options 
#./gradlew :applications:workers:release:flow-worker:publishOSGiImage -PbaseImage=docker-remotes.software.r3.com/azul/zulu-openjdk
# ./gradlew :tools:plugins:plugins-rest:jar :applications:workers:release:crypto-worker:publishOSGiImage :applications:workers:release:db-worker:publishOSGiImage \
#  :applications:workers:release:flow-worker:publishOSGiImage :applications:workers:release:member-worker:publishOSGiImage :applications:workers:release:p2p-gateway-worker:publishOSGiImage \
#  :applications:workers:release:p2p-link-manager-worker:publishOSGiImage :applications:workers:release:rest-worker:publishOSGiImage -PbaseImage=docker-remotes.software.r3.com/azul/zulu-openjdk
sleep 5
for VARIABLE in corda-os-rest-worker corda-os-p2p-link-manager-worker corda-os-p2p-gateway-worker \
 corda-os-member-worker corda-os-flow-worker corda-os-db-worker corda-os-crypto-worker corda-os-combined-worker corda-os-app-simulator corda-os-plugins
do 
 docker tag "corda-os-docker-dev.software.r3.com/"$VARIABLE":latest-local-5.1.0-INTEROP" "corda-os-docker-dev.software.r3.com/"$VARIABLE":latest-local"
 sleep 2
done
rm charts/corda/Chart.lock
sleep 5

echo "build charts"
helm dependency build charts/corda
sleep 5
echo "install charts on namespace"
helm install corda -n $namespace charts/corda --values values-prereqs.yaml --wait
sleep 5
cd ..
echo "get user secret"
kubectl get secret corda-rest-api-admin -o go-template='{{ .data.username | base64decode }}'
echo "get password secret"
kubectl get secret corda-rest-api-admin -o go-template='{{ .data.password | base64decode }}'
sleep 80
echo "about to open port"
kubectl port-forward --namespace $namespace deployment/corda-rest-worker 8888 &
echo "port should now be open"
echo "Installing cpi for group "$cpi
rm -r register-member
mkdir -p register-member
cd corda-runtime-os
echo "starting ./gradlew :tools:plugins:mgm:build -x test"
./gradlew tools:plugins:package:assemble tools:plugins:mgm:assemble			# was     ./gradlew :tools:plugins:mgm:build -x test
cd ../corda-cli-plugin-host
echo "starting ./gradlew build -x test"
./build/generatedScripts/corda-cli.sh mgm groupPolicy "--name=${identity1[@]:0:1}" "--name=${identity1[@]:1:1}" "--name=${identity1[@]:2:1}" --endpoint-protocol=1 --endpoint="http://localhost:1080" > "../register-member/$groupPolicyFile.json"    # no more  ./gradlew build -x test



#cp ../corda-runtime-os/tools/plugins/mgm/build/libs/mgm-5.1.0-INTEROP.0-SNAPSHOT.jar ./build/plugins
#cd ../corda-runtime-os && ./gradlew :tools:plugins:package:build -x test :tools:plugins:mgm:build -x test
#cd ../corda-cli-plugin-host
#cp ../corda-runtime-os/tools/plugins/package/build/libs/package-cli-plugin-*.jar ../corda-runtime-os/tools/plugins/mgm/build/libs/mgm-cli*.jar ./build/plugins/


#Step Group Policy for Demo1
#echo "./build/generatedScripts/corda-cli.sh mgm groupPolicy --name="
#For the following, when running in GitBash "$identity1" works with $Identity in quotes
#./build/generatedScripts/corda-cli.sh mgm groupPolicy --name="$identity1" --endpoint-protocol=1 --endpoint="http://localhost:1080" > ../register-member/$groupPolicyFile.json
cd ..
#Onboard Identities of Demo1 membershipGroup




cd corda-runtime-os
./gradlew testing:cpbs:interop-token-demo-workflows:cpb -x test
./gradlew notary-plugins:notary-plugin-non-validating:notary-plugin-non-validating-server:cpb -x test

cd ../register-member
cp ../corda-runtime-os/testing/cpbs/interop-token-demo-workflows/build/libs/interop-token-demo-workflows-5.1.0-INTEROP.0-SNAPSHOT-package.cpb ./
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
    --cpb interop-token-demo-workflows-5.1.0-INTEROP.0-SNAPSHOT-package.cpb \
    --group-policy $groupPolicyFile.json \
    --cpi-name $cpi \
    --cpi-version "1.0.0.0-SNAPSHOT" \
    --file $cpi.cpi \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1"
    
sleep 8
CPI_ID=$(curl --insecure -u admin:admin -F upload=@./$cpi.cpi https://localhost:8888/api/v1/cpi/| jq -r '.id')     #not macOS script has   "upload=@./$cpi.cpi"
echo CPI_ID=$CPI_ID
echo "CPI_ID FOR "$cpi=$CPI_ID
sleep 5
CPI_CHECKSUM=$(curl --insecure -u admin:admin https://localhost:8888/api/v1/cpi/status/$CPI_ID | jq -r '.cpiFileChecksum')       # note macOS script has "https://localhost:8888/api/v1/cpi/status/$CPI_ID"
echo CPI_CHECKSUM=$CPI_CHECKSUM
echo "Installing Identities of group "$cpi
#For the following, when running in GitBash "$identity1" works with $Identity in quotes
#for VARIABLE in "$identity1"  

for VARIABLE in "${identity1[@]:0:1}"                                                                                                            
do
 REQUEST_ID=$(curl --insecure -u admin:admin -d '{ "request": { "cpiFileChecksum": "'$CPI_CHECKSUM'", "x500Name": '\""$VARIABLE"\"'  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.requestId' )   # macOS has  REQUEST_BODY=$(printf '{ "request": { "cpiFileChecksum": "%s", "x500Name": "%s" }}' "$CPI_CHECKSUM" "$VARIABLE")
 echo REQUEST_ID=$REQUEST_ID
 sleep 5
 SHORT=$(curl --insecure -u admin:admin -X 'GET' 'https://localhost:8888/api/v1/virtualnode/status/'$REQUEST_ID'' -H 'accept: application/json' | jq -r '.resourceId')
 echo $VARIABLE=$SHORT
 curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "context": { "corda.key.scheme": "CORDA.ECDSA.SECP256R1" } } }' https://localhost:8888/api/v1/membership/$SHORT
 curl --insecure -u admin:admin -X GET https://localhost:8888/api/v1/members/$SHORT

	ALICE_HASH=$SHORT
	echo "\n"ALICE_HASH=$ALICE_HASH
done


for VARIABLE in "${identity1[@]:1:1}"                                                                                                            
do
 REQUEST_ID=$(curl --insecure -u admin:admin -d '{ "request": { "cpiFileChecksum": "'$CPI_CHECKSUM'", "x500Name": '\""$VARIABLE"\"'  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.requestId' )   # macOS has  REQUEST_BODY=$(printf '{ "request": { "cpiFileChecksum": "%s", "x500Name": "%s" }}' "$CPI_CHECKSUM" "$VARIABLE")
 echo REQUEST_ID=$REQUEST_ID
 sleep 5
 SHORT=$(curl --insecure -u admin:admin -X 'GET' 'https://localhost:8888/api/v1/virtualnode/status/'$REQUEST_ID'' -H 'accept: application/json' | jq -r '.resourceId')
 echo $VARIABLE=$SHORT
 curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "context": { "corda.key.scheme": "CORDA.ECDSA.SECP256R1" } } }' https://localhost:8888/api/v1/membership/$SHORT
 curl --insecure -u admin:admin -X GET https://localhost:8888/api/v1/members/$SHORT

	echo "I am over here"
 	SIMON_HASH=$SHORT
	echo "\n"SIMON_HASH=$SIMON_HASH
done



VARIABLE="${identity1[@]:2:1}"
echo "Installing notary="$VARIABLE", service name=$GROUP1_NOTARY_SERVICE_NAME"
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
REQUEST_ID=$(curl --insecure -u admin:admin -d '{ "request": { "cpiFileChecksum": "'$NOTARY_CPI_CHECKSUM'", "x500Name": '\""$VARIABLE"\"'  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.requestId' )
echo "REQUEST_ID=$REQUEST_ID"
printf "\n"
sleep 20
SHORT=$(curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/virtualnode/status/$REQUEST_ID" -H 'accept: application/json' | jq -r .resourceId)
echo "$VARIABLE=$SHORT"
printf "\n"
curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "context": { "corda.key.scheme": "CORDA.ECDSA.SECP256R1", "corda.roles.0" : "notary", "corda.notary.service.name" : "'"$GROUP1_NOTARY_SERVICE_NAME"'", "corda.notary.service.flow.protocol.name" : "com.r3.corda.notary.plugin.nonvalidating", "corda.notary.service.flow.protocol.version.0": "1" } } }' "https://localhost:8888/api/v1/membership/$SHORT"
curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/members/$SHORT"



groupPolicyFile2=GroupPolicy2
cpi=demo2
identity3="C=GB, L=London, O=Bob"
echo "Installing cpi for group "$cpi
cd ../corda-cli-plugin-host
#For the following, when running in GitBash "$identity1" works with $Identity in quotes
echo "create group policy file"
./build/generatedScripts/corda-cli.sh mgm groupPolicy --name="$identity3" --endpoint-protocol=1 --endpoint="http://localhost:1080" > ../register-member/$groupPolicyFile2.json
echo "changing into corda-runtime-os directory"
cd ../corda-runtime-os
echo "do gradle responer test"
./gradlew testing:cpbs:test-responder-cordapp:cpb -x test
echo "changing into register-member directory"
cd ../register-member
echo "copying cpb file into register-member directory"
cp ../corda-runtime-os/testing/cpbs/interop-token-demo-workflows/build/libs/interop-token-demo-workflows-5.1.0-INTEROP.0-SNAPSHOT-package.cpb ./

echo "does group policy file exist..."
echo $groupPolicyFile2
#cd ../register-member
echo "creating cpi ......."
../corda-cli-plugin-host/build/generatedScripts/corda-cli.sh package create-cpi \
    --cpb interop-token-demo-workflows-5.1.0-INTEROP.0-SNAPSHOT-package.cpb \
    --group-policy "$groupPolicyFile2.json" \
    --cpi-name "$cpi" \
    --cpi-version "1.0.0.0-SNAPSHOT" \
    --file "$cpi.cpi" \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1"
sleep 20
CPI_ID=$(curl --insecure -u admin:admin -F upload=@./$cpi.cpi https://localhost:8888/api/v1/cpi/| jq -r '.id')
echo CPI_ID=$CPI_ID
echo "CPI_ID FOR "$cpi=$CPI_ID
sleep 5
CPI_CHECKSUM=$(curl --insecure -u admin:admin https://localhost:8888/api/v1/cpi/status/$CPI_ID | jq -r '.cpiFileChecksum')
echo CPI_CHECKSUM=$CPI_CHECKSUM
echo "Installing Identities of group "$cpi
for VARIABLE in "$identity3"
do
 #When running in GitBash white space in $VARIABLE needs to be handled with '\""$VARIABLE"\"'  instead of "'$VARIABLE'"
 REQUEST_ID=$(curl --insecure -u admin:admin -d '{ "request": { "cpiFileChecksum": "'$CPI_CHECKSUM'", "x500Name": '\""$VARIABLE"\"'  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.requestId' )
 echo REQUEST_ID=$REQUEST_ID
 sleep 5
 SHORT=$(curl --insecure -u admin:admin -X 'GET' 'https://localhost:8888/api/v1/virtualnode/status/'$REQUEST_ID'' -H 'accept: application/json' | jq -r '.resourceId')
 echo $VARIABLE=$SHORT
 curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "context": { "corda.key.scheme": "CORDA.ECDSA.SECP256R1" } } }' https://localhost:8888/api/v1/membership/$SHORT
 curl --insecure -u admin:admin -X GET https://localhost:8888/api/v1/members/$SHORT
done

BOB_HASH="$SHORT"
printf "\nBOB_HASH=%s\n" "$BOB_HASH"




duration=$SECONDS
echo "\n$(($duration / 60)) minutes and $(($duration % 60)) seconds elapsed."
echo "Running interop issue flow as ALICE ..."

clientRequestId="$RANDOM"
curl --insecure -u admin:admin -X POST \
  "https://localhost:8888/api/v1/flow/$ALICE_HASH" \
  -H 'accept: application/json' -H 'Content-Type: application/json' \
  -d '{"clientRequestId": "'$clientRequestId'",
  "flowClassName": "com.r3.corda.demo.interop.tokens.workflows.IssueFlow",
  "requestBody": {
  "amount" : 100
}}}'
printf "\n"
sleep 15
curl_result=$(curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/flow/$ALICE_HASH/$clientRequestId" -H 'accept: application/json')
echo $curl_result
printf "\n"



STATE_ID=$(echo $curl_result | jq -r .flowResult | jq .stateId | tr -d '"')
clientRequestId="$RANDOM"
curl --insecure -u admin:admin -X POST \
  "https://localhost:8888/api/v1/flow/$ALICE_HASH" \
  -H 'accept: application/json' -H 'Content-Type: application/json' \
  -d '{"clientRequestId": "'$clientRequestId'",
  "flowClassName": "com.r3.corda.demo.interop.tokens.workflows.interop.SimpleReserveTokensFlowV2",
  "requestBody": {
  "interopGroupId" : "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08",
  "facadeId" : "org.corda.interop/platform/tokens/v2.0",
  "payload" : "'"$STATE_ID"'",
  "alias" : "C=GB, L=London, O=Bob Alias"
}}}'
printf "\n"
sleep 10
curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/flow/$ALICE_HASH/$clientRequestId" -H 'accept: application/json'
printf "\n"

cd ..


