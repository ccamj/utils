package net.morimekta.config.format;

import net.morimekta.config.Config;
import net.morimekta.config.ConfigException;
import net.morimekta.config.SimpleConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Format config into properties objects or files.
 */
public class PropertiesConfigParser implements ConfigParser {
    @Override
    public Config parse(InputStream in) {
        try {
            Config config = new SimpleConfig();
            Properties properties = new Properties();
            properties.load(in);

            for (Object o : new TreeSet<>(properties.keySet())) {
                String key = o.toString();
                if (isPositional(key)) {
                    String entryKey = entryKey(key);
                    if (config.containsKey(entryKey)) {
                        config.getSequence(entryKey).add(properties.getProperty(key));
                    } else {
                        LinkedList<Object> sequence = new LinkedList<>();
                        sequence.add(properties.getProperty(key));
                        config.putSequence(entryKey, sequence);
                    }
                } else {
                    config.put(key, properties.getProperty(key));
                }
            }
            return config;
        } catch (IOException e) {
            throw new ConfigException(e, e.getMessage());
        }
    }

    protected boolean isPositional(String key) {
        try {
            String[] parts = key.split("[.]");
            String last = parts[parts.length - 1];
            if (Integer.parseInt(last) >= 0) {
                return true;
            }
        } catch (NumberFormatException e) {
            // Ignore.
        }
        return false;
    }

    private String entryKey(String positional) {
        return positional.substring(0, positional.lastIndexOf("."));
    }
}
