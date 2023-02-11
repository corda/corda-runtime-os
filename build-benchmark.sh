# The purpose of this script it so run a number of build benchmark tests. The results of these tests will be sent
# to Gradle Enterprise so that we can analyse them.
MAX_WORKERS=4
TAG_BENCHMARK="build-benchmark"    # Gradle build scan label prefix - should remain stable
TAG_EXP="${TAG_BENCHMARK}-0001"    # Gradle build scan label suffix - can be incremented in case we want another set of benchmarks, for example to compare a before/after change

TAG_CACHE="cache"
TAG_NO_CACHE="no-cache"
TAG_CLEAN="clean"
TAG_INCREMENTAL="incremental"

TAG_BENCHMARK_ID="${USER}-$(date +%y%m%d_%H%M%S)"


gradle_run() {
  echo "[$1] $2"
 ./gradlew -Dscan.tag.${TAG_BENCHMARK} -Dscan.tag.${TAG_EXP} -Dscan.tag.${TAG_EXP} -Dscan.tag.${TAG_BENCHMARK_ID} -Dscan.tag.$1 --parallel --max-workers=${MAX_WORKERS} $2
}

set -x

./gradlew --stop

echo "Using ${MAX_WORKERS} workers"

echo "Building without cache"
gradle_run "${TAG_EXP}-${TAG_NO_CACHE}-${TAG_CLEAN}" "--no-build-cache clean"
gradle_run "${TAG_EXP}-${TAG_NO_CACHE}-${TAG_CLEAN}" "--no-build-cache publishOSGiImage -PmultiArchSupport=false"
gradle_run "${TAG_EXP}-${TAG_NO_CACHE}-${TAG_INCREMENTAL}" "--no-build-cache publishOSGiImage -PmultiArchSupport=false"

echo "Building with cache"
gradle_run "${TAG_EXP}-${TAG_CACHE}-${TAG_CLEAN}" "--build-cache clean"
gradle_run "${TAG_EXP}-${TAG_CACHE}-${TAG_CLEAN}" "--build-cache publishOSGiImage -PmultiArchSupport=false"
gradle_run "${TAG_EXP}-${TAG_CACHE}-${TAG_INCREMENTAL}" "--build-cache publishOSGiImage -PmultiArchSupport=false"

echo "(Incremental) Detekt without cache"
gradle_run "${TAG_EXP}-${TAG_NO_CACHE}-${TAG_INCREMENTAL}" "--no-build-cache detekt"

echo "(Incremental) Testing without cache"
gradle_run "${TAG_EXP}-${TAG_NO_CACHE}-${TAG_INCREMENTAL}" "--no-build-cache test"

echo "(Incremental) Testing without cache"
gradle_run "${TAG_EXP}-${TAG_NO_CACHE}-${TAG_INCREMENTAL}" "--no-build-cache integrationTest"
