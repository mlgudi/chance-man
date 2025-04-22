package com.chanceman.lifecycle;

import java.util.UUID;

/**
 * <p>The {@link ILifeCycle} interface can be implemented by classes with startUp/shutDown logic to be performed upon
 * plugin startUp/shutDown.</p>
 * <p>The {@link #onInit()} hook can be overridden with desired post-construction logic.</p>
 * <p>The {@link #onStartUp()} and {@link #onShutDown()} hooks can be overridden with desired logic for
 * plugin startUp/shutDown.</p>
 * <p>A minimal implementation can be found in the {@link LifeCycle} class.</p>
 */
public interface ILifeCycle
{
	UUID getUuid();

	void setLifeCycleHub(LifeCycleHub lifeCycleHub);
	LifeCycleHub getLifeCycleHub();

	void setStarted(boolean started);
	boolean isStarted();

	/**
	 * <p>The init method should be implemented to register the ILifeCycle with the LifeCycleHub singleton.</p>
	 * <p>It is recommended call this method from a constructor or method annotated with the @Inject to ensure it is
	 * called post-construction.</p>
	 * @param lifecycleHub The LifeCycleHub singleton.
	 */
	default void init(LifeCycleHub lifecycleHub)
	{
		if (getLifeCycleHub() != null) return; // Already initialised
		setLifeCycleHub(lifecycleHub);
		register();
		onInit();
	}

	/**
	 * Registers the ILifeCycle with the LifeCycleHub singleton.
	 */
	default void register()
	{
		getLifeCycleHub().register(this);
	}

	/**
	 * <p>Unregisters the ILifeCycle with the LifeCycleHub singleton.</p>
	 * <p><strong>Note:</strong></p>
	 * <p>Should be called when the object is being disposed, <strong>not upon plugin shutDown</strong>.</p>
	 * <p>If the object is to persist across plugin shutDown/startUp cycles, it
	 * should remain registered with the LifeCycleHub after shutDown.</p>
	 */
	default void unregister()
	{
		getLifeCycleHub().unregister(this);
	}

	/**
	 * <p>A hook to be overridden with desired post-construction logic, called at the end of
	 * {@link #init(LifeCycleHub)}.</p>
	 */
	default void onInit() { startUp(); }

	/**
	 * <p>A hook to be overridden with desired startUp logic, called upon plugin startUp.</p>
	 * <p>This method is only called if {@link #isStarted()} is false, preventing multiple calls in a single plugin
	 * cycle.</p>
	 */
	default void onStartUp() {}

	/**
	 * <p>A hook to be overridden with desired shutDown logic.</p>
	 * <p>This method is only called if {@link #isStarted()} is true, preventing multiple calls in a single plugin
	 * cycle.</p>
	 */
	default void onShutDown() {}

	/**
	 * <p><strong>NOT FOR OVERIDE.</strong></p>
	 * <p>Instead, override the {@link #onStartUp()} hook.</p>
	 * <p>Called upon plugin startUp.</p>
	 * <p>If {@link #isStarted()} is false, calls {@link #onStartUp()}.</p>
	 */
	default void startUp()
	{
		if (isStarted()) return;
		onStartUp();
		setStarted(true);
	}

	/**
	 * <p><strong>NOT FOR OVERIDE.</strong></p>
	 * <p>Instead, override the {@link #onShutDown()} hook.</p>
	 * <p>Called upon plugin shutDown.</p>
	 * <p>If {@link #isStarted()} is true, calls the {@link #onShutDown()} method.</p>
	 */
	default void shutDown()
	{
		if (!isStarted()) return;
		onShutDown();
		setStarted(false);
	}
}
