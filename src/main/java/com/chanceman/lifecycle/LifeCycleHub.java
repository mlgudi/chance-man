package com.chanceman.lifecycle;

import com.chanceman.events.StartUpComplete;
import net.runelite.client.eventbus.EventBus;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>The LifeCycleHub singleton maintains a registry of objects that implement the {@link ILifeCycle} interface.</p>
 * <p>Upon plugin startUp/shutDown, any currently registered ILifecycles will have their {@link ILifeCycle#startUp()}
 * or {@link ILifeCycle#shutDown()} method called.</p>
 * <p>To ensure proper registration, the implementation should call {@link ILifeCycle#init(LifeCycleHub)} from either
 * the constructor or a method annotated with the @Inject.</p>
 */
@Singleton
public class LifeCycleHub
{

	private final EventBus eventBus;
	private final Set<ILifeCycle> lifeCycles = ConcurrentHashMap.newKeySet();

	@Inject
	public LifeCycleHub(EventBus eventBus)
	{
		this.eventBus = eventBus;
	}

	/**
	 * <p>Registers an ILifeCycle with the LifeCycleHub. Upon plugin startUp/shutDown, any currently registered</p>
	 * ILifeCycle will have their {@link ILifeCycle#startUp()} and {@link ILifeCycle#shutDown()} methods called.</p>
	 * @param lifeCycle The ILifeCycle to register.
	 */
	public void register(ILifeCycle lifeCycle)
	{
		if (lifeCycle == null) return;
		lifeCycles.add(lifeCycle);
	}

	/**
	 * <p>Unregisters an ILifeCycle with the LifeCycleHub.</p>
	 * <p>Should be called if an ILifeCycle is being disposed.</p>
	 * @param lifeCycle The ILifeCycle to unregister.
	 */
	public void unregister(ILifeCycle lifeCycle)
	{
		lifeCycles.remove(lifeCycle);
	}

	/**
	 * <p>Calls the {@link ILifeCycle#startUp()} method on all currently registered ILifeCycles.</p>
	 * <p>Called upon plugin startUp.</p>
	 */
	public void startUp()
	{
		for (ILifeCycle lifeCycle : lifeCycles)
		{
			if (lifeCycle == null)
			{
				lifeCycles.remove(null);
				continue;
			}
			lifeCycle.startUp();
		}
		this.eventBus.post(new StartUpComplete());
	}

	/**
	 * <p>Calls the {@link ILifeCycle#shutDown()} method on all currently registered ILifeCycles.</p>
	 * <p>Called upon plugin shutDown.</p>
	 */
	public void shutDown()
	{
		for (ILifeCycle lifeCycle : lifeCycles)
		{
			if (lifeCycle == null)
			{
				lifeCycles.remove(null);
				continue;
			}
			lifeCycle.shutDown();
		}
	}
}
