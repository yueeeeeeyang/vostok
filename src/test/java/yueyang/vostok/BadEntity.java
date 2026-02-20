package yueyang.vostokbad;

import yueyang.vostok.util.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKId;

/**
 * 非法表名实体，用于安全校验测试。
 */
@VKEntity(table = "t_user;drop")
public class BadEntity {
    @VKId
    private Long id;
}
