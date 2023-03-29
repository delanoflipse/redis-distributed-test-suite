#bin/bash

. .env

docker compose ${COMPOSITON} down
docker compose ${COMPOSITON} build
docker compose ${COMPOSITON} up
