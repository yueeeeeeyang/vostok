package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.common.json.VKJson;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

        String json = VKJson.toJson(p);
        Profile parsed = VKJson.fromJson(json, Profile.class);

        assertNotNull(parsed);
        assertEquals("Tom", parsed.getName());
        assertNotNull(parsed.getAddress());
        assertEquals("Shanghai", parsed.getAddress().getCity());
        assertEquals(200000, parsed.getAddress().getZip());
        assertNotNull(parsed.getHistory());
        assertEquals(1, parsed.getHistory().size());
        assertEquals("Beijing", parsed.getHistory().get(0).getCity());
    }
}
