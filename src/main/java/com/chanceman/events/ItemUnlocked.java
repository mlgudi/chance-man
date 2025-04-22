package com.chanceman.events;

import lombok.Data;

@Data
public class ItemUnlocked
{
	private final int itemId;
	private final String itemName;
}
