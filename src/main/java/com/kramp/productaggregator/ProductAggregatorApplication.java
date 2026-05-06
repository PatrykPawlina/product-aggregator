package com.kramp.productaggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ProductAggregatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductAggregatorApplication.class, args);
    }

}
