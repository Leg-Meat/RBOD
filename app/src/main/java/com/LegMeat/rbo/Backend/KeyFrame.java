package com.LegMeat.rbo.Backend;

import java.awt.image.BufferedImage;

public class KeyFrame {
    private long id;
    private Double timestamp;
    private BufferedImage keyframeData;

    public long getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Double timestamp) {
        this.timestamp = timestamp;
    }

    public BufferedImage getKeyframeData() {
        return keyframeData;
    }

    public void setKeyframeData(BufferedImage keyframeData) {
        this.keyframeData = keyframeData;
    }

    public KeyFrame(Double timestamp, BufferedImage keyframeData, long id) {
        this.timestamp = timestamp;
        this.keyframeData = keyframeData;
        this.id = id;
    }
}
