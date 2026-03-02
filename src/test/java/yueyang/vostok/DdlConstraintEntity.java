package yueyang.vostok;

import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.util.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKId;

/**
 * 用于测试 @VKColumn 的 nullable / length / unique 属性对 DDL 自动建表的影响。
 */
@VKEntity(table = "t_ddl_constraint")
public class DdlConstraintEntity {

    @VKId
    private Long id;

    /** nullable=false, length=100, unique=true → NOT NULL UNIQUE VARCHAR(100) */
    @VKColumn(name = "email", nullable = false, length = 100, unique = true)
    private String email;

    /** nullable=false, length=50 → NOT NULL VARCHAR(50) */
    @VKColumn(name = "username", nullable = false, length = 50)
    private String username;

    /** 默认：nullable=true, length=255, unique=false → VARCHAR(255)，无 NOT NULL / UNIQUE */
    @VKColumn(name = "bio")
    private String bio;

    /** nullable=false → NOT NULL（Integer 包装类型不会因为 primitive 自动加 NOT NULL） */
    @VKColumn(name = "score", nullable = false)
    private Integer score;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
}
