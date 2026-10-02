package com.barbwra.mlum.downed;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The mod's one entity: the practice body for the downed system. */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MlumInventory.MODID);

    public static final RegistryObject<EntityType<DownedDummy>> DOWNED_DUMMY = ENTITIES.register("downed_dummy",
            () -> EntityType.Builder.<DownedDummy>of(DownedDummy::new, MobCategory.MISC)
                    .sized(0.8F, 0.5F)
                    .clientTrackingRange(10)
                    .build("downed_dummy"));

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DOWNED_DUMMY.get(), DownedDummy.attributes().build());
    }
}
