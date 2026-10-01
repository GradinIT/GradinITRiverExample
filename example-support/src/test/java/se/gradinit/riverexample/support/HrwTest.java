package se.gradinit.riverexample.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class HrwTest {
    @Test
    void sameKeyStaysOnTheSameNode() {
        List<String> nodes = List.of("a", "b", "c");
        String first = Hrw.choose("alice", nodes, id -> id);
        String second = Hrw.choose("alice", nodes, id -> id);
        assertEquals(first, second);
    }

    @Test
    void weightIsStable() {
        assertEquals(Hrw.weight("alice", "backend-1"), Hrw.weight("alice", "backend-1"));
    }
}
