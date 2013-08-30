package com.palantir.stash.stashbot.logger;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ensure our logger is on our classpath and seems to work
 * 
 * @author cmyers
 * 
 */
public class LoggerTest {

    Logger testLogger = LoggerFactory.getLogger(LoggerTest.class.toString());

    @Test
    public void testLogger() {
        Assert.assertNotNull(testLogger);

        testLogger.trace("trace");
        testLogger.debug("debug");
        testLogger.info("info");
        testLogger.warn("warn");
        testLogger.error("error");
    }
}
