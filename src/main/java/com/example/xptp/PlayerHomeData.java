package com.example.xptp;

import java.util.HashMap;
import java.util.Map;

public class PlayerHomeData {
    private Map<String, TeleportLocation> homes = new HashMap<>();
    private int extraHomeSlots = 0;

    public PlayerHomeData() {}

    public PlayerHomeData(Map<String, TeleportLocation> homes, int extraHomeSlots) {
        this.homes = homes;
        this.extraHomeSlots = extraHomeSlots;
    }

    public Map<String, TeleportLocation> getHomes() {
        return homes;
    }

    public void setHomes(Map<String, TeleportLocation> homes) {
        this.homes = homes;
    }

    public int getExtraHomeSlots() {
        return extraHomeSlots;
    }

    public void setExtraHomeSlots(int extraHomeSlots) {
        this.extraHomeSlots = extraHomeSlots;
    }
}
