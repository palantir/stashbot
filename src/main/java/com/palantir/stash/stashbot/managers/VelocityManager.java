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

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.tools.generic.EscapeTool;

/**
 * Make creation/injection of velocity engine and context automatic
 * 
 * @author cmyers
 * 
 */
public class VelocityManager {

    private final VelocityEngine velocityEngine;
    private final EscapeTool escapeTool;

    public VelocityManager() {
        velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();
        escapeTool = new EscapeTool();

    }

    // This is for testing/mocking
    public VelocityManager(VelocityEngine ve, EscapeTool esc) {
        velocityEngine = ve;
        escapeTool = esc;
    }

    public VelocityEngine getVelocityEngine() {
        return velocityEngine;
    }

    public VelocityContext getVelocityContext() {
        VelocityContext vc = new VelocityContext();
        vc.put("esc", escapeTool);
        return vc;
    }
}
