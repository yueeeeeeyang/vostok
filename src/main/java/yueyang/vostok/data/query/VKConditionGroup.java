package yueyang.vostok.data.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VKConditionGroup {
    private final VKLogic logic;
    private final List<VKCondition> conditions;

    private VKConditionGroup(VKLogic logic) {
        this.logic = logic;
        this.conditions = new ArrayList<>();
    }

    public static VKConditionGroup and(VKCondition... items) {
        return new VKConditionGroup(VKLogic.AND).add(items);
    }

    public static VKConditionGroup or(VKCondition... items) {
        return new VKConditionGroup(VKLogic.OR).add(items);
    }

    private VKConditionGroup add(VKCondition... items) {
        if (items != null) {
            for (VKCondition item : items) {
                if (item != null) {
                    conditions.add(item);
                }
            }
        }
        return this;
    }

    public VKLogic getLogic() {
        return logic;
    }

    public List<VKCondition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }
}
