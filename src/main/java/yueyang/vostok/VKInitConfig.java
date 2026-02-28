package yueyang.vostok;

import yueyang.vostok.ai.VKAiConfig;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.config.VKConfigOptions;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.event.VKEventConfig;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.VostokGame;
import yueyang.vostok.http.VKHttpConfig;
import yueyang.vostok.log.VKLogConfig;
import yueyang.vostok.security.VKSecurityConfig;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.VostokWeb;

import java.util.function.Consumer;

public final class VKInitConfig {
    private final VKConfigOptions configOptions;
    private final VKLogConfig logConfig;
    private final VKSecurityConfig securityConfig;
    private final VKDataConfig dataConfig;
    private final String[] dataPackages;
    private final VKCacheConfig cacheConfig;
    private final VKEventConfig eventConfig;
    private final VKGameConfig gameConfig;
    private final VKHttpConfig httpConfig;
    private final VKFileConfig fileConfig;
    private final VKAiConfig aiConfig;
    private final VKWebConfig webConfig;
    private final Consumer<VostokWeb> webSetup;
    private final Consumer<VostokGame> gameSetup;
    private final boolean webStart;

    private VKInitConfig(Builder builder) {
        this.configOptions = builder.configOptions;
        this.logConfig = builder.logConfig;
        this.securityConfig = builder.securityConfig;
        this.dataConfig = builder.dataConfig;
        this.dataPackages = builder.dataPackages == null ? new String[0] : builder.dataPackages.clone();
        this.cacheConfig = builder.cacheConfig;
        this.eventConfig = builder.eventConfig;
        this.gameConfig = builder.gameConfig;
        this.httpConfig = builder.httpConfig;
        this.fileConfig = builder.fileConfig;
        this.aiConfig = builder.aiConfig;
        this.webConfig = builder.webConfig;
        this.webSetup = builder.webSetup;
        this.gameSetup = builder.gameSetup;
        this.webStart = builder.webStart;
    }

    public static Builder builder() {
        return new Builder();
    }

    public VKConfigOptions getConfigOptions() {
        return configOptions;
    }

    public VKLogConfig getLogConfig() {
        return logConfig;
    }

    public VKSecurityConfig getSecurityConfig() {
        return securityConfig;
    }

    public VKDataConfig getDataConfig() {
        return dataConfig;
    }

    public String[] getDataPackages() {
        return dataPackages.clone();
    }

    public VKCacheConfig getCacheConfig() {
        return cacheConfig;
    }

    public VKEventConfig getEventConfig() {
        return eventConfig;
    }

    public VKHttpConfig getHttpConfig() {
        return httpConfig;
    }

    public VKGameConfig getGameConfig() {
        return gameConfig;
    }

    public VKFileConfig getFileConfig() {
        return fileConfig;
    }

    public VKAiConfig getAiConfig() {
        return aiConfig;
    }

    public VKWebConfig getWebConfig() {
        return webConfig;
    }

    public Consumer<VostokWeb> getWebSetup() {
        return webSetup;
    }

    public Consumer<VostokGame> getGameSetup() {
        return gameSetup;
    }

    public boolean isWebStart() {
        return webStart;
    }

    public static final class Builder {
        private VKConfigOptions configOptions;
        private VKLogConfig logConfig;
        private VKSecurityConfig securityConfig;
        private VKDataConfig dataConfig;
        private String[] dataPackages = new String[0];
        private VKCacheConfig cacheConfig;
        private VKEventConfig eventConfig;
        private VKGameConfig gameConfig;
        private VKHttpConfig httpConfig;
        private VKFileConfig fileConfig;
        private VKAiConfig aiConfig;
        private VKWebConfig webConfig;
        private Consumer<VostokWeb> webSetup;
        private Consumer<VostokGame> gameSetup;
        private boolean webStart;

        public Builder configOptions(VKConfigOptions configOptions) {
            this.configOptions = configOptions;
            return this;
        }

        public Builder configOptions(Consumer<VKConfigOptions> customizer) {
            if (customizer == null) {
                return this;
            }
            VKConfigOptions options = this.configOptions == null ? new VKConfigOptions() : this.configOptions.copy();
            customizer.accept(options);
            this.configOptions = options;
            return this;
        }

        public Builder logConfig(VKLogConfig logConfig) {
            this.logConfig = logConfig;
            return this;
        }

        public Builder securityConfig(VKSecurityConfig securityConfig) {
            this.securityConfig = securityConfig;
            return this;
        }

        public Builder dataConfig(VKDataConfig dataConfig) {
            this.dataConfig = dataConfig;
            return this;
        }

        public Builder dataPackages(String... dataPackages) {
            this.dataPackages = dataPackages == null ? new String[0] : dataPackages.clone();
            return this;
        }

        public Builder cacheConfig(VKCacheConfig cacheConfig) {
            this.cacheConfig = cacheConfig;
            return this;
        }

        public Builder eventConfig(VKEventConfig eventConfig) {
            this.eventConfig = eventConfig;
            return this;
        }

        public Builder gameConfig(VKGameConfig gameConfig) {
            this.gameConfig = gameConfig;
            return this;
        }

        public Builder httpConfig(VKHttpConfig httpConfig) {
            this.httpConfig = httpConfig;
            return this;
        }

        public Builder fileConfig(VKFileConfig fileConfig) {
            this.fileConfig = fileConfig;
            return this;
        }

        public Builder aiConfig(VKAiConfig aiConfig) {
            this.aiConfig = aiConfig;
            return this;
        }

        public Builder webConfig(VKWebConfig webConfig) {
            this.webConfig = webConfig;
            return this;
        }

        public Builder webSetup(Consumer<VostokWeb> webSetup) {
            this.webSetup = webSetup;
            return this;
        }

        public Builder gameSetup(Consumer<VostokGame> gameSetup) {
            this.gameSetup = gameSetup;
            return this;
        }

        public Builder webStart(boolean webStart) {
            this.webStart = webStart;
            return this;
        }

        public VKInitConfig build() {
            return new VKInitConfig(this);
        }
    }
}
