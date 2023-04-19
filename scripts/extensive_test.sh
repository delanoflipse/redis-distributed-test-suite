# Runs an extensive test for :kill, :pause and :all faults.
# Just to give an idea how tests are run.

lein run test -c 9 --replicas 2 --test-count 3 --faults=kill -d redis
lein run test -c 9 --replicas 2 --test-count 3 --faults=pause  -d redis
lein run test -c 9 --replicas 2 --test-count 3 --faults=kill -d keydb
lein run test -c 9 --replicas 2 --test-count 3 --faults=pause  -d keydb

lein run test -c 9 --replicas 2 --test-count 3 --faults=kill,pause -d redis
lein run test -c 9 --replicas 2 --test-count 3 --faults=kill,pause  -d keydb

lein run test -c 9 --replicas 2 --test-count 3 -d redis
lein run test -c 9 --replicas 2 --test-count 3 -d keydb