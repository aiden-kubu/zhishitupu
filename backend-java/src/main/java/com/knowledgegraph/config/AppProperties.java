package com.knowledgegraph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * kg.* 配置项（§15）。
 */
@ConfigurationProperties(prefix = "kg")
public class AppProperties {

    private Storage storage = new Storage();
    private Cors cors = new Cors();

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public static class Storage {
        private String root = System.getProperty("user.home") + "/.knowledge-graph/storage";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
