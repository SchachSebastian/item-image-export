package io.github.schachsebastian.itemimageexport;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.List;

public class ImageUtil {
    public static boolean isAnimated(List<NativeImage> frames) {
        NativeImage base = frames.getFirst();
        if(frames.size() < 2) return false;

        for (int i = 1; i < frames.size(); i++) {
            if (!imagesEqual(base, frames.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean imagesEqual(NativeImage a, NativeImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
            return false;

        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getPixelRGBA(x, y) != b.getPixelRGBA(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }
}
