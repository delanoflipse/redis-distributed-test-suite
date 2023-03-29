FROM eqalpha/keydb:x86_64_v6.3.2

RUN apt-get -qy update && \
  apt-get install -qy host

ADD create-cluster.sh create-cluster.sh
ADD keydb.conf keydb.conf

CMD ["keydb-server", "keydb.conf"]
