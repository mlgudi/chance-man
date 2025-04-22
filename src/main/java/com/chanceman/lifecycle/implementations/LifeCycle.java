package com.chanceman.lifecycle.implementations;

import com.chanceman.lifecycle.ILifeCycle;
import com.chanceman.lifecycle.LifeCycleHub;
import lombok.Getter;
import lombok.Setter;

import javax.inject.Inject;

/**
 * <p>A base implementation of the {@link ILifeCycle} interface.</p>
 * <p>Post-construction logic can be added by overriding the {@link ILifeCycle#onInit()} hook.</p>
 * <p>Plugin startUp/shutDown logic can be added by overriding the {@link ILifeCycle#onStartUp()} and
 * {@link ILifeCycle#onShutDown()} hooks.</p>
 * <p><strong>Note:</strong></p>
 * <p>Classes extending LifeCycle should not use @Inject-annotated fields if those fields are referenced in the
 * onInit() or onStartUp() methods.</p>
 * <p>Instead, <strong>use an @Inject-annotated constructor</strong>.</p>
 */
public class LifeCycle implements ILifeCycle
{

	@Getter @Setter private LifeCycleHub lifeCycleHub;
	@Getter @Setter private boolean started = false;

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
}
