package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.util.json.VKJsonProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VKJsonTest {
    static class Address {
        private String city;
        private int zip;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public int getZip() {
            return zip;
        }

        public void setZip(int zip) {
            this.zip = zip;
        }
    }

    static class Profile {
        private String name;
        private Address address;
        private List<Address> history;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        public List<Address> getHistory() {
            return history;
        }

        public void setHistory(List<Address> history) {
            this.history = history;
        }
    }

    static class MockProvider implements VKJsonProvider {
        @Override
        public String name() {
            return "mock";
        }

        @Override
        public String toJson(Object value) {
            return "\"mock-json\"";
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T fromJson(String json, Class<T> type) {
            if (type == String.class) {
                return (T) "mock-value";
            }
            throw new IllegalArgumentException("unsupported");
        }
    }

    @AfterEach
    void resetProvider() {
        Vostok.Util.resetDefaultJsonProvider();
    }

    @Test
    void testNestedJsonRoundTrip() {
        Address addr = new Address();
        addr.setCity("Shanghai");
        addr.setZip(200000);

        Address old = new Address();
        old.setCity("Beijing");
        old.setZip(100000);

        Profile p = new Profile();
        p.setName("Tom");
        p.setAddress(addr);
        p.setHistory(List.of(old));

        String json = Vostok.Util.toJson(p);
        Profile parsed = Vostok.Util.fromJson(json, Profile.class);

        assertNotNull(parsed);
        assertEquals("Tom", parsed.getName());
        assertNotNull(parsed.getAddress());
        assertEquals("Shanghai", parsed.getAddress().getCity());
        assertEquals(200000, parsed.getAddress().getZip());
        assertNotNull(parsed.getHistory());
        assertEquals(1, parsed.getHistory().size());
        assertEquals("Beijing", parsed.getHistory().get(0).getCity());
    }

    @Test
    void testSwitchProvider() {
        Vostok.Util.registerJsonProvider(new MockProvider());
        Vostok.Util.useJsonProvider("mock");

        assertEquals("mock", Vostok.Util.currentJsonProviderName());
        assertEquals("\"mock-json\"", Vostok.Util.toJson(new Object()));
        assertEquals("mock-value", Vostok.Util.fromJson("{}", String.class));
        assertTrue(Vostok.Util.jsonProviderNames().contains("mock"));
    }

    @Test
    void testUseUnknownProviderThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Vostok.Util.useJsonProvider("missing"));
        assertTrue(ex.getMessage().contains("JSON provider not found"));
    }

    @Test
    void testResetDefaultProvider() {
        Vostok.Util.registerJsonProvider(new MockProvider());
        Vostok.Util.useJsonProvider("mock");
        assertEquals("mock", Vostok.Util.currentJsonProviderName());

        Vostok.Util.resetDefaultJsonProvider();
        assertEquals("builtin", Vostok.Util.currentJsonProviderName());
    }
}
