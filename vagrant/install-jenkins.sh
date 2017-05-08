#!/bin/bash
# Install jenkins along with a bunch of plugins
if [[ ! -e /var/lib/jenkins ]]; then
  useradd -d /var/lib/jenkins jenkins
  echo "jenkins ALL= NOPASSWD: ALL" >> /etc/sudoers
  echo "Grabbing jenkins.war"
  if [[ ! -e jenkins.war ]]; then
    wget -q https://updates.jenkins-ci.org/latest/jenkins.war
  fi
  mkdir -p /var/lib/jenkins/jobs
  mkdir -p /var/lib/jenkins/plugins
  mkdir -p /var/lib/jenkins/users
  # Copy any premade jobs (if there are any)
  find ${path}jenkins/jobs -maxdepth 1 -mindepth 1 -type d | xargs -I% cp -r "%" /var/lib/jenkins/jobs
  find ${path}jenkins/users -maxdepth 1 -mindepth 1 -type d | xargs -I% cp -r "%" /var/lib/jenkins/users
  # Make sure we have various plugins
  pushd /var/lib/jenkins/plugins
  echo "Grabbing plugins"
  wget -q http://updates.jenkins-ci.org/latest/git.hpi
  wget -q http://updates.jenkins-ci.org/latest/scm-api.hpi
  wget -q http://updates.jenkins-ci.org/latest/credentials.hpi
  wget -q http://updates.jenkins-ci.org/latest/git-client.hpi
  wget -q http://updates.jenkins-ci.org/latest/ssh-credentials.hpi
  wget -q http://updates.jenkins-ci.org/latest/postbuild-task.hpi
  popd
  # Shell configuration for running bash and security configuration
  echo "Copying configuration and starting jenkins"
  cp ${path}jenkins/*.xml /var/lib/jenkins
  chown -R jenkins:jenkins /var/lib/jenkins
  export JENKINS_HOME=/var/lib/jenkins
  nohup java -jar jenkins.war &
fi
if [[ ! $(ps aux | grep jenkins | grep -v grep | grep -v install) ]]; then
  export JENKINS_HOME=/var/lib/jenkins
  nohup java -jar jenkins.war 1> jenkins-server.out 2>&1 &
fi
