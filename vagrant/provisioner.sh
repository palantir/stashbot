#!/bin/bash
yum -y install epel-release
yum -y install java-1.8.0-openjdk-headless java-1.8.0-openjdk-devel

pushd /etc/yum.repos.d/
wget http://sdkrepo.atlassian.com/atlassian-sdk-stable.repo
yum clean all
yum updateinfo metadata
yum -y install atlassian-plugin-sdk
popd
