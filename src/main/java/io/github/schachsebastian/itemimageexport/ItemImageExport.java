package io.github.schachsebastian.itemimageexport;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod(value = ItemImageExport.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ItemImageExport.MODID, value = Dist.CLIENT)
public class ItemImageExport {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "item_image_export";
    private static final int ICON_SIZE = 16;
    private static final int SCALE = 4;
    private static final int CAPTURE_W = ICON_SIZE * SCALE;
    private static final int CAPTURE_H = ICON_SIZE * SCALE;
    private static final int RENDER_X = 10;
    private static final int RENDER_Y = 10;
    private static final int FRAMES_PER_ITEM = 1;
    private static final String OUTPUT_DIRECTORY = "item_images";
    private static Iterator<Item> itemIterator;
    private static Item currentItem;
    private static boolean running = false;
    private static int frameIndex = 0;
    private static List<NativeImage> frames;
    private static CommandSourceStack source;
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gen-item-images").executes(context -> {
            source = context.getSource();
            source.sendSystemMessage(Component.literal("Starting Item image generation..."));
            itemIterator = BuiltInRegistries.ITEM.iterator();
            currentItem = itemIterator.next();
            frames = new ArrayList<>();
            running = true;
            return 1;
        }));
    }
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!running) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        if (!itemIterator.hasNext()) {
            running = false;
            currentItem = null;
            source.sendSystemMessage(Component.literal("Image generation complete!"));
            ChatUtil.sendFolderLink(mc.gameDirectory.toPath().resolve(OUTPUT_DIRECTORY).toAbsolutePath().toString(), OUTPUT_DIRECTORY);
            RenderSystem.setShaderGameTime(
                    Minecraft.getInstance().level.getGameTime(),
                    Minecraft.getInstance().getFrameTimeNs());
            return;
        }

        if (frameIndex >= FRAMES_PER_ITEM) {
            currentItem = itemIterator.next();
            frames = new ArrayList<>();
            frameIndex = 0;
        }
        // Draw Background
        graphics.fill(RENDER_X, RENDER_Y, RENDER_X + CAPTURE_W, RENDER_Y + CAPTURE_H, 0xFF8B8B8B);
        // Render Item
        // Determine scale to fit into CAPTURE_W x CAPTURE_H
        float scaleX = (float) CAPTURE_W / ICON_SIZE;
        float scaleY = (float) CAPTURE_H / ICON_SIZE;
        float finalScale = Math.min(scaleX, scaleY); // keep aspect ratio
        graphics.pose().pushPose();
        // Center the item in the capture area
        float offsetX = RENDER_X + (CAPTURE_W - ICON_SIZE * finalScale) / 2f;
        float offsetY = RENDER_Y + (CAPTURE_H - ICON_SIZE * finalScale) / 2f;
        graphics.pose().translate(offsetX, offsetY, 200);
        graphics.pose().scale(finalScale, finalScale, 1);
        RenderSystem.setShaderGameTime(frameIndex, 0);
        graphics.renderItem(new ItemStack(currentItem), 0, 0);
        graphics.pose().popPose();
        // Flush and move to next stage (Capture will happen NEXT frame)
        graphics.flush();
        frameIndex++;
    }
    @SubscribeEvent
    public static void onPostRender(RenderGuiEvent.Post event) throws IOException {
        if (currentItem == null) return;
        frames.add(captureItem());
        if (frameIndex >= FRAMES_PER_ITEM) {
            finalizeItem();
        }
    }
    private static NativeImage captureItem() {
        Minecraft mc = Minecraft.getInstance();
        // Use the actual height of the buffer we downloaded
        int bufferW = mc.getMainRenderTarget().width;
        int bufferH = mc.getMainRenderTarget().height;
        double guiScale = mc.getWindow().getGuiScale();
        // 1. Convert GUI coordinates to Buffer coordinates
        int pixelX = (int) (RENDER_X * guiScale);
        int pixelW = (int) (CAPTURE_W * guiScale);
        int pixelH = (int) (CAPTURE_H * guiScale);
        // 2. The Y-Flip:
        // GUI (0,0) is Top-Left.
        // OpenGL/Framebuffer (0,0) is Bottom-Left.
        // We need the TOP of our capture area in FB-space.
        int pixelY = (int) (bufferH - ((RENDER_Y + CAPTURE_H) * guiScale));
        // 3. Download the full screen buffer
        NativeImage img = new NativeImage(bufferW, bufferH, false);
        RenderSystem.bindTexture(mc.getMainRenderTarget().getColorTextureId());
        img.downloadTexture(0, false);
        // 4. Extract the region
        NativeImage cropped = new NativeImage(pixelW, pixelH, false);
        for (int y = 0; y < pixelH; y++) {
            for (int x = 0; x < pixelW; x++) {
                // Bounds check to prevent crashes if RENDER_X/Y is off-screen
                int srcX = pixelX + x;
                int srcY = pixelY + y;
                if (srcX >= 0 && srcX < bufferW && srcY >= 0 && srcY < bufferH) {
                    int color = img.getPixelRGBA(srcX, srcY);
                    // Flip Y back for the PNG file (Standard image format is Top-Down)
                    cropped.setPixelRGBA(x, (pixelH - 1) - y, color);
                }
            }
        }
        img.close();
        return cropped;
    }
    private static void finalizeItem() throws IOException {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(currentItem);
        Path path = Minecraft.getInstance().gameDirectory.toPath()
                                                         .resolve(OUTPUT_DIRECTORY)
                                                         .resolve(id.getNamespace())
                                                         .resolve(id.getPath() + ".png");
        Files.createDirectories(path.getParent());
        if (ImageUtil.isAnimated(frames)) {
            // TODO create animated image
            frames.getFirst().writeToFile(path);
        } else {
            frames.getFirst().writeToFile(path);
        }
        for (NativeImage img : frames) {
            img.close();
        }
    }
}
