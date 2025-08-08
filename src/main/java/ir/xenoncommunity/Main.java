package ir.xenoncommunity;

import ir.xenoncommunity.config.Config;
import ir.xenoncommunity.scan.Connection;

public class Main {
    public static void main(String[] args) {
        new Connection("127.0.0.1", 20001, new Config(5000)).run();
    }
}