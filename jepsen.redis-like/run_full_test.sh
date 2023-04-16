lein run test -c 9 --replicas 2 --test-count 3 --rate 100 --faults=kill -d redis
lein run test -c 9 --replicas 2 --test-count 3 --rate 100 --faults=pause  -d redis
lein run test -c 9 --replicas 2 --test-count 3 --rate 100 --faults=kill -d keydb
lein run test -c 9 --replicas 2 --test-count 3 --rate 100 --faults=pause  -d keydb

lein run test -c 9 --replicas 2 --test-count 3 --rate 100 --faults=kill,pause -d redis
lein run test -c 9 --replicas 2 --test-count 3 --rate 100 --faults=kill,pause  -d keydb

lein run test -c 9 --replicas 2 --test-count 3 --rate 100 -d redis
lein run test -c 9 --replicas 2 --test-count 3 --rate 100 -d keydb