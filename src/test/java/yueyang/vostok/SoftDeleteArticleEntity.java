package yueyang.vostok;

import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKLogicDelete;
import yueyang.vostok.util.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKId;

/**
 * 带逻辑删除标志的测试实体，用于 VostokDataVersionLogicDeleteTest。
 */
@VKEntity(table = "t_soft_delete_article")
public class SoftDeleteArticleEntity {

    @VKId
    private Long id;

    @VKColumn(name = "title")
    private String title;

    @VKColumn(name = "author")
    private String author;

    /** 逻辑删除：0=正常，1=已删除 */
    @VKLogicDelete(deletedValue = "1", normalValue = "0")
    @VKColumn(name = "is_deleted")
    private Integer isDeleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
}
