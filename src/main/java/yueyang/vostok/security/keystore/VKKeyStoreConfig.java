package yueyang.vostok.security.keystore;

public class VKKeyStoreConfig {
    private String baseDir = System.getProperty("user.home", ".") + "/.vostok/keystore";
    private String masterKey = "vostok-default-master-key-change-me";
    private boolean autoCreate = true;

    public String getBaseDir() {
        return baseDir;
    }

    public VKKeyStoreConfig baseDir(String baseDir) {
        if (baseDir != null && !baseDir.isBlank()) {
            this.baseDir = baseDir;
        }
        return this;
    }

    public String getMasterKey() {
        return masterKey;
    }

    public VKKeyStoreConfig masterKey(String masterKey) {
        if (masterKey != null && !masterKey.isBlank()) {
            this.masterKey = masterKey;
        }
        return this;
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public VKKeyStoreConfig autoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    public VKKeyStoreConfig copy() {
        return new VKKeyStoreConfig()
                .baseDir(this.baseDir)
                .masterKey(this.masterKey)
                .autoCreate(this.autoCreate);
    }
}
