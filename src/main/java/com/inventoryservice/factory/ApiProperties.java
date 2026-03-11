package com.inventoryservice.factory;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Binds all values from api-paths.yml into strongly-typed fields.
 *
 * File location: src/main/resources/api-paths.yml
 *
 * As new controllers are added, add a new nested static class here
 * and a corresponding block in api-paths.yml.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "api")
@PropertySource(
        value = "classpath:api-paths.yml",
        factory = YamlPropertySourceFactory.class
)
public class ApiProperties {

    private Inventory inventory = new Inventory();

    @Getter
    @Setter
    public static class Inventory {
        private String base;
        private String check;
        private String reserve;
        private String reduce;
        private String restore;
        private String add;
        private String getByProduct;
        private String getAll;
    }
}