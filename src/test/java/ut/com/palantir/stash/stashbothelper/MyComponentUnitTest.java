package ut.com.palantir.stash.stashbothelper;

import org.junit.Test;
import com.palantir.stash.stashbothelper.MyPluginComponent;
import com.palantir.stash.stashbothelper.MyPluginComponentImpl;

import static org.junit.Assert.assertEquals;

public class MyComponentUnitTest
{
    @Test
    public void testMyName()
    {
        MyPluginComponent component = new MyPluginComponentImpl(null);
        assertEquals("names do not match!", "myComponent",component.getName());
    }
}