lein run test -c 3 --replicas 0 --rate 10 --test-run 2
lein run test -c 6 --replicas 0 --rate 10 --test-run 2
lein run test -c 9 --replicas 0 --rate 10 --test-run 2
lein run test -c 6 --replicas 1 --rate 10 --test-run 2
lein run test -c 9 --replicas 1 --rate 10 --test-run 2
lein run test -c 9 --replicas 2 --rate 10 --test-run 2

lein run test -c 3 --replicas 0 --rate 100 --test-run 2
lein run test -c 6 --replicas 0 --rate 100 --test-run 2
lein run test -c 9 --replicas 0 --rate 100 --test-run 2
lein run test -c 6 --replicas 1 --rate 100 --test-run 2
lein run test -c 9 --replicas 1 --rate 100 --test-run 2
lein run test -c 9 --replicas 2 --rate 100 --test-run 2