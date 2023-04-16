# Running jepsen tests
Setup:

```sh
./bin/up --dev --node-count 6
```

In the console:
```sh
cd redis-cluster # path to test suite
lein run test --nodes n1,n2,n3,n4,n5,n6
```

### scripts
```sh
node scripts/rename-failures.js #might need sudo
```
