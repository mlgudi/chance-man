package com.chanceman.menus;

import lombok.Getter;

public enum EnabledUI
{
	BANK(12),
	DEPOSIT_BOX(192);

	@Getter private final int id;

	EnabledUI(int id) {
		this.id = id;
	}
}
