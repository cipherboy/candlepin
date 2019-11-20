#!/bin/sh
#
# Sets a system up for a candlepin development environment (minus a db,
# handled separately), and an initial clone of candlepin.

set -ve

source /root/dockerlib.sh

export JAVA_VERSION=1.8.0
export JAVA_HOME=/usr/lib/jvm/java-$JAVA_VERSION

# Install & configure dev environment
yum install -y epel-release

PACKAGES=(
    gcc
    gettext
    git
    hostname
    java-$JAVA_VERSION-openjdk-devel
    jss
    libxml2-python
    liquibase
    mariadb
    mysql-connector-java
    mariadb-java-client
    openssl
    postgresql
    postgresql-jdbc
    python-pip
    qpid-proton-c
    qpid-proton-c-devel
    rpmlint
    rsyslog
    tig
    tmux
    tomcat
    vim-enhanced
    wget
)

yum install -y ${PACKAGES[@]}

# pg_isready is used to check if the postgres server is up
# it is not included in postgresql versions < 9.3.0.
# therefore we must build it
if ! type pg_isready 2> /dev/null; then
  yum install -y centos-release-scl
  yum install -y yum install rh-postgresql96
fi

# Setup for autoconf:
mkdir /etc/candlepin
echo "# AUTOGENERATED" > /etc/candlepin/candlepin.conf

cat > /root/.bashrc <<BASHRC
if [ -f /etc/bashrc ]; then
  . /etc/bashrc
fi

export HOME=/root
export JAVA_HOME=/usr/lib/jvm/java-$JAVA_VERSION
BASHRC

git clone https://github.com/candlepin/candlepin.git /candlepin
cd /candlepin

# Installs all Java deps into the image, big time saver
./gradlew --no-daemon dependencies

cd /
rm -rf /candlepin
cleanup_env
