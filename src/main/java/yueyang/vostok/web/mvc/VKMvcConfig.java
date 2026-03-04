package yueyang.vostok.web.mvc;

/** MVC 注解路由配置。 */
public final class VKMvcConfig {
    private boolean autoWrapEnabled = true;
    private String internalErrorMessage = "Internal Server Error";
    private boolean exposeExceptionMessage;
    private boolean failFastOnControllerLoad = true;
    private int bindErrorStatus = 400;
    private int internalErrorStatus = 500;

    public static VKMvcConfig defaults() {
        return new VKMvcConfig();
    }

    public VKMvcConfig copy() {
        return new VKMvcConfig()
                .autoWrapEnabled(autoWrapEnabled)
                .internalErrorMessage(internalErrorMessage)
                .exposeExceptionMessage(exposeExceptionMessage)
                .failFastOnControllerLoad(failFastOnControllerLoad)
                .bindErrorStatus(bindErrorStatus)
                .internalErrorStatus(internalErrorStatus);
    }

    public boolean autoWrapEnabled() {
        return autoWrapEnabled;
    }

    public VKMvcConfig autoWrapEnabled(boolean autoWrapEnabled) {
        this.autoWrapEnabled = autoWrapEnabled;
        return this;
    }

    public String internalErrorMessage() {
        return internalErrorMessage;
    }

    public VKMvcConfig internalErrorMessage(String internalErrorMessage) {
        if (internalErrorMessage != null && !internalErrorMessage.isBlank()) {
            this.internalErrorMessage = internalErrorMessage;
        }
        return this;
    }

    public boolean exposeExceptionMessage() {
        return exposeExceptionMessage;
    }

    public VKMvcConfig exposeExceptionMessage(boolean exposeExceptionMessage) {
        this.exposeExceptionMessage = exposeExceptionMessage;
        return this;
    }

    public boolean failFastOnControllerLoad() {
        return failFastOnControllerLoad;
    }

    public VKMvcConfig failFastOnControllerLoad(boolean failFastOnControllerLoad) {
        this.failFastOnControllerLoad = failFastOnControllerLoad;
        return this;
    }

    public int bindErrorStatus() {
        return bindErrorStatus;
    }

    public VKMvcConfig bindErrorStatus(int bindErrorStatus) {
        this.bindErrorStatus = bindErrorStatus <= 0 ? 400 : bindErrorStatus;
        return this;
    }

    public int internalErrorStatus() {
        return internalErrorStatus;
    }

    public VKMvcConfig internalErrorStatus(int internalErrorStatus) {
        this.internalErrorStatus = internalErrorStatus <= 0 ? 500 : internalErrorStatus;
        return this;
    }
}
