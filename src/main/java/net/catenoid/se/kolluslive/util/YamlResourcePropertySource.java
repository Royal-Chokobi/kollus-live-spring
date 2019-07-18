package net.catenoid.se.kolluslive.util;

import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;

import java.io.IOException;

public class YamlResourcePropertySource extends PropertiesPropertySource {
    public YamlResourcePropertySource(String name, EncodedResource resource) throws IOException {
        super(name, new YamlPropertiesProcessor(resource.getResource()).createProperties());
    }
}
