# How to build

Stashbot occasioanlly, during development, needs to depends upon a custom
version of the jenkins-client lib.  These directions explain how to do this.
You can tell if it is currently happening by examining the pom.xml file and
looking to see if the version number includes a sha1 hash.  Once any changes are
merged into the jenkins-client project, the hash will be removed and the version
updated to use a stock version.

It is also worth noting that, for complex reasons, we must depend upon a
specific "classifier" of the jenkins-client lib, the one called "stash", because
it has guava "shaded" (jarjar'd) into it.  This is because the jenkins-client
lib needs a newer version of guava than stash currently uses.

See references below for more resources explaining what happened and why.

Suffice it to say, further development may require building a custom jar of
jenkins-client and then depending upon it.  Below is the "correct way" I will
accept in pull requests.  Your pull request should update the info below with
the correct sha1 hashes and/or versions as well.

    git clone https://github.com/terabyte/jenkins-client.git
    cd jenkins-client && git checkout dff62680e778a73f13d6e9a2419300111b165c88
    mvn clean test compile
    mvn source:jar
    # deploy special stash version with jarjar
    mvn install:install-file -DgroupId=com.offbytwo.jenkins -DartifactId=jenkins-client -Dclassifier=stash -Dpackaging=jar -Dversion=0.3.3-`git log -1 --format="%H"` -DpomFile=pom.xml -Dsources=target/jenkins-client-0.3.3-SNAPSHOT-sources.jar -Dfile=target/jenkins-client-0.3.3-SNAPSHOT-stash.jar -DlocalRepositoryPath=/home/cmyers/projects/oss/stashbot/jenkins-client
    mvn install:install-file -DgroupId=com.offbytwo.jenkins -DartifactId=jenkins-client -Dpackaging=jar -Dversion=0.3.3-`git log -1 --format="%H"` -DpomFile=pom.xml -Dsources=target/jenkins-client-0.3.3-SNAPSHOT-sources.jar -Dfile=target/jenkins-client-0.3.3-SNAPSHOT.jar -DlocalRepositoryPath=/home/cmyers/projects/oss/stashbot/jenkins-client

Then edit the pom.xml to point at the new jar.

The exact sha1 used to generate the current libs is: dff62680e778a73f13d6e9a2419300111b165c88
The upstream repo for jenkins-client is: https://github.com/RisingOak/jenkins-client.git
The canonical sha1 from the upstream repo is: 078cebd3484e2e573d4e06f900ed27a4de3f3b0f (which had my 4-5 commits added to it)

# References

* https://github.com/RisingOak/jenkins-client/issues/75 
* http://stackoverflow.com/questions/3765903/how-to-include-local-jar-files-in-maven-project

    vi: ft=markdown
