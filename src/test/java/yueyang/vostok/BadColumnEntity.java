package yueyang.vostokbad;

import yueyang.vostok.annotation.VKColumn;
import yueyang.vostok.annotation.VKEntity;
import yueyang.vostok.annotation.VKId;

/**
 * 非法列名实体，用于安全校验测试。
 */
@VKEntity(table = "t_user")
public class BadColumnEntity {
    @VKId
    @VKColumn(name = "id;drop")
    private Long id;
}
