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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.user.PermissionAdminService;
import com.atlassian.stash.user.StashUser;
import com.atlassian.stash.user.UserAdminService;
import com.atlassian.stash.user.UserService;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;

public class PluginUserManagerTest {

    @Mock
    private UserAdminService uas;
    @Mock
    private PermissionAdminService pas;
    @Mock
    private UserService us;

    private PluginUserManager pum;

    @Mock
    private Repository repo;
    @Mock
    private StashUser stashUser;
    @Mock
    private JenkinsServerConfiguration jsc;

    private final String USER = "someUser";
    private final String PW = "somePassword";

    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);

        Mockito.when(jsc.getStashUsername()).thenReturn(USER);
        Mockito.when(jsc.getStashPassword()).thenReturn(PW);

        pum = new PluginUserManager(uas, pas, us);
    }

    private class GetUserByName implements Answer<StashUser> {

        private boolean isCreated = false;

        @Override
        public StashUser answer(InvocationOnMock invocation) throws Throwable {
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
    }

    @Test
    public void testAddUserToRepo() throws URISyntaxException {
    }
}
