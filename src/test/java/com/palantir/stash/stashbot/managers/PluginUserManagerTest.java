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
package com.palantir.stash.stashbot.managers;

import java.net.URISyntaxException;
import java.security.PublicKey;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.atlassian.bitbucket.permission.PermissionAdminService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.ssh.SshKeyService;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.bitbucket.user.UserAdminService;
import com.atlassian.bitbucket.user.UserService;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.util.KeyUtils;

public class PluginUserManagerTest {

    @Mock
    private UserAdminService uas;
    @Mock
    private PermissionAdminService pas;
    @Mock
    private UserService us;
    @Mock
    private SshKeyService sks;
    @Mock
    private KeyUtils ku;
    @Mock
    private ConfigurationPersistenceService cps;

    private PluginUserManager pum;

    @Mock
    private Repository repo;
    @Mock
    private ApplicationUser stashUser;
    @Mock
    private JenkinsServerConfiguration jsc;

    private PluginLoggerFactory plf = new PluginLoggerFactory();

    private final String USER = "someUser";
    private final String PW = "somePassword";
    private final String SSH_KEY = "ssh-rsa AAAAdotdot\\dotend";
    private final String SSH_KEY_LABEL = "label";
    private final String SSH_KEY_WITH_LABEL = SSH_KEY + " " + SSH_KEY_LABEL;
    @Mock
    private PublicKey pk;

    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);

        Mockito.when(jsc.getStashUsername()).thenReturn(USER);
        Mockito.when(jsc.getStashPassword()).thenReturn(PW);
        Mockito.when(cps.getDefaultPublicSshKey()).thenReturn(SSH_KEY_WITH_LABEL);
        Mockito.when(ku.getPublicKey(Mockito.anyString())).thenReturn(pk);

        pum = new PluginUserManager(uas, pas, us, sks, cps, ku, plf);
    }

    private class GetUserByName implements Answer<ApplicationUser> {

        private boolean isCreated = false;

        @Override
        public ApplicationUser answer(InvocationOnMock invocation) throws Throwable {
            if (isCreated) {
                return stashUser;
            }
            return null;
        }

        public void create() {
            isCreated = true;
        }
    }

    @Test
    public void testCreateUser() throws URISyntaxException {

        final GetUserByName getUserByName = new GetUserByName();
        final Answer<Void> createUser = new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                getUserByName.create();
                return null;
            }
        };

        Mockito.when(us.getUserByName(USER)).thenAnswer(getUserByName);
        Mockito.doAnswer(createUser).when(uas)
            .createUser(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        pum.createStashbotUser(jsc);

        Mockito.verify(uas).createUser(Mockito.eq(USER), Mockito.eq(PW), Mockito.eq(USER), Mockito.anyString());
        Mockito.verify(sks).addForUser(stashUser, SSH_KEY, SSH_KEY_LABEL);
    }
}
