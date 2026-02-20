package yueyang.vostok;

import yueyang.vostok.util.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKId;

@VKEntity(table = "t_user_secret")
public class EncryptedUserEntity {
    @VKId
    private Long id;

    @VKColumn(name = "secret_name", encrypted = true, keyId = "enc-k1")
    private String secretName;

    @VKColumn(name = "age")
    private Integer age;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
