package com.LegMeat.rbo.Backend;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

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

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof KeyFrame)) {
            return false;
        } else {
            BufferedImage frameOne = this.getKeyframeData();
            BufferedImage frameTwo = ((KeyFrame) obj).getKeyframeData();
            if (frameOne.getWidth() == frameTwo.getWidth() && frameOne.getHeight() == frameTwo.getHeight()) {
                for (int x = 0; x < frameOne.getWidth(); x++) {
                    for (int y = 0; y < frameOne.getHeight(); y++) {
                        if (frameOne.getRGB(x, y) != frameTwo.getRGB(x, y)) {
                            return false;
                        }
                    }
                }
            } else {
                return false;
            }
            return true;
        }
    }

    @Override
    public String toString() {
        return "KeyFrame: " + id + " timestamp: " + timestamp + " Buffered Image: " + keyframeData;
    }

    public KeyFrame(Double timestamp, BufferedImage keyframeData, long id) {
        this.timestamp = timestamp;
        this.keyframeData = keyframeData;
        this.id = id;
    }
}
