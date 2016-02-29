package net.morimekta.util.json;

import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Testing special aspects of the JsonToken class.
 */
public class JsonTokenTest {
    @Test
    public void testIsNull() {
        JsonToken token;

        token = new JsonToken(JsonToken.Type.TOKEN, buffer, 57, 4, 1, 1);
        assertEquals("null", token.asString());
        assertTrue(token.isNull());

        token = new JsonToken(JsonToken.Type.TOKEN, buffer, 57, 3, 1, 1);
        assertEquals("nul", token.asString());
        assertFalse(token.isNull());
    }

    @Test
    public void testIsSymbol() {
        JsonToken token;

        token = new JsonToken(JsonToken.Type.SYMBOL, buffer, 0, 1, 1, 1);
        assertTrue(token.isSymbol());

        token = new JsonToken(JsonToken.Type.SYMBOL, buffer, 1, 1, 1, 1);
        assertFalse(token.isSymbol());

        token = new JsonToken(JsonToken.Type.SYMBOL, buffer, 0, 1, 1, 1);
        assertTrue(token.isSymbol('['));
        assertFalse(token.isSymbol(']'));
    }

    @Test
    public void testIsBoolean() {
        JsonToken token;

        token = new JsonToken(JsonToken.Type.TOKEN, "true".getBytes(), 0, 4, 1, 1);
        assertTrue(token.isBoolean());
        assertTrue(token.booleanValue());
        token = new JsonToken(JsonToken.Type.TOKEN, "false".getBytes(), 0, 5, 1, 1);
        assertTrue(token.isBoolean());
        assertFalse(token.booleanValue());
        token = new JsonToken(JsonToken.Type.TOKEN, "yes".getBytes(), 0, 3, 1, 1);
        assertFalse(token.isBoolean());
    }

    @Test
    public void testNumbers() {
        JsonToken token;

        token = new JsonToken(JsonToken.Type.TOKEN, "44".getBytes(), 0, 2, 1, 1);
        assertFalse(token.isInteger());
        assertFalse(token.isReal());

        token = new JsonToken(JsonToken.Type.NUMBER, "44".getBytes(), 0, 2, 1, 1);
        assertTrue(token.isInteger());
        assertTrue(token.isReal());
        assertEquals((byte) 44, token.byteValue());
        assertEquals((short) 44, token.shortValue());
        assertEquals(44, token.intValue());
        assertEquals(44L, token.longValue());
        assertEquals(44.0, token.doubleValue(), 0.001);

        token = new JsonToken(JsonToken.Type.NUMBER, "44.44".getBytes(), 0, 5, 1, 1);
        assertFalse(token.isInteger());
        assertTrue(token.isReal());
        assertEquals(44.44, token.doubleValue(), 0.001);
    }

    private final byte[] buffer = "[\"\\\\↓ÑI©ôðé\\b\\f\\r\\n\\t\\\"\\u4f92\",{\"key\":1337,123.45:null}]".getBytes(UTF_8);

    @Test
    public void testDecodeJsonLiteral() {
        JsonToken token = new JsonToken(JsonToken.Type.LITERAL, buffer, 1, 36, 1, 1);
        assertEquals("\"\\\\↓ÑI©ôðé\\b\\f\\r\\n" +
                     "\\t\\\"\\u4f92\"", token.asString());
        assertEquals("\\↓ÑI©ôðé\b\f\r\n" +
                     "\t\"侒", token.decodeJsonLiteral());

        // and with illegal escape characters.
        token = new JsonToken(JsonToken.Type.LITERAL, "\"\\0\"".getBytes(), 0, 4, 1, 1);
        assertEquals("?", token.decodeJsonLiteral());
        // and with illecal escaped unicode.
        token = new JsonToken(JsonToken.Type.LITERAL, "\"\\u01\"".getBytes(), 0, 6, 1, 1);
        assertEquals("?", token.decodeJsonLiteral());
        token = new JsonToken(JsonToken.Type.LITERAL, "\"\\ubals\"".getBytes(), 0, 8, 1, 1);
        assertEquals("?", token.decodeJsonLiteral());
    }

    @Test
    public void testHashCode() {
        JsonToken token1 = new JsonToken(JsonToken.Type.LITERAL, buffer, 1, 36, 1, 1);
        JsonToken token2 = new JsonToken(JsonToken.Type.SYMBOL, buffer, 0, 1, 1, 1);
        JsonToken token3 = new JsonToken(JsonToken.Type.TOKEN, buffer, 57, 4, 1, 1);

        assertNotEquals(token1, token2);
        assertNotEquals(token1, token3);
        assertNotEquals(token2, token3);

        assertNotEquals(token1.hashCode(), token2.hashCode());
        assertNotEquals(token1.hashCode(), token3.hashCode());
        assertNotEquals(token2.hashCode(), token3.hashCode());
    }

    @Test
    public void testNotEquals() {
        JsonToken token1 = new JsonToken(JsonToken.Type.LITERAL, buffer, 1, 36, 1, 1);

        assertTrue(token1.equals(token1));
        assertFalse(token1.equals(null));
        assertFalse(token1.equals(new Object()));
    }
}
