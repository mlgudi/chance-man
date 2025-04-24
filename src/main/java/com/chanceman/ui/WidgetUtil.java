package com.chanceman.ui;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

import java.util.function.Consumer;

/**
 * Helpers for widget-related functionality, primarily removing the need for null checks everywhere.
 */
public class WidgetUtil
{

	/**
	 * Returns the Widget children of the given type
	 * @param widget The parent Widget
	 * @param childType The child type
	 * @return The Widget children
	 */
	private static Widget[] getChildren(Widget widget, ChildType childType)
	{
		Widget[] children = null;
		switch (childType)
		{
			case STATIC:
				children = widget.getStaticChildren();
				break;
			case DYNAMIC:
				children = widget.getDynamicChildren();
				break;
			case NESTED:
				children = widget.getNestedChildren();
				break;
			case STANDARD:
				children = widget.getChildren();
				break;
		}
		return children;
	}

	/**
	 * Invokes the consumer only if the target Widget is non-null
	 * @param widget The Widget
	 * @param consumer The consumer to be invoked
	 */
	public static void apply(Widget widget, Consumer<Widget> consumer)
	{
		if (widget != null) consumer.accept(widget);
	}

	/**
	 * Invokes the consumer only if the target Widget is non-null
	 * @param client The client
	 * @param componentId The Widget component ID
	 * @param consumer The consumer to be invoked
	 */
	public static void apply(Client client, int componentId, Consumer<Widget> consumer)
	{
		Widget widget = client.getWidget(componentId);
		if (widget == null) return;
		consumer.accept(widget);
	}

	/**
	 * Invokes the consumer only if the target Widget is non-null
	 * @param client The client
	 * @param groupId The Widget group ID
	 * @param childId The Widget child ID
	 * @param consumer The consumer to be invoked
	 */
	public static void apply(Client client, int groupId, int childId, Consumer<Widget> consumer)
	{
		Widget widget = client.getWidget(groupId, childId);
		if (widget == null) return;
		consumer.accept(widget);
	}

	/**
	 * Invokes the consumer only if the child Widget at the given index is non-null
	 * @param widget The Widget
	 * @param childIndex The index of the target child widget
	 * @param consumer The consumer to be invoked
	 */
	public static void applyToChild(Widget widget, int componentId, int childIndex, Consumer<Widget> consumer)
	{
		if (widget == null) return;

		Widget child = widget.getChild(childIndex);
		if (child == null) return;

		consumer.accept(child);
	}

	/**
	 * Invokes the consumer only if the child Widget at the given index is non-null
	 * @param client The client
	 * @param componentId The Widget component ID
	 * @param childIndex The index of the target child widget
	 * @param consumer The consumer to be invoked
	 */
	public static void applyToChild(Client client, int componentId, int childIndex, Consumer<Widget> consumer)
	{
		Widget widget = client.getWidget(componentId);
		if (widget == null) return;

		Widget child = widget.getChild(childIndex);
		if (child == null) return;

		consumer.accept(child);
	}

	/**
	 * Invokes the consumer only if the child Widget at the given index is non-null
	 * @param client The client
	 * @param groupId The Widget group ID
	 * @param childId The Widget child ID
	 * @param childIndex The index of the target child Widget
	 * @param consumer The consumer to be invoked
	 */
	public static void applyToChild(Client client, int groupId, int childId, int childIndex, Consumer<Widget> consumer)
	{
		Widget widget = client.getWidget(groupId, childId);
		if (widget == null) return;

		Widget child = widget.getChild(childIndex);
		if (child == null) return;

		consumer.accept(child);
	}

	/**
	 * Invokes the consumer for any non-null children
	 * @param widget The Widget
	 * @param componentId The Widget component ID
	 * @param consumer The consumer to be invoked
	 */
	public static void applyToAllChildren(Widget widget, int componentId, Consumer<Widget> consumer)
	{
		if (widget == null) return;

		Widget[] children = widget.getChildren();
		if (children == null) return;

		for (Widget child : children)
		{
			if (child == null) continue;
			consumer.accept(child);
		}
	}

	/**
	 * Invokes the consumer for any non-null children
	 * @param client The client
	 * @param componentId The Widget component ID
	 * @param consumer The consumer to be invoked
	 */
	public static void applyToAllChildren(Client client, int componentId, Consumer<Widget> consumer)
	{
		Widget widget = client.getWidget(componentId);
		if (widget == null) return;

		Widget[] children = widget.getChildren();
		if (children == null) return;

		for (Widget child : children)
		{
			if (child == null) continue;
			consumer.accept(child);
		}
	}

	/**
	 * Invokes the consumer for non-null children of the given type
	 * @param client The client
	 * @param componentId The widget component ID
	 * @param type The child type
	 * @param consumer The consumer to be invoked
	 */
	public static void applyToAllChildren(Client client, int componentId, ChildType type, Consumer<Widget> consumer)
	{
		Widget widget = client.getWidget(componentId);
		if (widget == null) return;

		Widget[] children = getChildren(widget, type);
		for (Widget child : children)
		{
			if (child == null) continue;
			consumer.accept(child);
		}
	}

	/**
	 * Invokes the consumer for non-null children of the given type
	 * @param client The client
	 * @param groupId The Widget group ID
	 * @param childId The Widget child ID
	 * @param type The child type
	 * @param consumer The consumer to be invoked
	 */
	public static void applyToAllChildren(Client client, int groupId, int childId, ChildType type,
										  Consumer<Widget> consumer)
	{
		Widget widget = client.getWidget(groupId, childId);
		if (widget == null) return;

		Widget[] children = getChildren(widget, type);
		for (Widget child : children)
		{
			if (child == null) continue;
			consumer.accept(child);
		}
	}
}
