package net.morimekta.config.format;

import net.morimekta.config.Config;
import net.morimekta.config.ConfigException;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for the JSON config format.
 */
public class TomlConfigTest {
    private JsonConfigFormatter formatter;
    private TomlConfigParser    parser;

    @Before
    public void setUp() throws ConfigException {
        formatter = new JsonConfigFormatter(true);
        parser    = new TomlConfigParser();
    }

    @Test
    public void testFormat() throws ConfigException {
        Config config = parser.parse(getClass().getResourceAsStream("/net/morimekta/config/format/config.toml"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        formatter.format(config, baos);
        String result = new String(baos.toByteArray(), UTF_8);
        assertEquals("{\n" +
                     "    \"b\": true,\n" +
                     "    \"conf.real\": 1234.5678,\n" +
                     "    \"conf.sub_str\": \"another string value.\",\n" +
                     "    \"date\": \"2016-07-02T16:01:02.055Z\",\n" +
                     "    \"i\": 1234,\n" +
                     "    \"s\": \"string value.\",\n" +
                     "    \"seq_b\": [\n" +
                     "        false,\n" +
                     "        false,\n" +
                     "        false,\n" +
                     "        true\n" +
                     "    ],\n" +
                     "    \"seq_i\": [\n" +
                     "        1,\n" +
                     "        2.2,\n" +
                     "        3.7,\n" +
                     "        -4\n" +
                     "    ],\n" +
                     "    \"seq_s\": [\n" +
                     "        \"a\",\n" +
                     "        \"b\",\n" +
                     "        \"c\"\n" +
                     "    ]\n" +
                     "}", result);

        assertEquals(1467475262055L, config.getDate("date").getTime());
    }

    @Test
    public void testEmptyCollection() {
        ByteArrayInputStream in = new ByteArrayInputStream((
                "[section]\n" +
                "value = []\n" +
                "").getBytes(UTF_8));

        Config config = parser.parse(in);

        assertNotNull(config.getCollection("section.value"));
        assertTrue(config.getCollection("section.value").isEmpty());
    }

    @Test
    public void testBadToml() {
        assertBad("boo",
                  "Expected key/value separator (one of [':', '=']): Got end of file");
        assertBad("[meh\n" +
                  "so = 6008\n",
                  "Expected end of section (']'): but found 'so'");
        assertBad("[meh] more\n" +
                  "so = 6008\n",
                  "Garbage after section: more");
        assertBad("so = 2008 more\n",
                  "Garbage after value: more");
        assertBad("so = 2008more\n",
                  "Wrongly terminated JSON number: '2008m'");
        assertBad("so = more\n",
                  "Unknown value token more");
        assertBad("so = \n",
                  "Expected TOML value: Got end of file");
    }

    private void assertBad(String toml, String message) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(toml.getBytes(UTF_8));
            parser.parse(in);
            fail("No exception: " + message);
        } catch (ConfigException e) {
            assertThat(e.getMessage(), is(message));
        }
    }
}
