FROM alpine:3.22

RUN apk add --no-cache openssh-client

COPY entrypoint.sh /usr/local/bin/loadtest-ssh-tunnel
RUN chmod 755 /usr/local/bin/loadtest-ssh-tunnel

ENTRYPOINT ["loadtest-ssh-tunnel"]
