package com.chanceman.lifecycle.implementations;

import com.chanceman.lifecycle.ILifeCycle;
import com.chanceman.lifecycle.LifeCycleHub;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import java.util.UUID;

/**
 * <p>A base implementation of ILifeCycle extending PluginPanel, with support for event posting/subscriptions.</p>
 * <p>Post-construction logic can be added by overriding the {@link ILifeCycle#onInit()} hook.</p>
 * <p>Plugin startUp/shutDown logic can be added by overriding the {@link ILifeCycle#onStartUp()} and
 * {@link ILifeCycle#onShutDown()} hooks.</p>
 * <p><strong>Note:</strong></p>
 * <p>Classes extending EventPanel should not use @Inject-annotated fields if those fields are referenced in the
 * onInit() or onStartUp() methods.</p>
 * <p>Instead, <strong>use an @Inject-annotated constructor</strong>.</p>
 */
public class EventPanel extends PluginPanel implements ILifeCycle
{

	@Getter private final UUID uuid = UUID.randomUUID();
	@Getter @Setter private LifeCycleHub lifeCycleHub;
	@Getter @Setter private boolean started = false;

	@Getter @Setter private EventBus eventBus;
	private boolean subscribed = false;

	/**
	 * <p>The @Inject annotation ensures that this method is called post-construction by Guice.</p>
	 * <p>Simply calls the default implementation of {@link ILifeCycle#init(LifeCycleHub)}.</p>
	 * @param lifeCycleHub The LifeCycleHub singleton.
	 */
	@Inject
	public void initCycle(LifeCycleHub lifeCycleHub)
	{
		init(lifeCycleHub);
	}

	/**
	 * <p>Automatically called by Guice post-construction.</p>
	 * <p>If not already initialised, sets the EventUser's EventBus and, if subscribe is true, registers it with the
	 * RuneLite EventBus.</p>
	 * @param eventBus The RuneLite EventBus instance.
	 */
	@Inject
	public void initEvents(EventBus eventBus)
	{
		if (getEventBus() != null) return;
		setEventBus(eventBus);
		subscribe();
	}

	/**
	 * If the EventUser is a subscriber and is not registered, registers the EventUser with the RuneLite EventBus to
	 * enable event subscriptions.
	 */
	public void subscribe()
	{
		if (getEventBus() == null || this.subscribed) return;
		getEventBus().register(this);
		this.subscribed = true;
	}

	/**
	 * If registered, unregisters the EventUser with the RuneLite EventBus to disable event subscriptions.
	 */
	public void unsubscribe()
	{
		if (getEventBus() == null || !this.subscribed) return;
		getEventBus().unregister(this);
		this.subscribed = false;
	}

	@Override
	public void onStartUp()
	{
		ILifeCycle.super.onStartUp();
		subscribe();
	}

	@Override
	public void onShutDown()
	{
		ILifeCycle.super.onShutDown();
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
