# Jepsen.Redislike

A [Jepsen](https://github.com/jepsen-io/jepsen) test for Redis and Redislike (KeyDB) databases using the [Elle consistency checker](https://github.com/jepsen-io/elle).

You can read [our blogpost about this project](blog/blog.md) here.

## Running the test suite
Our tests rely on the [Jepsen framework](https://github.com/jepsen-io/jepsen) to run. We ran it on the Docker setup by running:

Now you have a few options: 
- Checkout this repository inside your Jepsen directory (if you are fan of nested git repos).
- Checkout this repository inside the Jepsen control node (this is not a persistent volume and offers a bad developer experience).

Or slightly tweak `docker/docker-compose.dev.yml` and add the following line:

```
services:
  control:
    volumes:
      - ${JEPSEN_ROOT}:/jepsen # Mounts $JEPSEN_ROOT on host to /jepsen control container
      # Add this line:
      - /path/to/this/repo:/jepsen/redis-cluster # Mounts this repo on host to /jepsen/redis-cluster control container
```
Then, in the
In the Jepsen project:

```sh
cd docker
./bin/up --dev -n 9 # or how many you want.
```

Then in another terminal:

```sh
cd docker
./bin/console
```

Once inside the control node:

```sh
cd redis-cluster # or another path to test suite.
lein run test -c 9 # there are many other options, see core.clj
```

If on the first try it gives some weird errors, just try again. Sometimes the synchronize timeout runs out, and everything breaks. Also, the first run will be slow, as everything has to be installed and built. But once you have it working, further iterations will be faster.

## Included scripts

We provide a few scripts. One of them will rename test runs to make it easier to find failed runs. You can run it with:
```sh
node scripts/rename-failures.js # might need sudo
```

We also include a custom history parser to calculate lost writes. To run it first copy the folders in `store/redislike/` to `scripts/python_history_parser/store/`. Than you can the run script to analyze the copyed historys with:
```sh
cd scripts/python_history_parser
parse.py
```

## Issues

If you run into the issue `Could not verify 'ssh-ed25519' host key with`, there is a `scripts/fix_ssh.sh` script that will solve this. You can find more information in the [FAQ section of Jepsen](https://github.com/jepsen-io/jepsen#faq).

We have also run into a bug where re-building the containers in WSL was a bit bugged. you can add `docker compose build --no-cache` and `docker compose up --force-recreate` flags, this might help.
## Disclaimer

This project was made for the [Spring 2023 CS4405 course](https://cs4405.github.io/) (Analysis of Concurrent and Distributed Programs) of the [Delft University of Technology](https://tudelft.nl/).

The setup of this project took great inspiration and code samples from the [Redis Raft test](https://github.com/jepsen-io/redis) setup, we've tried to link most of them to the original source code for clarity.
