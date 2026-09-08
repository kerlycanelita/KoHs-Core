package dev.zymekoh.kohscore.optimize;

import net.minecraft.resources.Identifier;

/** Assigned once when a sprite animation is created; avoids registry/string work per tick. */
public interface SpriteAnimationControl {
	void kohsCore$setSprite(Identifier id);
}
