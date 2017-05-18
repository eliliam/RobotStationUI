package org.robotstation.configs;

import java.awt.Image;
import java.awt.image.BufferedImage;

public class Resource {
    private final Image icon;
    private final String name;

    public Resource(Image icon, String name) {
        this.icon = icon;
        this.name = name;
    }

    public Image getIcon() {
        return this.icon;
    }

    public BufferedImage getBuffered() {
        return Resources.ImageToBuffered(this.icon);
    }

    public String getName() {
        return this.name;
    }
}