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
