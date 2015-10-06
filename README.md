Stashbot Helper is a plugin designed to enable a continuous integration
workflow within stash (similar to gerrit + jenkins).

# INSTALL GUIDE

To work with Jenkins, you MUST install the following jenkins plugins first.

1. [Jenkins GIT plugin](https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin)
2. [Post build task](https://wiki.jenkins-ci.org/display/JENKINS/Post+build+task)
3. [Mailer Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Mailer) (optional, if you want email notifications)
4. In order to have PR verify builds, your jenkins nodes must be able to create a merge commit, meaning your jenkins users should have a ~/.gitconfig file with a user/email set.  The commits are thrown away so the value doesn't matter.

If any of these are missing, things won't work.

# USER GUIDE

Stashbot is designed to enable high quality code workflows, and make doing the
easy things easy and the hard things possible.  Stashbot will create a build in
a jenkins instance for verifying your code, as well as publishing it.
Additionally, stashbot will trigger builds whenever matching pushes or pull
requests are created.

## INITIAL CONFIG

After installing stashbot, you will need to do the following as a system
administrator in stash:

* Create a jenkins server configuration (one for each jenkins server you connect to) by clicking on "Stashbot Jenkins Admin" under the administration page
* You will need to give stashbot a username/password to access jenkins which has permission to create jobs
* There are several other options you can configure here, most are described at the bottom of the page

To enable CI for a given project, follow the directions in the next section.

## REPO CONFIG

For each project you wish to configure using stashbot, you will need to do the
following as an admininstrator of that project:

* Go to the "Settings" page for your repository and click "Stashbot CI Admin"
* Check "Enabled"
* Select the jenkins server configuration which was created above
* Fill in the details (commands to run, branch regex to verify, etc)
* As before, there are additional details/documentation at the bottom of this page

## CONFIGURATION DETAILS

Note that the regular expressions for branches are a regular expression that,
when refs matching it are pushed to, will trigger a build.  Publish builds are
always performed ONLY on the actual ref pushed, not each commit in the history.
This means if you push 3 commits, A depends upon B depends upon C depends upon
the previous ref, commit A is published.  The regex is anchored and compared to
"refs/heads/foo", for example, so you probably want a regular expression like
`refs/heads/(master|develop)` or `.*feature.*`.

The "Regex for branches to verify" works just like the publish regex, except
things that match the verify regex trigger one or more verify builds.  The
exact logic to trigger verify builds is as follows.  For each updated ref that
matches the verify regex and are not a DELETE, a `rev-list` is performed to
list all commits from the previous value to the new value.  If the change is an
add, and there is no previous value, then all commits are listed.  The list is
limited by the max verify chain length, considering newer commits first.  Next,
for each commit in this list, the commit is built UNLESS it already triggered a
publish build, a verify build (from some other ref already processed), or
already exists in another ref which also matches the verify regex.  This
ensures that if you have feature/A and feature/B and A is 200 commits behind B,
and you push a merge between the two, these 200 commits (which theoretically
were already verified) are not verified again.

*NOTE:* When you click "Save" on a repository configuration, the build job in
jenkins is created/updated.  Any manual changes to the job are overwritten by
the defaults.  For this reason, making manual changes to jenkins jobs is not
recommended.  Future plans may include more extension points for jobs.

## NORMAL USAGE

Once stashbot is configured correctly and enabled on a repository, usage is
mostly automatic.  Any ref you push matching the verify or publish regex
configured will trigger builds.  If the build fails for a transient reason, or
is skipped due to maxVerifyChain limits, you can manually trigger a verify by
clicking the "Retrigger" link listed on the Commits tab of any repository which
has stashbot enabled.

Any time a pull request is created whose target branch to be updated matches
the verify regex, the pull request will trigger a special verify build which
first merges the source and target branches, emulating the pull request being
merged, before performing the build.  Since there is no commit listing to
report the build status to, the build status is reported in comments on the
pull request.  The pull request cannot be merged until this build succeeds, or
a user overrides by adding the phrase `"==OVERRIDE=="` to a comment.

If a pull request is updated (including if the target branch is updated)
another verify build is automatically triggered, and the merge is again
disallowed until that build has succeeded.

## STRICT MODE

In strict mode, stashbot requires that every commit in a pull request has at least one successful build before it can be merged.  This lets you enforce a higher level of verification so that git bisects are reliable.  Another example where you might want to do this is if your build acts upon changed pieces only, and can therefore only guarantee consistency if each commit's parent has also been verified.

Strict mode is disabled by default, and when enabled it cannot be overridden with the override flag due to implementation details (but users may still perform the merge locally and directly push it, if they have adequate permissions to do so).

# DEV GUIDE

## Necessary Tools

1. [Atlassian Plugin SDK](https://developer.atlassian.com/display/DOCS/Set+up+the+Atlassian+Plugin+SDK+and+Build+a+Project) (or run `bin/invoke-sdk.sh` on Linux)
2. [Eclipse](http://eclipse.org) (or the java IDE of your choice)
3. [JDK8](http://java.com) Stash has dropped support for JDK7 as of version 3.5 and later, so we should be building with JDK8.
4. git cli should be on your path.

## Eclipse Setup

1. Generate project files by running `atlas-mvn eclipse:eclipse`
2. Load the code formatter settings by going to File -> Import -> Preferences and loading the .epf file in code-style/
3. Finally, again under preferences, filter on "save actions" for the java editor and check the options for "format source code", "format all lines", and "organize imports".

Doing these 4 things will ensure you do not introduce unneccessary whitespace changes.

NOTE: Please ensure you add a LICENSE block to the top of each newly added file.

## Jenkins

To run jenkins for testing, simply obtain a suitable jenkins.war, then do the
following to configure it:

1. Run `java -jar jenkins.war` (or use the scripts in bin/)
2. Navigate to http://localhost:8080 to configure
3. Install the necessary plugins listed above (required!)
4. Ensure you navigate to a repository settings page and click "save", that is what initially creates/updates jobs in jenkins.

## Test Plan

Currently there are no integration tests.  After major changes, the following tests should be performed manually:

* Plugin successfully loads in stash (if fails, did you forget to add a new class to atlassian-plugin.xml?)
* Go to stashbot settings in rep_1, enable stashbot and save, ensure jobs are updated in jenkins
* Clone rep_1, create empty commit, push, ensure build is triggered
* Create new branch, push to branch, create Pull Request, ensure verify build is triggered
* Ensure PR cannot merge until build succeeds
* Ensure PR can merge after build succeeds
* Ensure edits to PR that do not change from/to sha do not trigger a new build
* Repeat the above 4 steps with a PR from forked repo to parent
* Ensure publish builds are triggered properly
* Ensure failing build of each type correctly reports its failed status
* Ensure "Retrigger" links work
* Ensure comments to PRs override and report success/failure

## Custom Jenkins Client

Originally this plugin required a customized version of the jenkins-client
library, but Cosmin, the author of this library, has generously (and expediently)
accepted our patches, so the current version is maven is all that is required.
If you are adding features which require patches to the library, however, you
can do something like this to easily build your own copy and use it:

    git clone https://github.com/RisingOak/jenkins-client.git $REPO_PATH
    # make modifications, build jar using maven
    atlas-mvn install:install-file -Dfile=$REPO_PATH/target/jenkins-client-0.1.5-SNAPSHOT.jar -DgroupId=com.offbytwo.jenkins -DartifactId=jenkins-client -Dversion=0.1.5-SNAPSHOT -Dpackaging=jar -DpomFile=$REPO_PATH/pom.xml

## Dev/Release Workflow

This project uses versions determined by `git describe --dirty='-dirty' --abbrev=12`, and thus versions are of the form 1.2.3-N-gX where N is the number of commits since that tag and X is the exact 12-character prefix of the sha1 the version was built from.

If you build using `./build/invoke-sdk.sh`, the version will be set automatically.  Alternatively, you can set the DOMAIN_VERSION environemnt variable when invoking maven directly to override the version.

This is important because Atlassian plugins use OSGi and their version strings *must* be of the form:
    ^\d+\.\d+\.\d+.*
    
Therefore, for jars that actually work to be produced, the tag must be a number such as "1.0.0".  For that reason, feature branches will start "features/", and be merged into "master", which will occasionally be tagged for releases.

Not every released version will necessarily be put on the Atlassian Marketplace, but every released version should be stable (i.e. pass all unit tests, and be reasonably functional).

# TODO

## KNOWN BUGS

* Calls to user(null) in RepositoryCloneLinksRequest.Builder() may be broken in Stash 3.11+
    * Removed them for SSH but haven't tested if they need to be removed for HTTP URLs also yet...
* JenkinsManager.updateAllJobs() and createMissingJobs() are untested.
* PluginUserManager calls UserAdminService.createUser() instead of UserAdminService.createServiceUser().  Service users can't be put in groups and thus do not use up a license slot.  We should fix this.

## PLANNED FEATURES

* Better Test coverage - especially integration tests
* Error checking - validate hashes sent to build status, etc.
* Switch to using access keys (aka deployment keys) instead of creating a user
* Better handling of ssh keys and jenkins credentials (i.e. support multiple keys with different permissions)

### SSH Key Support

Modern jenkins with modern git plugin doesn't work at all.  Need to add an
authentication mode that will generate SSH keys, add them as deployment keys to
the stash repos, and connect to jenkins and configure jobs to use the newly
created credentials.

Jenkins Bug: https://issues.jenkins-ci.org/browse/JENKINS-26537

Resources:
* Create credentials in jenkins programmatically: https://github.com/opscode-cookbooks/jenkins/blob/master/libraries/credentials.rb
* Ask jenkins-client to add an API for that: https://github.com/RisingOak/jenkins-client/issues/72
* calling API with curl looks like this:  curl -X POST -d 'script=System.out.println("fooz")' http://localhost:8080/scriptText (https://wiki.jenkins-ci.org/display/JENKINS/Jenkins+Script+Console)
* Ask atlassian how to programmatically add deploy keys: https://answers.atlassian.com/questions/307633/how-do-you-access-service-like-defaultsshkeyservice-from-stash-plugin
* Details about making the actual rest call to jenkins: https://groups.google.com/forum/#!topic/jenkinsci-users/sdmsiVwqvyg
* How to generate SSH keys using JSch: http://www.jcraft.com/jsch/examples/KeyGen.java.html
* Since verify_pr does a manual fetch, the job template itself will have to write out the ssh key then set GIT_SSH_COMMAND to a script that does 'ssh -i someKey "$@"'

## POSSIBLE FUTURE FEATURES

* Add authenticator to auth chain to allow dynamic credentials per-repo
* Supposedly jenkins supports groovy scripting.  We could possibly expose more functionality via arbitrary groovy by plugging into this.
* Add support for using Bamboo for CI (or other CI tools)

# LICENSE

Stashbot is released by Palantir Technologies, Inc. under the Apache 2.0
License.  see the included LICENSE file for details.
