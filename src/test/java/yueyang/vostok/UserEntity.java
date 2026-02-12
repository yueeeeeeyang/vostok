package yueyang.vostok;

import yueyang.vostok.annotation.VKColumn;
import yueyang.vostok.annotation.VKEntity;
import yueyang.vostok.annotation.VKId;

/**
 * 测试用户实体。
 */
@VKEntity(table = "t_user")
public class UserEntity {
    @VKId
    private Long id;

    @VKColumn(name = "user_name")
    private String name;

    private Integer age;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
