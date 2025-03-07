package xyz.rebasing.rebot.plugin.dimep.utils;

import java.time.Instant;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilsTest {

    @Test
    public void testDecode() {
        String encoded = "cGFzc3dvcmQ=";
        String decoded = Utils.base64decode(encoded);
        assertEquals("password", decoded);
    }

    @Test
    public void testFormatDate() {
        String ist = "2024-11-08T18:35:39.231050Z";
        Instant instant = Instant.parse(ist);
        assertEquals("08-11-2024 15:35:00 - Friday - OK", (Utils.formatDate(instant)));
    }
}
