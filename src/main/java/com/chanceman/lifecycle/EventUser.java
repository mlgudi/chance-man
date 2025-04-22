package com.chanceman.lifecycle;

import lombok.Getter;
import lombok.Setter;
import net.runelite.client.eventbus.EventBus;

import javax.inject.Inject;

/**
 * <p>A class to be extended by users of the RuneLite EventBus.</p>
 * <p>Extending classes can post events using the {@link #post(Object)} method, and optionally register with
 * the event bus for subscriptions.</p>
 * <p>Set {@link #subscriber} to true to register for event subscriptions with the RuneLite EventBus.</p>
 * <p>EventBus registration/unregistration is handled automatically post-construction and upon plugin
 * startUp/shutDown.</p>
 */
public class EventUser extends LifeCycle
{

	@Getter @Setter private EventBus eventBus;
	@Getter @Setter private boolean subscriber = true;
	private boolean subscribed = false;

	/**
	 * <p>Automatically called by Guice post-construction.</p>
	 * <p>If not already initialised, sets the EventUser's EventBus and, if subscribe is true, registers it with the
	 * RuneLite EventBus.</p>
	 * @param eventBus The RuneLite EventBus instance.
	 */
	@Inject
	private void initEvents(EventBus eventBus)
	{
		if (getEventBus() != null) return;
		setEventBus(eventBus);
		if (isSubscriber()) subscribe();
	}

	/**
	 * If the EventUser is a subscriber and is not registered, registers the EventUser with the RuneLite EventBus to
	 * enable event subscriptions.
	 */
	public void subscribe()
	{
		if (!isSubscriber() || this.subscribed) return;
		getEventBus().register(this);
		this.subscribed = true;
	}

	/**
	 * If registered, unregisters the EventUser with the RuneLite EventBus to disable event subscriptions.
	 */
	public void unsubscribe()
	{
		if (!this.subscribed) return;
		getEventBus().unregister(this);
		this.subscribed = false;
	}

	@Override
	public void onStartUp()
	{
		super.onStartUp();
		subscribe();
	}

	@Override
	public void onShutDown()
	{
		super.onShutDown();
		unsubscribe();
	}

	/**
	 * Posts an event to the RuneLite EventBus.
	 * @param event The event to post.
	 */
	public void post(Object event)
	{
		if (getEventBus() == null) return;
		getEventBus().post(event);
	}
}
