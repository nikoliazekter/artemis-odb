package com.artemis;

import com.artemis.annotations.Transient;
import com.artemis.io.SaveFileFormat;
import com.artemis.utils.reflect.ClassReflection;

import java.util.BitSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * During saving, this class is responsible for collecting all used
 * component types which aren't annotated with {@link Transient}.
 */
public class ComponentCollector {
	private BitSet componentIds = new BitSet();
	private Set<Class<Component>> referencedComponents = new HashSet<Class<Component>>();

	private World world;

	public ComponentCollector(World world) {
		this.world = world;
	}

	public void preWrite(SaveFileFormat save) {
		componentIds.clear();
		referencedComponents.clear();

		inspectComponentTypes(save);
		extractComponents(save);
	}

	protected void extractComponents(SaveFileFormat save) {
		ComponentManager cm = world.getComponentManager();
		IdentityHashMap<Class<? extends Component>, String> lookup = save.componentIdentifiers;

		Set<String> names = new HashSet<String>();
		BitSet bs = componentIds;
		for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
			Class<? extends Component> type = cm.typeFactory.getTypeFor(i).getType();
			if (ClassReflection.getDeclaredAnnotation(type, Transient.class) != null)
				continue;

			lookup.put(type, resolveNameId(names, type));
		}
	}

	private String resolveNameId(Set<String> existing, Class<? extends Component> type) {
		String name = type.getSimpleName();
		if (existing.contains(name)) {
			int index = 2;
			while(existing.contains(name + "_" + index++));
			name += "_" + (index - 1);
		}

		existing.add(name);
		return name;
	}

	protected void inspectComponentTypes(SaveFileFormat save) {
		EntityManager em = world.getEntityManager();

		int[] ids = save.entities.getData();
		for (int i = 0, s = save.entities.size(); s > i; i++)
			componentIds.or(em.componentBits(ids[i]));
	}
}
