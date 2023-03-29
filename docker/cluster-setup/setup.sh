#bin/bash

if [ ! -f .env ]; then
  cp -v .env.example .env
fi

echo "Setup!"