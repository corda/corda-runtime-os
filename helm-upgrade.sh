helm uninstall corda -n corda

./gradlew publishOSGiImage --parallel

helm upgrade --install corda -n corda \
  charts/corda \
  --values dev.yaml \
  --wait