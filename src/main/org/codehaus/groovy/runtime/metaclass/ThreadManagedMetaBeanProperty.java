/* Copyright 2004-2005 Graeme Rocher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.runtime.metaclass;


import groovy.lang.MetaBeanProperty;
import groovy.lang.MetaMethod;


import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * This MetaBeanProperty will create a psuedo property whoes value is bound to the current
 * Thread using soft references. The values will go out of scope and be garabage collected when
 * the Thread dies or when memory is required by the JVM
 *
 * The property uses a InheritableThreadLocal instance internally so child threads will still be able
 * to see the property
 *
 * @author Graeme Rocher
 * @since 1.1
 *
 */
public class ThreadManagedMetaBeanProperty extends MetaBeanProperty {
	private static final Class[] ZERO_ARGUMENT_LIST = new Class[0];
	private static final ThreadLocal propertyInstanceHolder = new InheritableThreadLocal();

	private Class declaringClass;
	private ThreadBoundGetter getter;
	private ThreadBoundSetter setter;
	private Object initialValue;
    private static final String PROPERTY_SET_PREFIX = "set";

    /**
	 * Retrieves the initial value of the ThreadBound property
	 *
	 * @return The initial value
	 */
	public synchronized Object getInitialValue() {
		return initialValue;
	}

	/**
	 * Constructs a new ThreadManagedBeanProperty for the given arguments
	 *
	 * @param declaringClass The class that declares the property
	 * @param name The name of the property
	 * @param type The type of the property
	 * @param iv The properties initial value
	 */
	public ThreadManagedMetaBeanProperty(Class declaringClass, String name, Class type, Object iv) {
		super(name, type, null,null);
		this.type = type;
		this.declaringClass = declaringClass;

		this.getter = new ThreadBoundGetter(name);
		this.setter = new ThreadBoundSetter(name);
		initialValue = iv;

	}

	private static Object getThreadBoundPropertyValue(Object obj, String name, Object initialValue) {
		Map propertyMap = getThreadBoundPropertMap();
		String key = System.identityHashCode(obj) + name;
		if(propertyMap.containsKey(key)) {
			return propertyMap.get(key);
		}
		else {
			propertyMap.put(key, initialValue);
			return initialValue;
		}
	}

	private static Map getThreadBoundPropertMap() {
		Map propertyMap = (Map)propertyInstanceHolder.get();
		if(propertyMap == null) {
			propertyMap = new WeakHashMap();
			propertyInstanceHolder.set(propertyMap);
		}
		return propertyMap;
	}

	private static Object setThreadBoundPropertyValue(Object obj, String name, Object value) {
		Map propertyMap = getThreadBoundPropertMap();
		String key = System.identityHashCode(obj) + name;
		return propertyMap.put(key,value);
	}

	/* (non-Javadoc)
	 * @see groovy.lang.MetaBeanProperty#getGetter()
	 */
	public MetaMethod getGetter() {
		return this.getter;
	}

	/* (non-Javadoc)
	 * @see groovy.lang.MetaBeanProperty#getSetter()
	 */
	public MetaMethod getSetter() {
		return this.setter;
	}



	/**
	 * Accesses the ThreadBound state of the property as a getter
	 *
	 * @author Graeme Rocher
	 *
	 */
	class ThreadBoundGetter extends MetaMethod {


		private String getterName;


		public ThreadBoundGetter(String name) {
			super(name, declaringClass, ZERO_ARGUMENT_LIST, type, Modifier.PUBLIC);
			getterName = getGetterName(name, type);
		}


		/* (non-Javadoc)
		 * @see groovy.lang.MetaMethod#getName()
		 */
		public String getName() {
			return getterName;
		}


		/* (non-Javadoc)
		 * @see groovy.lang.MetaMethod#invoke(java.lang.Object, java.lang.Object[])
		 */
		public Object invoke(Object object, Object[] arguments) {
			return getThreadBoundPropertyValue(object, name, getInitialValue());
		}
	}

	/**
	 * Sets the ThreadBound state of the property like a setter
	 *
	 * @author Graeme Rocher
	 *
	 */
	private class ThreadBoundSetter extends MetaMethod {


		private String setterName;

		public ThreadBoundSetter(String name) {
			super(name, declaringClass, new Class[]{type}, type, Modifier.PUBLIC);
			setterName = getSetterName(name);
		}



		/* (non-Javadoc)
		 * @see groovy.lang.MetaMethod#getName()
		 */
		public String getName() {
			return setterName;
		}
		/* (non-Javadoc)
		 * @see groovy.lang.MetaMethod#invoke(java.lang.Object, java.lang.Object[])
		 */
		public Object invoke(Object object, Object[] arguments) {
			return setThreadBoundPropertyValue(object, name, arguments[0]);
		}
	}

    private String getGetterName(String propertyName, Class type)
    {
        String prefix = type == boolean.class || type == Boolean.class ? "is" : "get";
        return prefix + Character.toUpperCase(propertyName.charAt(0))
            + propertyName.substring(1);
    }

    private String getSetterName(String propertyName) {
        return PROPERTY_SET_PREFIX+propertyName.substring(0,1).toUpperCase()+ propertyName.substring(1);
    }

}
