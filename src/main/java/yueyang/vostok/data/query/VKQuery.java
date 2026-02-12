package yueyang.vostok.data.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VKQuery {
    private final List<VKConditionGroup> groups = new ArrayList<>();
    private final List<VKOrder> orders = new ArrayList<>();
    private final List<String> groupBy = new ArrayList<>();
    private final List<VKConditionGroup> having = new ArrayList<>();
    private final List<VKAggregate> aggregates = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    public VKQuery() {
    }

    public static VKQuery create() {
        return new VKQuery();
    }

    public VKQuery where(VKCondition condition) {
        if (condition != null) {
            groups.add(VKConditionGroup.and(condition));
        }
        return this;
    }

    public VKQuery or(VKCondition... conditions) {
        groups.add(VKConditionGroup.or(conditions));
        return this;
    }

    public VKQuery whereGroup(VKConditionGroup group) {
        if (group != null) {
            groups.add(group);
        }
        return this;
    }

    public VKQuery orderBy(VKOrder order) {
        if (order != null) {
            orders.add(order);
        }
        return this;
    }

    public VKQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    public VKQuery offset(int offset) {
        this.offset = offset;
        return this;
    }

    public VKQuery groupBy(String... fields) {
        if (fields != null) {
            for (String f : fields) {
                if (f != null && !f.trim().isEmpty()) {
                    groupBy.add(f.trim());
                }
            }
        }
        return this;
    }

    public VKQuery having(VKCondition condition) {
        if (condition != null) {
            having.add(VKConditionGroup.and(condition));
        }
        return this;
    }

    public VKQuery havingGroup(VKConditionGroup group) {
        if (group != null) {
            having.add(group);
        }
        return this;
    }

    public VKQuery selectAggregates(VKAggregate... items) {
        if (items != null) {
            for (VKAggregate item : items) {
                if (item != null) {
                    aggregates.add(item);
                }
            }
        }
        return this;
    }

    public List<VKConditionGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public List<VKOrder> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    public List<String> getGroupBy() {
        return Collections.unmodifiableList(groupBy);
    }

    public List<VKConditionGroup> getHaving() {
        return Collections.unmodifiableList(having);
    }

    public List<VKAggregate> getAggregates() {
        return Collections.unmodifiableList(aggregates);
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }
}
