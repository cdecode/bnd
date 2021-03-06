package aQute.bnd.main.testlib;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import aQute.bnd.service.Registry;

public class MockRegistry implements Registry {

	private final Set<Object> plugins = new HashSet<Object>();

	public void addPlugin(Object plugin) {
		plugins.add(plugin);
	}

	public <T> List<T> getPlugins(Class<T> clazz) {
		List<T> l = new ArrayList<T>();
		for (Object plugin : plugins) {
			if (clazz.isInstance(plugin))
				l.add(clazz.cast(plugin));
		}
		return l;
	}

	public <T> T getPlugin(Class<T> clazz) {
		for (Object plugin : plugins) {
			if (clazz.isInstance(plugin))
				return clazz.cast(plugin);
		}
		return null;
	}

}
