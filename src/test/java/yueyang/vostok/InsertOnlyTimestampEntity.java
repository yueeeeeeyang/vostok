package yueyang.vostok;

import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.util.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKId;

/**
 * 带 insertable/updatable 控制属性的测试实体，用于 VostokDataVersionLogicDeleteTest。
 * created_at 字段只写入一次，updatable=false 禁止在 UPDATE 中修改。
 */
@VKEntity(table = "t_insert_only_ts")
public class InsertOnlyTimestampEntity {

    @VKId
    private Long id;

    @VKColumn(name = "content")
    private String content;

    /**
     * 创建时间：insertable=true（写入 INSERT），updatable=false（排除出 UPDATE SET 子句）。
     */
    @VKColumn(name = "created_at", updatable = false)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
