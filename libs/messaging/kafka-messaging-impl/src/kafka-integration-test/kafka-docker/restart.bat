docker-compose -f single-kafka-cluster.yml down --remove-orphans
rmdir /s /q data
docker-compose -f single-kafka-cluster.yml up -d
