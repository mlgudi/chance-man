package com.chanceman;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("chanceman")
public interface ChanceManConfig extends Config
{
    @ConfigItem(
            keyName = "freeToPlay",
            name = "Free To Play Mode",
            description = "Only allow free-to-play items",
            position = 1
    )
    default boolean freeToPlay()
    {
        return false;
    }

    @ConfigItem(
            keyName = "enableArmourSets",
            name = "Roll Armour Sets",
            description = "Include armour set items in the rollable items list. Disabling this will exclude any" +
                    " armour set items from random rolls.",
            position = 2
    )
    default boolean enableArmourSets() { return true; }

    @ConfigItem(
            keyName = "enableFlatpacks",
            name = "Roll Flatpacks",
            description = "Include flatpacks in the rollable items list. Disabling this will prevent" +
                    " flatpacks from being rolled.",
            position = 3
    )
    default boolean enableFlatpacks() { return true; }

    @ConfigItem(
            keyName = "requireWeaponPoison",
            name = "Weapon Poison Unlock Requirements",
            description = "Force poison variants to roll only if both the base weapon and the corresponding" +
                    " weapon poison are unlocked. (Disabling this will allow poisoned variants to roll even if " +
                    "the poison is locked.)",
            position = 4
    )
    default boolean requireWeaponPoison() { return true; }
}
