#!/bin/bash
# A script to add comments from Jenkins to Stash pull requests.

set -e

name=$(basename $0)

if [[ "x$STASH_HOST" == "x" ]]; then
    echo "You must set STASH_HOST, e.g. 'stash.yourcompany.com'"
    exit 3
fi

if [ "$#" -ne 1 -o "$1" == "-h" -o "$1" == "--help" ]
then
    echo "Usage: $name comment (e.g. $name \"This is a test comment\")"
    exit 2
fi

if [ -z "$pullRequestId" ]
then
    echo "$name: This doesn't appear to be a pull request, aborting"
    exit 0
fi

GIT_URL=$(sed -n 's/.*url *= *\(.*\)/\1/p' < .git/config | head -n 1)
if [[ $GIT_URL =~ https://([^:]+):([^@]+)@$STASH_HOST/scm/([^/]+)/(.*)\.git ]]
then
    username=${BASH_REMATCH[1]}
    password=${BASH_REMATCH[2]}
    projectKey=${BASH_REMATCH[3]}
    repoSlug=${BASH_REMATCH[4]}

    # Escape double quotes and newlines
    text=$(echo "$1" | sed 's/"/\\\"/g' | sed ':a;N;$!ba;s/\n/\\n/g') # From http://unix.stackexchange.com/a/114948
    url="https://$STASH_HOST/rest/api/1.0/projects/$projectKey/repos/$repoSlug/pull-requests/$pullRequestId/comments"

    echo "$name: Creating comment \"$1\" using URL $url"
    curl -s -i --fail "$url" --basic --user "$username:$password" -H "Content-Type: application/json" --data-binary "{\"text\":\"$text\"}"
else
    echo "$name: Unable to match regex in git URL \"$GIT_URL\" (check .git/config)"
    exit 1
fi
