package com.palantir.stash.stashbot.mocks;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.bitbucket.user.EscalatedSecurityContext;
import com.atlassian.bitbucket.user.SecurityService;
import com.atlassian.bitbucket.util.Operation;

public class MockSecurityServiceBuilder {

    private final SecurityService ss;
    private final EscalatedSecurityContext esc;

    public MockSecurityServiceBuilder() throws Throwable {
        ss = Mockito.mock(SecurityService.class);
        esc = Mockito.mock(EscalatedSecurityContext.class);

        Answer<Void> justDoIt = new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object oop = invocation.getArguments()[0];
                @SuppressWarnings("unchecked")
                Operation<Void, Exception> op = (Operation<Void, Exception>) oop;
                op.perform();
                return null;
            }
        };
        Mockito.when(esc.call(Mockito.<Operation<Void, Throwable>> any())).thenAnswer(justDoIt);

        Mockito.when(ss.impersonating(Mockito.any(ApplicationUser.class), Mockito.anyString())).thenReturn(esc);
        Mockito.when(ss.anonymously(Mockito.anyString())).thenReturn(esc);
    }

    public SecurityService getSecurityService() {
        return ss;
    }

    public EscalatedSecurityContext getEscalatedSecurityContext() {
        return esc;
    }
}
