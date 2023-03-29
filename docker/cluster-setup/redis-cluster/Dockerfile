FROM redis:7.0.10

ADD create-cluster.sh create-cluster.sh
ADD redis.conf redis.conf

CMD ["redis-server", "redis.conf"]
