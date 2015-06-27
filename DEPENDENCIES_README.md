# How to build

Stashbot currently depends upon a custom version of the jenkins-client lib.

This is because jenkins-client requires a newish version of Google Guava, but
stash requires an ass-old version of guava.  The only way to square this is to
modify jenkins-client to bundle its own copy of the guava libs it needs using
"jarjar".

For now this custom jar is checked into git, but to rebuild them or produce your
own, you can do the following:

    git clone https://github.com/terabyte/jenkins-client.git
    cd jenkins-client && git checkout feature/jarjar
    mvn clean test compile
    mvn source:jar
    mvn install:install-file -DgroupId=com.offbytwo.jenkins -DartifactId=jenkins-client -Dpackaging=jar -Dversion=0.3.1-`git log -1 --format="%H"` -DpomFile=pom.xml -Dsources=target/jenkins-client-0.3.1-SNAPSHOT-sources.jar -Dfile=target/jenkins-client-0.3.1-SNAPSHOT.jar -DlocalRepositoryPath=/home/cmyers/projects/stash/stashbot/jenkins-client


Then edit the pom.xml to point at the new jar.

Sometimes maven flips out and running the source:jar target ruins the actual jar
produced, so make sure the installed jar is over 2MB and not under 400kb (which
is the difference between having guava bundled properly, or not)

The exact sha1 used to generate the current libs is: 4f8ce857b5abf662129e8f95f1815db928cc64ea
The upstream repo for jenkins-client is: https://github.com/RisingOak/jenkins-client.git
The canonical sha1 from the upstream repo is: 032588c7eacdb73a3311d5943e1e363936d63f27

# References

* http://stackoverflow.com/questions/3765903/how-to-include-local-jar-files-in-maven-project

    vi: ft=markdown
