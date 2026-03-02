package yueyang.vostok;

import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKVersion;
import yueyang.vostok.util.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKId;

/**
 * 带乐观锁版本字段的测试实体，用于 VostokDataVersionLogicDeleteTest。
 */
@VKEntity(table = "t_versioned_order")
public class VersionedOrderEntity {

    @VKId
    private Long id;

    @VKColumn(name = "title")
    private String title;

    @VKColumn(name = "amount")
    private Integer amount;

    @VKVersion
    @VKColumn(name = "version")
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
