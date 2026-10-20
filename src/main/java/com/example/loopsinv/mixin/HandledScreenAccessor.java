package com.example.loopsinv.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Ekranın sol üst köşe koordinatlarını okumak için (slot konumlarını bulmakta kullanılır). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int loopsinv_getX();

    @Accessor("y")
    int loopsinv_getY();
}
