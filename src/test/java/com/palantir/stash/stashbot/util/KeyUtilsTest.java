package com.palantir.stash.stashbot.util;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.palantir.stash.stashbot.logger.PluginLoggerFactory;

public class KeyUtilsTest {

    private static final String TEST_PUB_KEY =
        "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQCiXuTcLgf9qP4r6t0pEWq29TYhiYLA8Guj4gdCIm92D6zm86O2v57eTz8YUjOiidwCDQc4k7pmDjhK52I5cluET1DCLjW7Y1ZmDgm41/BKz7k3cfrhhdz39Zy7VDpV8ivFdlZgSz7eryA+sgnV1gpGpGMeX56Xa/Ju1Mqq+twIgMjUK1jd/IcRghQZA6Urjhx3WK0g2cNC2K5HuNUl36jWUln3uL0Z1bxGkHYUE2yIK5KuEML5yLT3EuNGNxBIxzkdeovJlWGuPothMFw72IIUagVeIZSK7gwp54yEq+PkQV2Ar6nSDkZ9EplJ34jstrql3gwQwNQvL45In1CITEc1QcjxgPS5nDIoe+NkLxqV3l+xjfyW/PaYME86DqJ587XOmzgAD3hd/ABiRG1ciDJeQJraRPhnXSJgSnVYwcCcDg1B5rl7jBRkkQ3HvVp+IZ0Wrubguov9yH7Oq0jTKu8t89YGIelR0+BkiAyxufYNPjjG1mH7ocWbt8XGolKvrJzQuZ5xGuUPV7/VPT7RVyYhijJ1oJWeG9ZU+UiVddnkXqHH6M+9Obl65WHqQ9Eo6DcUeVY/CWL4wXLu55mMd8XATfmxNHq3RYdddqHcLuhgZG/0SLFY7UBk8PyOpDQT5LiKLhjVQ+SwmYtBEYngW0KvY4dufpCHdyFgua2DmXrB3w== testkey";
    private static final String TEST_PRIVATE_KEY =
        "-----BEGIN RSA PRIVATE KEY-----"
            + "MIIJJwIBAAKCAgEAol7k3C4H/aj+K+rdKRFqtvU2IYmCwPBro+IHQiJvdg+s5vOj"
            + "tr+e3k8/GFIzooncAg0HOJO6Zg44SudiOXJbhE9Qwi41u2NWZg4JuNfwSs+5N3H6"
            + "4YXc9/Wcu1Q6VfIrxXZWYEs+3q8gPrIJ1dYKRqRjHl+el2vybtTKqvrcCIDI1CtY"
            + "3fyHEYIUGQOlK44cd1itINnDQtiuR7jVJd+o1lJZ97i9GdW8RpB2FBNsiCuSrhDC"
            + "+ci09xLjRjcQSMc5HXqLyZVhrj6LYTBcO9iCFGoFXiGUiu4MKeeMhKvj5EFdgK+p"
            + "0g5GfRKZSd+I7La6pd4MEMDULy+OSJ9QiExHNUHI8YD0uZwyKHvjZC8ald5fsY38"
            + "lvz2mDBPOg6iefO1zps4AA94XfwAYkRtXIgyXkCa2kT4Z10iYEp1WMHAnA4NQea5"
            + "e4wUZJENx71afiGdFq7m4LqL/ch+zqtI0yrvLfPWBiHpUdPgZIgMsbn2DT44xtZh"
            + "+6HFm7fFxqJSr6yc0LmecRrlD1e/1T0+0VcmIYoydaCVnhvWVPlIlXXZ5F6hx+jP"
            + "vTm5euVh6kPRKOg3FHlWPwli+MFy7ueZjHfFwE35sTR6t0WHXXah3C7oYGRv9Eix"
            + "WO1AZPD8jqQ0E+S4ii4Y1UPksJmLQRGJ4FtCr2OHbn6Qh3chYLmtg5l6wd8CAwEA"
            + "AQKCAgAssT//VvA+IuDrR7deUXv1JiOjMY16+/I05scmrgHOFlx6KX/bknzxJhDw"
            + "6ddqmtWi/uEI8qiw5KMcAvpnY5HLJmXNPRjvHlWuu5hzd4SdovWRTF9I6ia7XbCp"
            + "Y3K3K3Re4sa9tJh2hO+0Mh9A66xia2cY+irV9RGC7jFmxKwB7yjYNaI9X//xksNj"
            + "azxwc6pkM1VcIHR9ltTJyxdoWFrJu1smM4RhcxJiveqydfI+vPefz99LD7K3+0vx"
            + "jMB/t86BzbRiZSrA4lhNBRmuI1cStWgK0+VmJEXW1vQ3pHSS5GcPP8rXlbRTQuZM"
            + "jinLNjTD6Q2bIuiEoGtik+9/xLcoglHbHY8W8d0H3bFE8/sfDimLgRTzR7QxqgDB"
            + "QIRHu3gLjuW9CLMnNIvpxyVxu4ITx/FZo3Iyq77ZHL84b5glyh474vFJN7GZUZEV"
            + "mnsDxYx9YnRH3AGQ1TM174ZOvCYEF6pa5kN5gUhOlWdZp3eGKuUuPDOfR62KnaDa"
            + "EJknMwnWlrPgBX/TL+i3ykgqVBh8FP2BoLVnI+rq4eziXdr6yx3fabvkUnCHrH+x"
            + "ek7Zb7++fI7BVEkQTh8opGtIKnsoiIw/E0E2Km6WEygMZOvPco3iHXck8SKK+oJc"
            + "/ZUCA2qFIjCjlEVSK8IN9lJsEqzE/nLg+ycAfVtd96HJXOTiOQKCAQEA0JUM+lHQ"
            + "a2xQHgHMmLNNMBFJkllXzHg42xawJmr0jwSh7RWcc17DaTn7NcAJ5qy2Jut/lNTU"
            + "jhrfMgBfcbrBuDHXBedMcwzhNxAOCMRZdYrWHAW7Auo0WARxMJUhTzGQZ5x6KrJH"
            + "EroinS1Q9FtDpM0h65asGVTvvcIfRuVPoqhpRi6WcahHFo34LZmUHiNXDdbF2W3O"
            + "6IdHhlgNVQHysJChe4mx4JEM75bFOU60P0EWhotbkIQZo1g97Qk6L9QP3CEJtmyC"
            + "4fhOg6GTgKVW5BIQRFtk9DXfwjpLI4pv+M6YTewiGUpzq66tetk+GnoRVJFsbVW6"
            + "+Xp4uZRNVyFlcwKCAQEAx0hzL9pDwtw2/Das0Hz0KqhbQi1TIiOrYdhm69ej0sru"
            + "ghgPBbl429EczoOk5kvzM4o8PGyLW+aVAw0swmI4QDNZCXom6NoWBJ+pcrqyaJnw"
            + "GD3T0HJjvyBjqAarKu7Bz73RRJ/K5XQy73UVQvvZIg0SBAr3f75H4bncyBo67Osd"
            + "LeRiXmwSm4n/MQ2GDLUwgGT1hqeiVi0qh5AnPy4TvPVl9BaZ98czvw1DWqFIgHhW"
            + "W2T7L36+i6fmW+AyaQxXMt4fktPmbQ5ytGo6yaYEdVIznKnRoB/nuj3zEOtEdrLq"
            + "q6e6oeW+nBZPuDuwwV/1wjccyhaLbpjLmm8rdsh25QKCAQAEW2i2/eiFpfCSf+Uf"
            + "N3egTmwkA3vDCKvfX7z0QH21Uxpy0mW/PzaDxzNJLybFy8vOSlMS79M02Lja2Ykk"
            + "3grX5yqfs/Iz4Qv/U3WHl1prCyhn03LRn8TSJd2bbzIP7nAeyfT/WVQEvyCj+eNc"
            + "B2AFXEeeHTqhGNYdN6XhnD6qniv8zFJWM3awsOyDP1cJviE+Z8MgRJvy3YiP9Nzq"
            + "bqz8JTlKVFkD4OPNSW/7P3qia6Cl1Nlnzmqer+QzRGMTxrFCHuqOBfxB7ibttv1W"
            + "zZ1gDESMuQAF3e47p24UYvrtzpr93bXko1qBNpfnHgyfDve3tYX3TrgmJ/nHnqJy"
            + "9H6jAoIBADC5rjBNFbRXU7pnQ+nWI30qnOWZX3JBdm3+E2wm1Y98vsRgTYwAxWQ5"
            + "xOlZlsAYYeaALio3//sAbf7JAeClU0ufQm3myAy3BhsrTqsWqviYavQCaf/VeD4L"
            + "FVLIDqH4rrq0lq5Iw7tGpg2WexRdr8F7oKQMamd5hunSIAFtKGs1MXU/Gi4sDoCp"
            + "RRQhUl1ZD/rUtJOv+SlI8dUjkfUItxrEhaEgxZSnyCc2fvlx5eBDjBncnu4F92D+"
            + "NHzSXIEC2288kEu+M+EGX1Vtg+I6DW0CJExx2SdlpasL6pqAa8T6Chbec+uR2QEh"
            + "E2zga19cryJFh2yCBqWMBnxEIkRwz2ECggEAWT6Fr95npI2W6W0FvxH+BbktIeF2"
            + "s7hlBu0YYSutbBgmCTTYeNuddDBuzRNCpbjRaUvlDJoUMwbLwExPx4FSOegxuCX9"
            + "tKRDR2u5YS0Hc1kMiLFNXpe5qr2CTJT5i/hGSxb0H2x4HgTnVoYEkxnd/4ONUjD5"
            + "an4q4iylXPKvbtdeYr1BM81szEJrZvAySIUCfygrziXNRnzXoWZhSLjKK3OFQScR"
            + "xDBMj7cLLKMw2618lqg3e7kpQYaR9ggeuAi6xSL3m9oOomUd7kkOyCU7EHZSF5nl"
            + "TrergD+lWMX3B84EA9EFP7YgXGT/rQq+nzyghW8u0FwG8vSrYiYraca3gA=="
            + "-----END RSA PRIVATE KEY-----";

