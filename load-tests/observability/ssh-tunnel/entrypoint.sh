#!/bin/sh
set -eu

test -n "${LOADTEST_SSH_HOST:-}"
install -m 600 /run/source-key /tmp/ec2-key
install -d -m 700 /root/.ssh

exec ssh \
  -N \
  -o BatchMode=yes \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  -o StrictHostKeyChecking=accept-new \
  -o GatewayPorts=yes \
  -L 0.0.0.0:18080:127.0.0.1:80 \
  -L 0.0.0.0:19090:127.0.0.1:9090 \
  -i /tmp/ec2-key \
  "${LOADTEST_SSH_HOST}"
