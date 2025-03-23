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
            description = "Only allow free-to-play items"
    )
    default boolean freeToPlay()
    {
        return false;
    }
}
