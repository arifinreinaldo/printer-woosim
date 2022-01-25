package net.simplr.woosimdp230l;

public class Device {
    public String getName() {
        return name;
    }

    String name;
    String address;

    public Device(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getAddress() {
        return address;
    }
}
