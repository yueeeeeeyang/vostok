package yueyang.vostok;

import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 测试任务实体。
 */
@VKEntity(table = "t_task")
public class TaskEntity {
    @VKId
    private Long id;

    @VKColumn(name = "start_date")
    private LocalDate startDate;

    @VKColumn(name = "finish_time")
    private LocalDateTime finishTime;

    private BigDecimal amount;

    private Status status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
