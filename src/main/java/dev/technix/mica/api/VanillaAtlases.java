package dev.technix.mica.api;

import net.minecraft.resources.Identifier;


public final class VanillaAtlases {

    
    public static final Identifier BLOCKS =
            Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png");

    
    public static final Identifier ITEMS =
            Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/items.png");

    
    public static final Identifier DEFAULT_SKIN =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/steve.png");

    private VanillaAtlases() {
    }
}
