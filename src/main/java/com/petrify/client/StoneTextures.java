package com.petrify.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.petrify.PetrifyMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds statue textures from the original skin of an entity, so the face and clothes stay readable:
 * <ul>
 *   <li>{@link #get}: the skin turned to gray stone, with a cool tint and a bit of grain;</li>
 *   <li>{@link #getMaterial}: the same shading, but colored and patterned like a given block
 *       (the top texture of the block is tiled over the skin).</li>
 * </ul>
 * Results are cached.
 */
public final class StoneTextures {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    private StoneTextures() {}

    /** @return the gray stone texture for the given skin, or null if it is not available yet. */
    @Nullable
    public static ResourceLocation get(ResourceLocation source) {
        return build("stone|" + source, source, null, null);
    }

    /** @return the texture of the skin made of the given block, or null if it is not available yet. */
    @Nullable
    public static ResourceLocation getMaterial(ResourceLocation source, BlockState state, BlockPos pos) {
        return build("block|" + source + "|" + Block.getId(state) + "|" + pos.asLong(), source, state, pos);
    }

    @Nullable
    private static ResourceLocation build(String key, ResourceLocation source, @Nullable BlockState state, @Nullable BlockPos pos) {
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(key)) {
            return null;
        }

        NativeImage skin = null;
        NativeImage block = null;
        try {
            skin = downloadSkin(source);
            if (skin == null) {
                return null; // not uploaded yet (skin still downloading), try again next frame
            }

            if (state == null) {
                convertStone(skin);
            } else {
                int[] tint = {-1};
                block = loadBlockImage(state, pos, tint);
                if (block == null) {
                    FAILED.add(key);
                    return null;
                }
                convertMaterial(skin, block, tint[0]);
            }

            ResourceLocation id = new ResourceLocation(PetrifyMod.MOD_ID, "stone/" + Integer.toHexString(key.hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(skin));
            skin = null; // now owned by the registered texture
            CACHE.put(key, id);
            return id;
        } catch (IOException | RuntimeException e) {
            FAILED.add(key);
            return null;
        } finally {
            if (skin != null) {
                skin.close();
            }
            if (block != null) {
                block.close();
            }
        }
    }

    /** Copies the pixels of an already loaded texture back from the GPU. */
    @Nullable
    private static NativeImage downloadSkin(ResourceLocation source) {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        AbstractTexture texture = manager.getTexture(source);
        RenderSystem.bindTexture(texture.getId());

        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0) {
            return null;
        }
        if (width > 4096 || height > 4096) {
            throw new IllegalStateException("texture too large");
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        image.downloadTexture(0, false);
        return image;
    }

    /** Reads the top texture of a block straight from the resource packs (so custom packs work). */
    @Nullable
    private static NativeImage loadBlockImage(BlockState state, BlockPos pos, int[] tintOut) throws IOException {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);

        TextureAtlasSprite sprite;
        int tintIndex = -1;
        List<BakedQuad> quads = model.getQuads(state, Direction.UP, RandomSource.create());
        if (!quads.isEmpty()) {
            BakedQuad quad = quads.get(0);
            sprite = quad.getSprite();
            tintIndex = quad.getTintIndex();
        } else {
            sprite = model.getParticleIcon();
        }

        ResourceLocation name = sprite.contents().name();
        ResourceLocation file = new ResourceLocation(name.getNamespace(), "textures/" + name.getPath() + ".png");
        Optional<Resource> resource = mc.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            return null;
        }

        tintOut[0] = (tintIndex >= 0 && mc.level != null) ? mc.getBlockColors().getColor(state, mc.level, pos, tintIndex) : -1;
        try (InputStream in = resource.get().open()) {
            return NativeImage.read(in);
        }
    }

    private static void convertStone(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                int a = (abgr >>> 24) & 0xFF;
                float gray = luminance(abgr);
                float v = 62.0F + gray * 0.58F + grain(x, y);
                image.setPixelRGBA(x, y, pack(a, v + 5.0F, v, v - 2.0F));
            }
        }
    }

    /** Skin brightness decides the shading, the block texture decides color and pattern. */
    private static void convertMaterial(NativeImage skin, NativeImage block, int tint) {
        int blockWidth = block.getWidth();
        int blockHeight = Math.min(block.getHeight(), blockWidth); // animated textures are tall strips: use the first frame

        for (int y = 0; y < skin.getHeight(); y++) {
            for (int x = 0; x < skin.getWidth(); x++) {
                int abgr = skin.getPixelRGBA(x, y);
                int a = (abgr >>> 24) & 0xFF;
                float shade = 0.45F + 0.85F * (luminance(abgr) / 255.0F);

                int sample = block.getPixelRGBA(x % blockWidth, y % blockHeight);
                float r = sample & 0xFF;
                float g = (sample >>> 8) & 0xFF;
                float b = (sample >>> 16) & 0xFF;
                if (((sample >>> 24) & 0xFF) < 32) {
                    r = g = b = 128.0F;
                }
                if (tint != -1) {
                    r = r * ((tint >> 16) & 0xFF) / 255.0F;
                    g = g * ((tint >> 8) & 0xFF) / 255.0F;
                    b = b * (tint & 0xFF) / 255.0F;
                }

                float noise = grain(x, y);
                skin.setPixelRGBA(x, y, pack(a, b * shade + noise, g * shade + noise, r * shade + noise));
            }
        }
    }

    private static float luminance(int abgr) {
        int r = abgr & 0xFF;
        int g = (abgr >>> 8) & 0xFF;
        int b = (abgr >>> 16) & 0xFF;
        return 0.299F * r + 0.587F * g + 0.114F * b;
    }

    /** Packs to the ABGR order that NativeImage uses. */
    private static int pack(int alpha, float blue, float green, float red) {
        return (alpha << 24) | (clamp(blue) << 16) | (clamp(green) << 8) | clamp(red);
    }

    /** Deterministic noise in the range -4..+4, so the grain looks the same every time. */
    private static int grain(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFF) % 9 - 4;
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }
}
