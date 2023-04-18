# Jepsen.Redislike

A [jepsen](https://github.com/jepsen-io/jepsen) test for redis and redislike (KeyDB) databases using the [Elle consistency checker](https://github.com/jepsen-io/elle).

You can read [our blogpost about this project](blog/blog.md) here.

## Running the test suite
- TODO: how to setup jepsen.

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
### Issues

If you run into the issue `Could not verify 'ssh-ed25519' host key with`, there is a `fix_ssh.sh` script that will solve this. You can find more information in the [FAQ section of Jepsen](https://github.com/jepsen-io/jepsen#faq).

### Disclaimer

This project was made for the [Spring 2023 CS4405 course](https://cs4405.github.io/) (Analysis of Concurrent and Distributed Programs) of the [Delft University of Technology](https://tudelft.nl/).

The setup of this project took great inspiration and code samples from the [Redis Raft test](https://github.com/jepsen-io/redis) setup.