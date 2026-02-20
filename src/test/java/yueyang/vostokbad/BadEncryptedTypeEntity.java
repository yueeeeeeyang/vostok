package yueyang.vostokbad;

import yueyang.vostok.common.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKId;

@VKEntity(table = "t_bad_encrypt")
public class BadEncryptedTypeEntity {
    @VKId
    private Long id;

    @VKColumn(name = "age", encrypted = true)
    private Integer age;
}
