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
