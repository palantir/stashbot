// Copyright 2014 Palantir Technologies
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.palantir.stash.stashbot.urlbuilder;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.bitbucket.nav.NavBuilder;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryCloneLinksRequest;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.util.NamedLink;
import com.atlassian.bitbucket.util.SimpleNamedLink;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration.AuthenticationMode;

public class StashbotUrlBuilderTest {

    private static final String BUILD_HEAD = "0a943a29376f2336b78312d99e65da17048951db";
    private static final String TO_SHA = "beefbeef376f2336b78312d99e65da17048951db";
    private static final String ABS_URL = "http://example.com:1234/";
    private static final String ABS_URL_WITH_CREDS = "http://someuser:somepw@example.com:1234/";
    private static final Long PULL_REQUEST_ID = 1234L;
    private static final Integer REPO_ID = 5678;

    @Mock
    private ConfigurationPersistenceService cps;
    @Mock
    private NavBuilder nb;
    @Mock
    private RepositoryService rs;

    @Mock
    private Repository repo;
    @Mock
    private JenkinsServerConfiguration jsc;
    @Mock
    private RepositoryConfiguration rc;
    @Mock
    private PullRequest pr;
    @Mock
    private PullRequestRef toRef;
    @Mock
    private PullRequestRef fromRef;

    private StashbotUrlBuilder sub;
    private Set<NamedLink> links;

    @Before
    public void setUp() throws SQLException {
        MockitoAnnotations.initMocks(this);
        links = new HashSet<NamedLink>();
        links.add(new SimpleNamedLink(ABS_URL, "http"));

        Mockito.when(cps.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);
        Mockito.when(cps.getJenkinsServerConfiguration(Mockito.anyString())).thenReturn(jsc);
        
        Mockito.when(nb.buildAbsolute()).thenReturn(ABS_URL);
        Mockito.when(jsc.getStashUsername()).thenReturn("someuser");
        Mockito.when(jsc.getStashPassword()).thenReturn("somepw");
        Mockito.when(jsc.getAuthenticationMode()).thenReturn(AuthenticationMode.USERNAME_AND_PASSWORD);
        Mockito.when(pr.getId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(pr.getFromRef()).thenReturn(fromRef);
        Mockito.when(pr.getToRef()).thenReturn(toRef);
        Mockito.when(toRef.getLatestCommit()).thenReturn(TO_SHA);
        Mockito.when(fromRef.getLatestCommit()).thenReturn(BUILD_HEAD);
        Mockito.when(repo.getId()).thenReturn(REPO_ID);
        Mockito.when(rs.getCloneLinks(Mockito.any(RepositoryCloneLinksRequest.class))).thenReturn(links);

        sub = new StashbotUrlBuilder(cps, nb, rs);

    }

    @Test
    public void testJenkinsTriggerUrlVerify() throws Exception {
        String url = sub.getJenkinsTriggerUrl(repo, JobType.VERIFY_COMMIT,
            BUILD_HEAD, pr);
        Assert.assertEquals(ABS_URL
            + "/plugins/servlet/stashbot/build-trigger/" + REPO_ID
            + "/verification/" + BUILD_HEAD + "/" + TO_SHA + "/"
            + PULL_REQUEST_ID.toString(), url);
    }

    @Test
    public void testBuildReportingUrl() {
        String url = sub.buildReportingUrl(repo, JobType.VERIFY_COMMIT, jsc,
            "successful");
        Assert
            .assertEquals(
                ABS_URL
                    + "/plugins/servlet/stashbot/build-reporting/$repoId/verification/successful/$BUILD_NUMBER/$buildHead/$mergeHead/$pullRequestId",
                url);
    }

    @Test
    public void testCloneUrl() {

        String url = sub.buildCloneUrl(repo, jsc);

        Mockito.verify(rs).getCloneLinks(Mockito.any(RepositoryCloneLinksRequest.class));
        Assert.assertEquals(ABS_URL_WITH_CREDS, url);
    }
}