    private KeyUtils ku;

    @Before
    public void setUp() {
        ku = new KeyUtils(new PluginLoggerFactory());
    }

    @Test
    public void testGetPublicKey() {
        PublicKey pk = ku.getPublicKey(TEST_PUB_KEY);
        Assert.assertNotNull(pk);
        Assert.assertEquals("RSA", pk.getAlgorithm());
        RSAPublicKey rpk = (RSAPublicKey) pk;
        // manually extracted these values from the hard-coded key above
        Assert.assertEquals(BigInteger.valueOf(65537), rpk.getPublicExponent());
        Assert.assertEquals(BigInteger.valueOf(819780553), rpk.getModulus().mod(BigInteger.valueOf(1234567890)));

    }

    /*
    @Test
    public void testGetPrivateKey() {
        PrivateKey pk = ku.getPrivateKey(TEST_PRIVATE_KEY);
        Assert.assertNotNull(pk);
        Assert.assertEquals("RSA", pk.getAlgorithm());
        RSAPrivateKey rpk = (RSAPrivateKey) pk;
        // manually extracted these values from the hard-coded key above
        Assert.assertEquals(BigInteger.valueOf(65537), rpk.getPrivateExponent());
        Assert.assertEquals(819780553, rpk.getModulus().mod(BigInteger.valueOf(1234567890)));

    }
    */
}
