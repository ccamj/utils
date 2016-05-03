package net.morimekta.config.format;

import net.morimekta.config.Config;
import net.morimekta.config.ConfigException;
import net.morimekta.util.Strings;

import org.junit.Before;
import org.junit.Test;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for the JSON config format.
 */
public class PropertiesConfigFormatTest {
    private Config config;
    private PropertiesConfigFormat formatter;

    @Before
    public void setUp() throws ConfigException {
        JsonConfigFormat format = new JsonConfigFormat();
        config = format.parse(getClass().getResourceAsStream("/net/morimekta/config/format/config.json"));
        formatter = new PropertiesConfigFormat();
    }

    @Test
    public void testFormat() throws IOException, ConfigException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        formatter.format(baos, config);

        Properties ps = new Properties();
        ps.load(new ByteArrayInputStream(baos.toByteArray()));

        Properties pf = formatter.format(config);

        assertEquals(pf, ps);

        // NOTE: The ordering here does not really make any sense to me, but it
        // is generated by java... Probably a result of the HashMap. And it's
        // not stable (why??). So sorting all the lines before comparing.
        String[] result = new String(baos.toByteArray(), UTF_8).split("[\\n]");
        // Properties.store even adds the current date...
        result[1] = "# ...(date)...";
        Arrays.sort(result);

        assertEquals("# ...(date)...\n" +
                     "# generated by net.morimekta.config.format.PropertiesConfigFormat\n" +
                     "b=true\n" +
                     "conf.real=1234.5678\n" +
                     "conf.sub_str=another string value.\n" +
                     "i=1234\n" +
                     "s=string value.\n" +
                     "seq_b.0=false\n" +
                     "seq_b.1=false\n" +
                     "seq_b.2=false\n" +
                     "seq_b.3=true\n" +
                     "seq_c.0.my=sql\n" +
                     "seq_c.1.my=little pony\n" +
                     "seq_i.0=1\n" +
                     "seq_i.1=2.2\n" +
                     "seq_i.2=3.7\n" +
                     "seq_i.3=-4\n" +
                     "seq_s.0=a\n" +
                     "seq_s.1=b\n" +
                     "seq_s.2=c\n" +
                     "seq_seq.0.0=1\n" +
                     "seq_seq.0.1=2\n" +
                     "seq_seq.0.2=3\n" +
                     "seq_seq.1.0=3\n" +
                     "seq_seq.1.1=2\n" +
                     "seq_seq.1.2=1", Strings.join("\n", result));

        assertEquals("1", pf.getProperty("seq_i.0"));
        assertEquals("another string value.", pf.getProperty("conf.sub_str"));
    }

    @Test
    public void testParse() throws IOException, ConfigException {
        try {
            PropertiesConfigFormat formatter = new PropertiesConfigFormat();
            formatter.parse(new ByteArrayInputStream(new byte[0]));
            fail("No exception on parse.");
        } catch (IllegalStateException e) {
            assertEquals("not implemented", e.getMessage());
        }
    }
}