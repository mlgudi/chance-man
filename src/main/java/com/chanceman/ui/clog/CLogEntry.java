package com.chanceman.ui.clog;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;

@Data
@AllArgsConstructor
public class CLogEntry
{
	private int index;

	private int unlockedId;
	private String unlockedName;

	private int rolledId;
	private String rolledName;

	private boolean unlockedIsRolled;
	private boolean rolledIsUnlocked;

	public int unlockedOpacity()
	{
		return unlockedIsRolled ? 0 : 130;
	}
	public int rolledOpacity()
	{
		return rolledIsUnlocked ? 0 : 130;
	}

	public String actionText(boolean showObtained) {
		ChatMessageBuilder message = new ChatMessageBuilder()
				.append(ChatColorType.NORMAL);

		if (showObtained) {
			message.append(rolledName + " unlocked " + unlockedName + ".");
		} else {
			message.append(unlockedName + " was unlocked by " + rolledName + ".");
		}

		message.append(" ").append("Unlock number " + (index + 1) + ".");
		return message.build();
	}
}
