package dev.zymekoh.kohscore.gui.widget;

import dev.zymekoh.kohscore.gui.CoreTheme;
import dev.zymekoh.kohscore.gui.CoreDraw;
import dev.zymekoh.kohscore.optimize.AnimationGovernor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** A sprite-strip preview; GIF export lives in docs, no GIF decoder runs in the render loop. */
public final class AnimationPreviewRow extends CoreControl {
    private static final Identifier STRIP = Identifier.fromNamespaceAndPath("kohs_core", "textures/gui/previews/animations.png");
    public AnimationPreviewRow(int x, int y, int width) {
        super(x, y, width, 66, Component.translatable("kohs_core.animation.preview"));
        setDescription(Component.translatable("kohs_core.animation.preview.description"));
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        CoreDraw.clippedText(g, Minecraft.getInstance().font, getMessage(), getX() + 4, getY() + 3,
                Math.max(1, getWidth() - 8), CoreTheme.TEXT_MUTED);
        int cell = Math.max(16, Math.min(44, (getWidth() - 12) / 3));
        String[] kinds = {"fire", "water", "vegetation"};
        int frame = (int) (System.nanoTime() / 60_000_000L % 24);
        for (int i = 0; i < 3; i++) {
            int f = AnimationGovernor.textureEnabled(kinds[i]) ? frame : 0;
            g.blit(RenderPipelines.GUI_TEXTURED, STRIP, getX() + 4 + i * (cell + 2), getY() + 17,
                    f * 48, i * 48, cell, cell, 48, 48, 1152, 144);
        }
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out) { out.add(NarratedElementType.TITLE, getMessage()); }
}
