package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;
import yueyang.vostok.terminal.event.VKKey;
import yueyang.vostok.terminal.event.VKKeyEvent;

import java.util.ArrayList;
import java.util.List;

public final class VKListView extends VKView {
    private final List<String> items = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private int viewportHeight = 12;
    private boolean focused;
    private boolean followTail = true;

    public VKListView items(List<String> items) {
        this.items.clear();
        if (items != null) {
            for (String item : items) {
                this.items.add(item == null ? "" : item);
            }
        }
        clampIndexes();
        if (followTail) {
            scrollToEnd();
        }
        return this;
    }

    public VKListView selectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
        clampIndexes();
        return this;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public VKListView viewportHeight(int viewportHeight) {
        this.viewportHeight = Math.max(1, viewportHeight);
        clampIndexes();
        return this;
    }

    public int viewportHeight() {
        return viewportHeight;
    }

    public int scrollOffset() {
        return scrollOffset;
    }

    public VKListView scrollOffset(int scrollOffset) {
        this.scrollOffset = Math.max(0, scrollOffset);
        clampIndexes();
        return this;
    }

    public VKListView followTail(boolean followTail) {
        this.followTail = followTail;
        if (followTail) {
            scrollToEnd();
        }
        return this;
    }

    public boolean isFollowTail() {
        return followTail;
    }

    public VKListView scrollToEnd() {
        int max = Math.max(0, items.size() - viewportHeight);
        this.scrollOffset = max;
        return this;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void focused(boolean focused) {
        this.focused = focused;
    }

    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean onKey(VKKeyEvent event) {
        if (!focused || event == null) {
            return false;
        }
        switch (event.key()) {
            case UP -> {
                followTail = false;
                if (selectedIndex >= 0) {
                    if (selectedIndex > 0) {
                        selectedIndex--;
                        ensureSelectedVisible();
                        return true;
                    }
                    return false;
                }
                return moveScroll(-1);
            }
            case DOWN -> {
                if (selectedIndex >= 0) {
                    if (selectedIndex < items.size() - 1) {
                        selectedIndex++;
                        ensureSelectedVisible();
                        if (selectedIndex == items.size() - 1) {
                            followTail = true;
                        }
                        return true;
                    }
                    return false;
                }
                boolean changed = moveScroll(1);
                if (scrollOffset >= Math.max(0, items.size() - viewportHeight)) {
                    followTail = true;
                }
                return changed;
            }
            case PAGE_UP -> {
                followTail = false;
                return moveScroll(-viewportHeight);
            }
            case PAGE_DOWN -> {
                boolean changed = moveScroll(viewportHeight);
                if (scrollOffset >= Math.max(0, items.size() - viewportHeight)) {
                    followTail = true;
                }
                return changed;
            }
            case HOME -> {
                followTail = false;
                if (selectedIndex >= 0) {
                    boolean changed = selectedIndex != 0;
                    selectedIndex = 0;
                    ensureSelectedVisible();
                    return changed;
                }
                if (scrollOffset != 0) {
                    scrollOffset = 0;
                    return true;
                }
                return false;
            }
            case END -> {
                if (selectedIndex >= 0) {
                    int target = Math.max(0, items.size() - 1);
                    boolean changed = selectedIndex != target;
                    selectedIndex = target;
                    ensureSelectedVisible();
                    followTail = true;
                    return changed;
                }
                int max = Math.max(0, items.size() - viewportHeight);
                if (scrollOffset != max) {
                    scrollOffset = max;
                    followTail = true;
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        if (items.isEmpty()) {
            return List.of(ctx.styleMuted("(empty)"));
        }
        int limit = Math.max(1, viewportHeight);
        int maxOffset = Math.max(0, items.size() - limit);
        scrollOffset = Math.min(scrollOffset, maxOffset);

        int end = Math.min(items.size(), scrollOffset + limit);
        List<String> out = new ArrayList<>();
        for (int i = scrollOffset; i < end; i++) {
            boolean selected = i == selectedIndex;
            String prefix = selected ? "> " : "  ";
            String line = ctx.fit(prefix + items.get(i));
            if (selected) {
                out.add(ctx.styleSuccess(line));
            } else {
                out.add(ctx.styleText(line));
            }
        }

        if (out.isEmpty()) {
            out.add(ctx.styleMuted("(empty)"));
        }
        if (focused) {
            String marker = "[focus:list]";
            out.add(ctx.styleMuted(ctx.fit(marker)));
        }
        return out;
    }

    private boolean moveScroll(int delta) {
        int max = Math.max(0, items.size() - viewportHeight);
        int next = Math.max(0, Math.min(max, scrollOffset + delta));
        if (next == scrollOffset) {
            return false;
        }
        scrollOffset = next;
        return true;
    }

    private void ensureSelectedVisible() {
        if (selectedIndex < 0) {
            return;
        }
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
            return;
        }
        int tail = scrollOffset + viewportHeight - 1;
        if (selectedIndex > tail) {
            scrollOffset = selectedIndex - viewportHeight + 1;
        }
        clampIndexes();
    }

    private void clampIndexes() {
        if (items.isEmpty()) {
            selectedIndex = -1;
            scrollOffset = 0;
            return;
        }
        if (selectedIndex >= items.size()) {
            selectedIndex = items.size() - 1;
        }
        if (selectedIndex < -1) {
            selectedIndex = -1;
        }
        int max = Math.max(0, items.size() - viewportHeight);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset));
    }
}
