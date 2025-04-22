package com.chanceman.lifecycle;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>The LifeCycleHub singleton maintains a registry of objects that implement the {@link ILifeCycle} interface.</p>
 * <p>Upon plugin startUp/shutDown, any currently registered ILifecycles will have their {@link ILifeCycle#startUp()}
 * or {@link ILifeCycle#shutDown()} method called.</p>
 * <p>To ensure proper registration, the implementation's {@link ILifeCycle#init(LifeCycleHub)} method should call
 * {@link LifeCycleHub#register(ILifeCycle)}. This is done can be done using the default {@link ILifeCycle#register()}.</p>
 */
@Slf4j
@Singleton
public class LifeCycleHub
{

	private final Set<UUID> lifeCycleUUIDs = ConcurrentHashMap.newKeySet();
	private final ConcurrentHashMap<UUID, ILifeCycle> lifeCycles = new ConcurrentHashMap<>();

	/**
	 * <p>Registers an ILifeCycle with the LifeCycleHub. Upon plugin startUp/shutDown, any currently registered</p>
	 * ILifeCycle will have their {@link ILifeCycle#startUp()} and {@link ILifeCycle#shutDown()} methods called.</p>
	 * @param lifeCycle The ILifeCycle to register.
	 */
	public void register(ILifeCycle lifeCycle)
	{
		if (lifeCycleUUIDs.contains(lifeCycle.getUuid())) return;
		lifeCycleUUIDs.add(lifeCycle.getUuid());
		lifeCycles.put(lifeCycle.getUuid(), lifeCycle);
		String debug = String.format("Registered ILifeCycle: %s", lifeCycle.getClass().getName());
		log.debug(debug);
		System.out.println(debug);
	}

	/**
	 * <p>Unregisters an ILifeCycle with the LifeCycleHub.</p>
	 * <p>Should be called if an ILifeCycle is being disposed.</p>
	 * @param lifeCycle The ILifeCycle to unregister.
	 */
	public void unregister(ILifeCycle lifeCycle)
	{
		lifeCycleUUIDs.remove(lifeCycle.getUuid());
		lifeCycles.remove(lifeCycle.getUuid());
		String debug = String.format("Unregistered ILifeCycle: %s", lifeCycle.getClass().getName());
		log.debug(debug);
		System.out.println(debug);
	}

	/**
	 * <p>Calls the {@link ILifeCycle#startUp()} method on all currently registered ILifeCycles.</p>
	 * <p>Called upon plugin startUp.</p>
	 */
	public void startUp()
	{
		for (UUID uuid : lifeCycleUUIDs)
		{
			ILifeCycle lifeCycle = lifeCycles.get(uuid);
			if (lifeCycle == null)
			{
				lifeCycleUUIDs.remove(uuid);
				lifeCycles.remove(uuid);
				continue;
			}
			lifeCycle.startUp();
		}
	}

	/**
	 * <p>Calls the {@link ILifeCycle#shutDown()} method on all currently registered ILifeCycles.</p>
	 * <p>Called upon plugin shutDown.</p>
	 */
	public void shutDown()
	{
		for (UUID uuid : lifeCycleUUIDs)
		{
			ILifeCycle lifeCycle = lifeCycles.get(uuid);
			if (lifeCycle == null)
			{
				lifeCycleUUIDs.remove(uuid);
				lifeCycles.remove(uuid);
				continue;
			}
			lifeCycle.shutDown();
		}
	}
}
