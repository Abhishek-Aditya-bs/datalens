package io.datalens.datasource.model;

/**
 * Connection status information.
 */
public class ConnectionStatus {
    private Environment environment;
    private boolean connected;
    private String message;
    private long connectionTimeMs;

    public ConnectionStatus() {}

    public ConnectionStatus(Environment environment, boolean connected, String message, long connectionTimeMs) {
        this.environment = environment;
        this.connected = connected;
        this.message = message;
        this.connectionTimeMs = connectionTimeMs;
    }

    // Getters
    public Environment getEnvironment() { return environment; }
    public boolean isConnected() { return connected; }
    public String getMessage() { return message; }
    public long getConnectionTimeMs() { return connectionTimeMs; }

    // Setters
    public void setEnvironment(Environment environment) { this.environment = environment; }
    public void setConnected(boolean connected) { this.connected = connected; }
    public void setMessage(String message) { this.message = message; }
    public void setConnectionTimeMs(long connectionTimeMs) { this.connectionTimeMs = connectionTimeMs; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Environment environment;
        private boolean connected;
        private String message;
        private long connectionTimeMs;

        public Builder environment(Environment environment) { this.environment = environment; return this; }
        public Builder connected(boolean connected) { this.connected = connected; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder connectionTimeMs(long connectionTimeMs) { this.connectionTimeMs = connectionTimeMs; return this; }

        public ConnectionStatus build() {
            return new ConnectionStatus(environment, connected, message, connectionTimeMs);
        }
    }
}
