# Running jepsen tests
Setup:

```sh
./bin/up --dev -n 9
```

In the console:
```sh
cd redis-cluster # path to test suite
lein run test -c 9 # there are many other options
```

### scripts
```sh
node scripts/rename-failures.js #might need sudo
```
