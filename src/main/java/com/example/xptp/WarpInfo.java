package com.example.xptp;

public class WarpInfo {
    private TeleportLocation location;
    private String creatorUuid; // Null if created by console/admin

    public WarpInfo() {}

    public WarpInfo(TeleportLocation location, String creatorUuid) {
        this.location = location;
        this.creatorUuid = creatorUuid;
    }

    public TeleportLocation getLocation() {
        return location;
    }

    public void setLocation(TeleportLocation location) {
        this.location = location;
    }

    public String getCreatorUuid() {
        return creatorUuid;
    }

    public void setCreatorUuid(String creatorUuid) {
        this.creatorUuid = creatorUuid;
    }
}
