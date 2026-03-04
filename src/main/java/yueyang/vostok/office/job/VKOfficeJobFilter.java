package yueyang.vostok.office.job;

/**
 * 任务通知过滤器。
 *
 * <p>可通过 builder 组合 jobType / status / jobIdPrefix / tag 条件。</p>
 */
@FunctionalInterface
public interface VKOfficeJobFilter {
    boolean test(VKOfficeJobNotification notification);

    static VKOfficeJobFilter all() {
        return n -> true;
    }

    static VKOfficeJobFilter byType(VKOfficeJobType type) {
        return n -> n != null && n.type() == type;
    }

    static Builder builder() {
        return new Builder();
    }

    /** 过滤器构建器。 */
    final class Builder {
        private VKOfficeJobType type;
        private VKOfficeJobStatus status;
        private String jobIdPrefix;
        private String tag;

        public Builder type(VKOfficeJobType type) {
            this.type = type;
            return this;
        }

        public Builder status(VKOfficeJobStatus status) {
            this.status = status;
            return this;
        }

        public Builder jobIdPrefix(String jobIdPrefix) {
            this.jobIdPrefix = jobIdPrefix;
            return this;
        }

        public Builder tag(String tag) {
            this.tag = tag;
            return this;
        }

        public VKOfficeJobFilter build() {
            return n -> {
                if (n == null) {
                    return false;
                }
                if (type != null && n.type() != type) {
                    return false;
                }
                if (status != null && n.status() != status) {
                    return false;
                }
                if (jobIdPrefix != null && !jobIdPrefix.isBlank()) {
                    if (n.jobId() == null || !n.jobId().startsWith(jobIdPrefix)) {
                        return false;
                    }
                }
                if (tag != null && !tag.isBlank()) {
                    return tag.equals(n.tag());
                }
                return true;
            };
        }
    }
}
