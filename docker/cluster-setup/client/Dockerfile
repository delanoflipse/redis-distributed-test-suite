FROM ubuntu:18.04

RUN apt-get -qy update && \
  apt-get install -qy redis-server

ADD ./init.sh /init.sh
CMD /init.sh
