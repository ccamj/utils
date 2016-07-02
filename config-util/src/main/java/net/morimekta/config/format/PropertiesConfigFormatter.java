package net.morimekta.config.format;

import net.morimekta.config.Config;
import net.morimekta.config.ConfigException;
import net.morimekta.config.IncompatibleValueException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Format config into properties objects or files.
 */
public class PropertiesConfigFormatter implements ConfigFormatter {
    @Override
    public void format(Config config, OutputStream out) {
        try {
            Properties properties = format(config);
            properties.store(out, " generated by " + getClass().getName());
        } catch (IOException e) {
            throw new ConfigException(e, e.getMessage());
        }
    }

    /**
     * Format a config into a properties instance.
     *
     * @param config The config to put into the properties instance.
     * @return The properties instance.
     */
    public static Properties format(Config config) {
        Properties properties = new Properties();
        writeConfig("", config, properties);
        return properties;
    }

    // --- INTERNAL ---
    private static void writeConfig(String prefix, Config config, Properties properties) {
        for (String key : new TreeSet<>(config.keySet())) {
            Object value = config.get(key);

            String entryKey = makeKey(prefix, key);
            if (value instanceof Collection) {
                writeCollection(entryKey, (Collection) value, properties);
            } else {
                properties.setProperty(entryKey, Objects.toString(value));
            }
        }
    }

    private static <T> void writeCollection(String prefix, Collection<T> collection, Properties properties) {
        int i = 0;
        for (Object value : collection) {
            String key = makeKey(prefix, Integer.toString(i));
            if (value instanceof Collection) {
                throw new IncompatibleValueException("");
            } else {
                properties.setProperty(key, Objects.toString(value));
            }
            ++i;
        }
    }

    private static String makeKey(String prefix, String key) {
        if (prefix.length() > 0) {
            return String.format("%s.%s", prefix, key);
        }
        return key;
    }
}
