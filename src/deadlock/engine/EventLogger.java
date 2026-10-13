package deadlock.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EventLogger {
    private final List<String> events = new ArrayList<>();

    public void record(String event) {
        if (event == null || event.isBlank()) {
            throw new IllegalArgumentException("Event cannot be empty.");
        }
        events.add(event);
    }

    public void clear() {
        events.clear();
    }

    public List<String> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }
}