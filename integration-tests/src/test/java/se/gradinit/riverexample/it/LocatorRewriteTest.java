package se.gradinit.riverexample.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LocatorRewriteTest {
    @Test
    void rewritesWildcardHosts() {
        assertEquals("jini://127.0.0.1:4160", OrderPlatformIT.usableLocator("jini://0.0.0.0:4160"));
        assertEquals("jini://127.0.0.1:4160", OrderPlatformIT.usableLocator("jini://*:4160"));
        assertEquals("jini://127.0.0.1:4160", OrderPlatformIT.usableLocator("jini://127.0.0.1:4160."));
        assertEquals("jini://localhost:4160", OrderPlatformIT.usableLocator("jini://localhost:4160"));
    }
}