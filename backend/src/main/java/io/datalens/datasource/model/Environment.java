package io.datalens.datasource.model;

/**
 * Database environment enumeration.
 */
public enum Environment {
    DEV,
    UAT,
    PROD;

    public static Environment fromString(String env) {
        if (env == null || env.isBlank()) {
            throw new IllegalArgumentException("Environment cannot be null or empty");
        }
        return switch (env.toUpperCase().trim()) {
            case "DEV", "DEVELOPMENT" -> DEV;
            case "UAT", "TEST", "STAGING" -> UAT;
            case "PROD", "PRODUCTION" -> PROD;
            default -> throw new IllegalArgumentException("Unknown environment: " + env);
        };
    }
}
