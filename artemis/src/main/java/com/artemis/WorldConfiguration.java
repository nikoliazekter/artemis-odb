package com.artemis;

import com.artemis.injection.Injector;
import com.artemis.utils.Bag;
import com.artemis.utils.reflect.ClassReflection;
import com.artemis.utils.reflect.ReflectionException;

import java.util.*;

import static com.artemis.EntityManager.NO_COMPONENTS;

public final class WorldConfiguration {
	public static final int COMPONENT_MANAGER_IDX = 0;
	public static final int ENTITY_MANAGER_IDX = 1;
	public static final int ASPECT_SUBSCRIPTION_MANAGER_IDX = 2;
	
	final Bag<BaseSystem> systems = new Bag<BaseSystem>();

	protected int expectedEntityCount = 128;
	protected Map<String, Object> injectables = new HashMap<String, Object>();

	protected Injector injector;
	protected SystemInvocationStrategy invocationStrategy;

	private Set<Class<? extends BaseSystem>> registered = new HashSet<Class<? extends BaseSystem>>();

	public WorldConfiguration() {
		// reserving space for core managers
		systems.add(null); // ComponentManager
		systems.add(null); // EntityManager
		systems.add(null); // AspectSubscriptionManager
	}

	public int expectedEntityCount() {
		return expectedEntityCount;
	}

	/**
	 * Initializes array type containers with the value supplied.
	 * 
	 * @param expectedEntityCount count of expected entities.
	 * @return This instance for chaining.
	 */
	public WorldConfiguration expectedEntityCount(int expectedEntityCount) {
		this.expectedEntityCount = expectedEntityCount;
		return this;
	}

	/**
	 * Set Injector to handle all dependency injections.
	 *
	 * @param injector Injector to handle dependency injections.
	 * @return This instance for chaining.
	 */
	public WorldConfiguration setInjector(Injector injector) {
		if ( injector == null ) throw new NullPointerException();
		this.injector = injector;
		return this;
	}

	/**
	 * Set strategy for invoking systems on {@link World#process()}.
	 * @param invocationStrategy Strategy that will invoke systems.
	 * @return This instance for chaining.
	 */
	public WorldConfiguration setInvocationStrategy(SystemInvocationStrategy invocationStrategy) {
		if ( invocationStrategy == null ) throw new NullPointerException();
		this.invocationStrategy = invocationStrategy;
		return this;
	}

	/**
	 * Manually register object for injection by type.
	 *
	 * Explicitly annotate to be injected fields with <code>@Wire</code>. A class level
	 * <code>@Wire</code> annotation is not enough.
	 *
	 * Since objects are injected by type, this method is limited to one object per type.
	 * Use {@link #register(String, Object)} to register multiple objects of the same type.
	 *
	 * Not required for systems.
	 *
	 * @param o object to inject.
	 * @return This instance for chaining.
	 */
	public WorldConfiguration register(Object o) {
		return register(o.getClass().getName(), o);
	}

	/**
	 * Manually register object for injection by name.
	 *
	 * Explicitly annotate to be injected fields with <code>@Wire(name="myName")</code>. A class
	 * level <code>@Wire</code> annotation is not enough.
	 *
	 * Not required for systems.
	 *
	 * @param name unique identifier matching injection site name.
	 * @param o object to inject.
	 * @return This instance for chaining.
	 */
	public WorldConfiguration register(String name, Object o) {
		injectables.put(name, o);
		return this;
	}

	/**
	 * Adds a system to this world that will be processed by
	 * {@link World#process()}.
	 *
	 * @param system the system to add
	 */
	public WorldConfiguration setSystem(Class<? extends BaseSystem> system) {
		return setSystem(system, false);
	}

	/**
	 * Adds a system to this world that will be processed by
	 * {@link World#process()}.
	 *
	 * @param system the system to add
	 * @return the added system
	 */
	public WorldConfiguration setSystem(Class<? extends BaseSystem> system, boolean passive) {
		try {
			return setSystem(ClassReflection.newInstance(system), passive);
		} catch (ReflectionException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Adds a system to this world that will be processed by
	 * {@link World#process()}.
	 *
	 * @param <T>	the system class type
	 * @param system the system to add
	 * @return the added system
	 */
	public <T extends BaseSystem> WorldConfiguration setSystem(T system) {
		return setSystem(system, false);
	}

		/**
	 * Will add a system to this world.
	 *
	 * @param <T>	 the system class type
	 * @param system  the system to add
	 * @param passive whether or not this system will be processed by
	 *				{@link World#process()}
	 * @return the added system
	 */
	public <T extends BaseSystem> WorldConfiguration setSystem(T system, boolean passive) {
		system.setPassive(passive);
		systems.add(system);

		if (!registered.add(system.getClass())) {
			String name = system.getClass().getSimpleName();
			throw new RuntimeException(name + " already added to " + getClass().getSimpleName());
		}

		return this;
	}

	void initialize(World world, Injector injector, AspectSubscriptionManager asm) {
		if ( invocationStrategy != null ) {
			world.setInvocationStrategy(invocationStrategy);
		}

		systems.set(COMPONENT_MANAGER_IDX, world.getComponentManager());
		systems.set(ENTITY_MANAGER_IDX, world.getEntityManager());
		systems.set(ASPECT_SUBSCRIPTION_MANAGER_IDX, asm);

		for (BaseSystem system : systems) {
			world.systems.put(system.getClass(), system);
			system.setWorld(world);
		}

		injector.initialize(world, injectables);

		initializeSystems(injector);

		asm.processComponentIdentity(NO_COMPONENTS, new BitSet());
	}

	private void initializeSystems(Injector injector) {
		for (int i = 0, s = systems.size(); i < s; i++) {
			BaseSystem system = systems.get(i);
			injector.inject(system);
			system.initialize();
		}
	}
}
