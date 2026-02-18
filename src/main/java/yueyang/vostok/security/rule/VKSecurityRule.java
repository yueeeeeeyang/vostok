package yueyang.vostok.security.rule;

public interface VKSecurityRule {
    String name();

    VKSecurityFinding apply(VKSecurityContext context);
}
