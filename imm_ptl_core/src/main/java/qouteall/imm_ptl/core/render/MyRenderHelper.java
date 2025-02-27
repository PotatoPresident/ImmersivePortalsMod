package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.PortalLike;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.my_util.SignalBiArged;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_FRONT;
import static org.lwjgl.opengl.GL11.GL_RED;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glReadPixels;

public class MyRenderHelper {
    
    public static final Minecraft client = Minecraft.getInstance();
    
    public static final SignalBiArged<ResourceManager, Consumer<ShaderInstance>> loadShaderSignal =
        new SignalBiArged<>();
    
    public static void init() {
        
        loadShaderSignal.connect((resourceManager, resultConsumer) -> {
            try {
                DrawFbInAreaShader shader = new DrawFbInAreaShader(
                    getResourceFactory(resourceManager),
                    "portal_draw_fb_in_area",
                    DefaultVertexFormat.POSITION_COLOR
                );
                resultConsumer.accept(shader);
                drawFbInAreaShader = shader;
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        
        loadShaderSignal.connect((resourceManager, resultConsumer) -> {
            try {
                ShaderInstance shader = new ShaderInstance(
                    getResourceFactory(resourceManager),
                    "portal_area",
                    DefaultVertexFormat.POSITION_COLOR
                );
                resultConsumer.accept(shader);
                portalAreaShader = shader;
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        
        loadShaderSignal.connect((resourceManager, resultConsumer) -> {
            try {
                ShaderInstance shader = new ShaderInstance(
                    getResourceFactory(resourceManager),
                    "blit_screen_noblend",
                    DefaultVertexFormat.POSITION_TEX_COLOR
                );
                resultConsumer.accept(shader);
                blitScreenNoBlendShader = shader;
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private static ResourceProvider getResourceFactory(ResourceManager resourceManager) {
        ResourceProvider resourceFactory = new ResourceProvider() {
            @Override
            public Resource getResource(ResourceLocation id) throws IOException {
                ResourceLocation corrected = new ResourceLocation("immersive_portals", id.getPath());
                return resourceManager.getResource(corrected);
            }
        };
        return resourceFactory;
    }
    
    public static class DrawFbInAreaShader extends ShaderInstance {
        
        public final Uniform uniformW;
        public final Uniform uniformH;
        
        public DrawFbInAreaShader(
            ResourceProvider factory, String name, VertexFormat format
        ) throws IOException {
            super(factory, name, format);
            
            uniformW = getUniform("w");
            uniformH = getUniform("h");
        }
        
        void loadWidthHeight(int w, int h) {
            uniformW.set((float) w);
            uniformH.set((float) h);
        }
    }
    
    public static DrawFbInAreaShader drawFbInAreaShader;
    public static ShaderInstance portalAreaShader;
    public static ShaderInstance blitScreenNoBlendShader;
    
    public static void drawPortalAreaWithFramebuffer(
        PortalLike portal,
        RenderTarget textureProvider,
        Matrix4f modelViewMatrix,
        Matrix4f projectionMatrix
    ) {
        
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._viewport(0, 0, textureProvider.width, textureProvider.height);
        
        DrawFbInAreaShader shader = drawFbInAreaShader;
        shader.setSampler("DiffuseSampler", textureProvider.getColorTextureId());
        shader.loadWidthHeight(textureProvider.width, textureProvider.height);
        
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(modelViewMatrix);
        }
        
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(projectionMatrix);
        }
        
        shader.apply();
        
        Tesselator tessellator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tessellator.getBuilder();
        
        ViewAreaRenderer.buildPortalViewAreaTrianglesBuffer(
            Vec3.ZERO,//fog
            portal,
            bufferBuilder,
            CHelper.getCurrentCameraPos(),
            RenderStates.tickDelta
        );
        
        BufferUploader._endInternal(bufferBuilder);
        
        // wrong name. unbind
        shader.clear();
    }
    
    public static void renderScreenTriangle() {
        renderScreenTriangle(255, 255, 255, 255);
    }
    
    public static void renderScreenTriangle(Vec3 color) {
        renderScreenTriangle(
            (int) (color.x * 255),
            (int) (color.y * 255),
            (int) (color.z * 255),
            255
        );
    }
    
    public static void renderScreenTriangle(int r, int g, int b, int a) {
        ShaderInstance shader = GameRenderer.getPositionColorShader();
        Validate.notNull(shader);
        
        Matrix4f identityMatrix = new Matrix4f();
        identityMatrix.setIdentity();
        
        shader.MODEL_VIEW_MATRIX.set(identityMatrix);
        shader.PROJECTION_MATRIX.set(identityMatrix);
        
        shader.apply();
        
        RenderSystem.disableTexture();
        
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        
        bufferBuilder.vertex(1, -1, 0).color(r, g, b, a)
            .endVertex();
        bufferBuilder.vertex(1, 1, 0).color(r, g, b, a)
            .endVertex();
        bufferBuilder.vertex(-1, 1, 0).color(r, g, b, a)
            .endVertex();
        
        bufferBuilder.vertex(-1, 1, 0).color(r, g, b, a)
            .endVertex();
        bufferBuilder.vertex(-1, -1, 0).color(r, g, b, a)
            .endVertex();
        bufferBuilder.vertex(1, -1, 0).color(r, g, b, a)
            .endVertex();
        
        bufferBuilder.end();
        
        BufferUploader._endInternal(bufferBuilder);
        
        // wrong name. unbind
        shader.clear();
        
        RenderSystem.enableTexture();
    }
    
    /**
     * {@link RenderTarget#blitToScreen(int, int)}
     */
    public static void drawScreenFrameBuffer(
        RenderTarget textureProvider,
        boolean doUseAlphaBlend,
        boolean doEnableModifyAlpha
    ) {
        float right = (float) textureProvider.viewWidth;
        float up = (float) textureProvider.viewHeight;
        float left = 0;
        float bottom = 0;
        
        int viewportWidth = textureProvider.viewWidth;
        int viewportHeight = textureProvider.viewHeight;
        
        drawFramebufferWithViewport(
            textureProvider, doUseAlphaBlend, doEnableModifyAlpha,
            left, (double) right, bottom, (double) up,
            viewportWidth, viewportHeight
        );
    }
    
    public static void drawFramebuffer(
        RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        float left, double right, float bottom, double up
    ) {
        drawFramebufferWithViewport(
            textureProvider,
            doUseAlphaBlend, doEnableModifyAlpha,
            left, right, bottom, up,
            client.getWindow().getWidth(),
            client.getWindow().getHeight()
        );
    }
    
    public static void drawFramebufferWithViewport(
        RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        float left, double right, float bottom, double up,
        int viewportWidth, int viewportHeight
    ) {
        CHelper.checkGlError();
        
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._viewport(0, 0, viewportWidth, viewportHeight);
        
        if (doUseAlphaBlend) {
            RenderSystem.enableBlend();
        }
        else {
            RenderSystem.disableBlend();
        }
        
        if (doEnableModifyAlpha) {
            GlStateManager._colorMask(true, true, true, true);
        }
        else {
            GlStateManager._colorMask(true, true, true, false);
        }
        
        ShaderInstance shader = doUseAlphaBlend ? client.gameRenderer.blitShader : blitScreenNoBlendShader;
        
        shader.setSampler("DiffuseSampler", textureProvider.getColorTextureId());
        
        Matrix4f projectionMatrix = Matrix4f.orthographic(
            (float) viewportWidth, (float) (-viewportHeight), 1000.0F, 3000.0F);
        
        shader.MODEL_VIEW_MATRIX.set(Matrix4f.createTranslateMatrix(0.0F, 0.0F, -2000.0F));
        
        shader.PROJECTION_MATRIX.set(projectionMatrix);
        
        shader.apply();
        
        float textureXScale = (float) viewportWidth / (float) textureProvider.width;
        float textureYScale = (float) viewportHeight / (float) textureProvider.height;
        
        Tesselator tessellator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tessellator.getBuilder();
        
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        
        bufferBuilder.vertex(left, up, 0.0D)
            .uv(0.0F, 0.0F)
            .color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(right, up, 0.0D)
            .uv(textureXScale, 0.0F)
            .color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(right, bottom, 0.0D)
            .uv(textureXScale, textureYScale)
            .color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(left, bottom, 0.0D)
            .uv(0.0F, textureYScale)
            .color(255, 255, 255, 255).endVertex();
        
        bufferBuilder.end();
        BufferUploader._endInternal(bufferBuilder);
        
        // unbind
        shader.clear();
        
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        
        RenderSystem.enableBlend();
        
        CHelper.checkGlError();
    }
    
    // it will remove the light sections that are marked to be removed
    // if not, light data will cause minor memory leak
    // and wrongly remove the light data when the chunks get reloaded to client
    public static void earlyUpdateLight() {
        if (!ClientWorldLoader.getIsInitialized()) {
            return;
        }
        
        ClientWorldLoader.getClientWorlds().forEach(world -> {
            if (world != Minecraft.getInstance().level) {
                int updateNum = world.getChunkSource().getLightEngine().runUpdates(
                    1000, true, true
                );
            }
        });
    }
    
    public static void applyMirrorFaceCulling() {
        glCullFace(GL_FRONT);
    }
    
    public static void recoverFaceCulling() {
        glCullFace(GL_BACK);
    }
    
    public static void clearAlphaTo1(RenderTarget mcFrameBuffer) {
        mcFrameBuffer.bindWrite(true);
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.clearColor(0, 0, 0, 1.0f);
        RenderSystem.clear(GL_COLOR_BUFFER_BIT, true);
        RenderSystem.colorMask(true, true, true, true);
    }
    
    public static void restoreViewPort() {
        Minecraft client = Minecraft.getInstance();
        GlStateManager._viewport(
            0,
            0,
            client.getWindow().getWidth(),
            client.getWindow().getHeight()
        );
    }
    
    public static float transformFogDistance(float value) {
        if (IPGlobal.debugDisableFog) {
            return value * 23333;
        }
        
        // sodium use world position to calculate fog color
        // vanilla use transformed position to calculate fog
        if (SodiumInterface.invoker.isSodiumPresent()) {
            return value;
        }
        
        if (PortalRendering.isRendering()) {
            PortalLike renderingPortal = PortalRendering.getRenderingPortal();
            if (PortalRenderer.shouldApplyScaleToModelView(renderingPortal)) {
                double scaling = renderingPortal.getScale();
                float result = (float) (value / scaling);
                if (scaling > 10) {
                    result *= 10;
                }
                return result;
            }
        }
        return value;
    }
    
    private static boolean debugEnabled = false;
    
    public static void debugFramebufferDepth() {
        if (!debugEnabled) {
            return;
        }
        debugEnabled = false;
        
        int width = client.getMainRenderTarget().width;
        int height = client.getMainRenderTarget().height;
        
        
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        
        FloatBuffer floatBuffer = directBuffer.asFloatBuffer();
        
        glReadPixels(
            0, 0, width, height,
            GL_DEPTH_COMPONENT, GL_FLOAT, floatBuffer
        );
        
        float[] data = new float[width * height];
        
        floatBuffer.rewind();
        floatBuffer.get(data);
        
        float maxValue = (float) IntStream.range(0, data.length)
            .mapToDouble(i -> data[i]).max().getAsDouble();
        float minValue = (float) IntStream.range(0, data.length)
            .mapToDouble(i -> data[i]).min().getAsDouble();
        
        byte[] grayData = new byte[width * height];
        for (int i = 0; i < data.length; i++) {
            float datum = data[i];
            
            datum = (datum - minValue) / (maxValue - minValue);
            
            grayData[i] = (byte) (datum * 255);
        }
        
        BufferedImage bufferedImage =
            new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        bufferedImage.setData(
            Raster.createRaster(
                bufferedImage.getSampleModel(),
                new DataBufferByte(grayData, grayData.length), new Point()
            )
        );
        
        System.out.println("oops");
    }
    
    public static void debugFramebufferColorRed() {
        if (!debugEnabled) {
            return;
        }
        debugEnabled = false;
        
        int width = client.getMainRenderTarget().width;
        int height = client.getMainRenderTarget().height;
        
        
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        
        FloatBuffer floatBuffer = directBuffer.asFloatBuffer();
        
        glReadPixels(
            0, 0, width, height,
            GL_RED, GL_FLOAT, floatBuffer
        );
        
        float[] data = new float[width * height];
        
        floatBuffer.rewind();
        floatBuffer.get(data);
        
        float maxValue = (float) IntStream.range(0, data.length)
            .mapToDouble(i -> data[i]).max().getAsDouble();
        float minValue = (float) IntStream.range(0, data.length)
            .mapToDouble(i -> data[i]).min().getAsDouble();
        
        byte[] grayData = new byte[width * height];
        for (int i = 0; i < data.length; i++) {
            float datum = data[i];
            
            datum = (datum - minValue) / (maxValue - minValue);
            
            grayData[i] = (byte) (datum * 255);
        }
        
        BufferedImage bufferedImage =
            new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        bufferedImage.setData(
            Raster.createRaster(
                bufferedImage.getSampleModel(),
                new DataBufferByte(grayData, grayData.length), new Point()
            )
        );
        
        System.out.println("oops");
    }
}
