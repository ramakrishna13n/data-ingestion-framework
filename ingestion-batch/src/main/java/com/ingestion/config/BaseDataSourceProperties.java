package com.ingestion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties
public class BaseDataSourceProperties {
    private String url;
    private String driverClassName;
    private String username;
    private String password;
}
