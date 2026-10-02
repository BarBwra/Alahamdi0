package com.barbwra.mlum.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the TACZ mixins only when TACZ is actually in the pack.
 *
 * <p>Without this, a pack assembled without TACZ would have Mixin try to transform a class that is
 * not there. Every other piece of TACZ compatibility in this mod degrades to "no guns" rather than
 * crashing, and bytecode transformation is the one place where that has to be arranged on purpose -
 * it happens long before the mod's own code gets a chance to check anything.</p>
 *
 * <p>{@link LoadingModList} is the right question to ask at this point in startup: mods are not
 * constructed yet, so {@code ModList} does not exist, but the file list has been scanned.</p>
 */
public final class MlumMixinPlugin implements IMixinConfigPlugin {

    private boolean tacz;

    @Override
    public void onLoad(String mixinPackage) {
        tacz = LoadingModList.get() != null && LoadingModList.get().getModFileById("tacz") != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return tacz;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {
    }
}
