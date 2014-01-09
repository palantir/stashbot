package com.palantir.stash.stashbot.managers;

import java.io.StringWriter;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class VelocityManagerTest {

    private VelocityManager vm;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        vm = new VelocityManager();
    }

    @Test
    public void testVelocityTools() {
        VelocityEngine ve = vm.getVelocityEngine();
        VelocityContext vc = vm.getVelocityContext();

        // contains escape tool
        Assert.assertTrue(vc.containsKey("esc"));

        // escape tool works
        Template test = ve.getTemplate("test-template.vm");
        StringWriter testWriter = new StringWriter();
        test.merge(vc, testWriter);
        String result = testWriter.toString();
        Assert.assertEquals("&amp;\n", result);
    }
}
