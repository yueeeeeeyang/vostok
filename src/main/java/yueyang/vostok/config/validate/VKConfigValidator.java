package yueyang.vostok.config.validate;

@FunctionalInterface
public interface VKConfigValidator {
    void validate(VKConfigView view);
}
