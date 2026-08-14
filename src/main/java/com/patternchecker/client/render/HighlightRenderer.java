package com.patternchecker.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.patternchecker.PatternCheckerMod;
import com.patternchecker.client.PatternCheckClient;
import com.patternchecker.network.HighlightPayload;
import com.patternchecker.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * See-through translucent boxes around highlighted pattern providers and their
 * wireless connection targets. Uses the same rendering pattern as
 * AE2NetworkAnalyzer (POSITION_COLOR quads, no depth test), which is proven to
 * display correctly in this Minecraft version.
 */
@EventBusSubscriber(modid = PatternCheckerMod.MODID, value = Dist.CLIENT)
public final class HighlightRenderer {

    private static final long HIGHLIGHT_MS = 15_000L;

    private static final RenderType FILLED_BOX = RenderType.create(
            "patternchecker_filled_box",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setCullState(RenderStateShard.NO_CULL)
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    private HighlightRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        HighlightPayload polled = NetworkHandler.pollHighlights();
        if (polled != null) {
            PatternCheckClient.setHighlights(polled);
        }
        PatternCheckClient.HighlightState state = PatternCheckClient.getHighlights();
        if (System.currentTimeMillis() - state.receivedAt() > HIGHLIGHT_MS) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        String dimension = level.dimension().location().toString();
        List<HighlightPayload.HighlightData> highlights = state.payload().highlights();
        if (highlights.isEmpty()) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        Vec3 camera = event.getCamera().getPosition();
        // Like vanilla structure block boxes: the level renderer already keeps
        // the camera rotation in the model view; we only shift to camera
        // relative space. Adding rotation again would double-transform it.
        pose.translate(-camera.x, -camera.y, -camera.z);

        // Force see-through rendering regardless of render type state.
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(FILLED_BOX);
        float hueBase = (System.currentTimeMillis() % 1200L) / 1200f;
        for (HighlightPayload.HighlightData data : highlights) {
            if (!data.dimension().equals(dimension)) {
                continue;
            }
            addWireframe(consumer, pose.last().pose(), data.pos(), hueBase);
        }
        buffers.endBatch();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void addWireframe(VertexConsumer consumer, Matrix4f matrix, BlockPos pos, float hueBase) {
        // Slightly larger than the block so the frame never z-fights with it.
        final float inset = 0.01f;
        final float thickness = 0.045f;
        float x0 = pos.getX() - inset;
        float y0 = pos.getY() - inset;
        float z0 = pos.getZ() - inset;
        float x1 = x0 + 1.0f + inset * 2;
        float y1 = y0 + 1.0f + inset * 2;
        float z1 = z0 + 1.0f + inset * 2;
        float t = thickness;

        // 4 vertical edges.
        edge(consumer, matrix, 0, hueBase, x0, y0, z0, x0 + t, y1, z0 + t);
        edge(consumer, matrix, 1, hueBase, x1 - t, y0, z0, x1, y1, z0 + t);
        edge(consumer, matrix, 2, hueBase, x0, y0, z1 - t, x0 + t, y1, z1);
        edge(consumer, matrix, 3, hueBase, x1 - t, y0, z1 - t, x1, y1, z1);
        // 4 bottom edges.
        edge(consumer, matrix, 4, hueBase, x0, y0, z0, x1, y0 + t, z0 + t);
        edge(consumer, matrix, 5, hueBase, x0, y0, z1 - t, x1, y0 + t, z1);
        edge(consumer, matrix, 6, hueBase, x0, y0, z0, x0 + t, y0 + t, z1);
        edge(consumer, matrix, 7, hueBase, x1 - t, y0, z0, x1, y0 + t, z1);
        // 4 top edges.
        edge(consumer, matrix, 8, hueBase, x0, y1 - t, z0, x1, y1, z0 + t);
        edge(consumer, matrix, 9, hueBase, x0, y1 - t, z1 - t, x1, y1, z1);
        edge(consumer, matrix, 10, hueBase, x0, y1 - t, z0, x0 + t, y1, z1);
        edge(consumer, matrix, 11, hueBase, x1 - t, y1 - t, z0, x1, y1, z1);
    }

    private static void edge(VertexConsumer consumer, Matrix4f matrix, int index, float hueBase,
                             float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        // Split the edge along its long axis so each edge shows a flowing
        // rainbow gradient instead of a single flat colour.
        float lenX = maxX - minX;
        float lenY = maxY - minY;
        float lenZ = maxZ - minZ;
        int axis = lenX >= lenY && lenX >= lenZ ? 0 : (lenY >= lenZ ? 1 : 2);
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            float t0 = (float) i / segments;
            float t1 = (float) (i + 1) / segments;
            float hue = (hueBase + index / 12.0f + t0) % 1.0f;
            float[] rgb = hsvToRgb(hue, 0.95f, 1.0f);
            switch (axis) {
                case 0 -> box(consumer, matrix,
                        minX + lenX * t0, minY, minZ,
                        minX + lenX * t1, maxY, maxZ,
                        rgb[0], rgb[1], rgb[2], 0.9f);
                case 1 -> box(consumer, matrix,
                        minX, minY + lenY * t0, minZ,
                        maxX, minY + lenY * t1, maxZ,
                        rgb[0], rgb[1], rgb[2], 0.9f);
                default -> box(consumer, matrix,
                        minX, minY, minZ + lenZ * t0,
                        maxX, maxY, minZ + lenZ * t1,
                        rgb[0], rgb[1], rgb[2], 0.9f);
            }
        }
    }

    private static float[] hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6.0f);
        float f = h * 6.0f - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);
        return switch (i % 6) {
            case 0 -> new float[]{v, t, p};
            case 1 -> new float[]{q, v, p};
            case 2 -> new float[]{p, v, t};
            case 3 -> new float[]{p, q, v};
            case 4 -> new float[]{t, p, v};
            default -> new float[]{v, p, q};
        };
    }

    private static void box(VertexConsumer consumer, Matrix4f matrix,
                            float minX, float minY, float minZ,
                            float maxX, float maxY, float maxZ,
                            float r, float g, float b, float a) {
        quad(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a); // bottom
        quad(consumer, matrix, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a); // top
        quad(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a); // north
        quad(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ, r, g, b, a); // south
        quad(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, r, g, b, a); // west
        quad(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a); // east
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        consumer.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a);
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
    }
}
