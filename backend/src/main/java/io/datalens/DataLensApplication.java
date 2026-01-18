package io.datalens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DataLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataLensApplication.class, args);
    }
}
