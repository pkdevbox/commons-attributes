/*
 * Copyright 2003-2004 The Apache Software Foundation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.attributes;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * API for accessing attributes.
 *
 * <h3>General Notes on Errors</h3>
 * 
 * All Methods in this class may throw <code>RepositoryError</code> or subclasses thereof.
 * This Error is thrown if an attribute repository can not be loaded for some Exceptional
 * reason.
 * 
 * <h4>Rationale for Errors instead of Exceptions</h4>
 *
 * The methods in this class throws <code>Error</code>s instead of <code>Exception</code>s.
 * This rationale behind this is that:
 *
 * <ul> 
 * <li>The programmer should not have to wrap all accesses to 
 *     the Attributes API in a try/catch clause.
 * <li>An Exception being thrown here is caused by missing classes
 *     or other "Error-like" conditions.
 * </ul>
 *
 * <h3>Performance Notes</h3>
 * The process of loading attributes for a class is a
 * (relatively) time-consuming process, as it involves some dynamic linking 
 * in the form of inheritable attributes, a lot of reflection and so on. However,
 * once loaded the attributes are cached, so repeated access to them are fast.
 * But despite this the process of finding one attribute of a given type
 * or such operations does involve some iteration of HashSets that <b>runs in linear
 * time in the number of attributes associated with the program element</b>, and you should
 * avoid accessing attributes in your innermost loops if you can avoid it. For 
 * example, instead of:
 *
 * <pre><code>
 * Class myClass = ...;
 * for (int i = 0; i < 10000; i++) {
 *     if (Attributes.hasAttributeType (myClass, MyAttribute.class)) {
 *         doThis(myClass);
 *     } else {
 *         doThat(myClass);
 *     }
 * }
 * </code></pre>
 *
 * do:
 * 
 * <pre><code>
 * Class myClass = ...;
 * boolean shouldDoThis = Attributes.hasAttributeType (myClass, MyAttribute.class);
 * for (int i = 0; i < 10000; i++) {
 *     if (shouldDoThis) {
 *         doThis(myClass);
 *     } else {
 *         doThat(myClass);
 *     }
 * }
 * </code></pre>
 *
 * if the loop should run at maximum speed.
 */
public class Attributes {
    
    /**
     * A cache of attribute repositories. The map used is a WeakHashMap keyed on the
     * Class owning the attribute repository. This works because Class objects use
     * the identity function to compare for equality - i.e. if the two classes
     * have the same name, and are loaded from the same two ClassLoaders, then
     * <code>class1 == class2</code>. Also, <code>(class1.equals(class2)) == (class1 ==
     * class2)</code>. This means that a once a Class reference has been garbage-collected,
     * it can't be re-created. Thus we can treat the cache map as a normal map - the only
     * entries that will ever disappear are those we can't look up anyway because we
     * can't ever create the key for them!
     *
     * <p>Also, this will keep the cache from growing too big in environments where
     * classes are loaded and unloaded all the time (i.e. application servers).
     */
    private final static Map classRepositories = new WeakHashMap ();
    
    /**
     * List used to keep track of the initialization list in getCachedRepository.
     * Since the method is synchronized static, we only need one list.
     */
    private static List initList = new ArrayList ();
    
    private synchronized static CachedRepository getCachedRepository (Class clazz) throws RepositoryError, CircularDependencyError {
        if (classRepositories.containsKey (clazz)) {
            CachedRepository cr = (CachedRepository) classRepositories.get (clazz);
            if (cr == null) {
                // Circular references.
                List dependencyList = new ArrayList ();
                dependencyList.addAll (initList);
                throw new CircularDependencyError (clazz.getName (), dependencyList);
            } else {
                return cr;
            }
        } else {
            // Indicate that we're loading it.
            CachedRepository cached = null;
            
            initList.add (clazz.getName ());
            try {
                classRepositories.put (clazz, null);
                
                Class attributeRepo = null;
                AttributeRepositoryClass repo = EmptyAttributeRepositoryClass.INSTANCE;
                try {
                    attributeRepo = Class.forName (clazz.getName () + "$__attributeRepository", true, clazz.getClassLoader ());
                    repo = (AttributeRepositoryClass) attributeRepo.newInstance ();
                } catch (ClassNotFoundException cnfe) {
                    // OK, just means no repo available, so default to empty one.
                    repo = EmptyAttributeRepositoryClass.INSTANCE;
                } catch (InstantiationException ie) {
                    throw new RepositoryError (ie);
                } catch (IllegalAccessException iae) {
                    throw new RepositoryError (iae);
                }
                cached = new DefaultCachedRepository (clazz, repo);
                
                classRepositories.put (clazz, cached);
                
                if (repo != null) {
                    Util.validateRepository (clazz, repo);
                }
            } finally {
                initList.remove (initList.size () - 1);
            }
            
            return cached;
        }
    }
    
    /**
     * Selects from a collection of attributes one attribute with a given class.
     *
     * @return the attribute instance, or null of none could be found.
     * @throws MultipleAttributesError if the collection contains more than one
     *         instance of the specified class.
     */
    private static Object getAttribute (Collection attrs, Class attributeClass) throws MultipleAttributesError {
        Object candidate = null;
        Iterator iter = attrs.iterator ();
        while (iter.hasNext ()) {
            Object attr = iter.next ();
            if (attr.getClass () == attributeClass) {
                if (candidate == null) {
                    candidate = attr;
                } else {
                    throw new MultipleAttributesError (attributeClass.getName ());
                }
            }
        }
        
        return candidate;
    }
    
    /**
     * Get one attributes of a given type from a class.
     *
     * @return the attribute instance, or null of none could be found.
     * @throws MultipleAttributesError if the collection contains more than one
     *         instance of the specified class.
     */
    public static Object getAttribute (Class clazz, Class attributeClass) throws RepositoryError, MultipleAttributesError {
        return getAttribute (getAttributes (clazz), attributeClass);
    }
    
    /**
     * Get one attributes of a given type from a field.
     *
     * @return the attribute instance, or null of none could be found.
     * @throws MultipleAttributesError if the collection contains more than one
     *         instance of the specified class.
     */
    public static Object getAttribute (Field field, Class attributeClass) throws RepositoryError, MultipleAttributesError {
        return getAttribute (getAttributes (field), attributeClass);
    }
    
    /**
     * Get one attributes of a given type from a constructor.
     *
     * @return the attribute instance, or null of none could be found.
     * @throws MultipleAttributesError if the collection contains more than one
     *         instance of the specified class.
     */
    public static Object getAttribute (Constructor ctor, Class attributeClass) throws RepositoryError, MultipleAttributesError {
        return getAttribute (getAttributes (ctor), attributeClass);
    }
    
    /**
     * Get one attributes of a given type from a method.
     *
     * @return the attribute instance, or null of none could be found.
     * @throws MultipleAttributesError if the collection contains more than one
     *         instance of the specified class.
     */
    public static Object getAttribute (Method method, Class attributeClass) throws RepositoryError, MultipleAttributesError {
        return getAttribute (getAttributes (method), attributeClass);
    }
    
    /**
     * Get one attributes of a given type from a parameter.
     *
     * @return the attribute instance, or null of none could be found.
     * @throws MultipleAttributesError if the collection contains more than one
     *         instance of the specified class.
     */
    public static Object getParameterAttribute (Method method, int parameter, Class attributeClass) throws RepositoryError, MultipleAttributesError {
        return getAttribute (getParameterAttributes (method, parameter), attributeClass);
    }
    
    /**
     * Get one attributes of a given type from a constructor's parameter.
     *
     * @return the attribute instance, or null of none could be found.
     * @throws MultipleAttributesError if the collection contains more than one
     *         instance of the specified class.
     */
    public static Object getParameterAttribute (Constructor constructor, int parameter, Class attributeClass) throws RepositoryError, MultipleAttributesError {
        return getAttribute (getParameterAttributes (constructor, parameter), attributeClass);
    }
    
    /**
     * Get one attributes of a given type from a method's return value.
     *
     * @return the attribute instance, or null of none could be found.
     * @throws MultipleAttributesError if the collection contains more than one
     *         instance of the specified class.
     */
    public static Object getReturnAttribute (Method method, Class attributeClass) throws RepositoryError, MultipleAttributesError {
        return getAttribute (getReturnAttributes (method), attributeClass);
    }
    
    /**
     * Gets all attributes for a class.
     */
    public static Collection getAttributes (Class clazz) throws RepositoryError {
        if (clazz == null) {
            throw new NullPointerException ("clazz");
        }
        
        return getCachedRepository (clazz).getAttributes ();
    }
    
    /**
     * Gets all attributes for a method.
     */
    public static Collection getAttributes (Method method) throws RepositoryError {
        if (method == null) {
            throw new NullPointerException ("method");
        }
        
        return getCachedRepository (method.getDeclaringClass()).getAttributes (method);
    }
    
    /**
     * Gets all attributes for a parameter of a method.
     */
    public static Collection getParameterAttributes (Method method, int parameter) throws RepositoryError {
        return getCachedRepository (method.getDeclaringClass()).getParameterAttributes (method, parameter);
    }
    
    /**
     * Gets all attributes for a parameter of a constructor.
     */
    public static Collection getParameterAttributes (Constructor constructor, int parameter) throws RepositoryError {
        if (constructor == null) {
            throw new NullPointerException ("constructor");
        }
        return getCachedRepository (constructor.getDeclaringClass()).getParameterAttributes (constructor, parameter);
    }
    
    /**
     * Gets all attributes for the return value of a method.
     */
    public static Collection getReturnAttributes (Method method) throws RepositoryError {
        if (method == null) {
            throw new NullPointerException ("method");
        }
        return getCachedRepository (method.getDeclaringClass()).getReturnAttributes (method);
    }
    
    /**
     * Gets all attributes for a field.
     */
    public static Collection getAttributes (Field field) throws RepositoryError {
        if (field == null) {
            throw new NullPointerException ("field");
        }
        return getCachedRepository (field.getDeclaringClass()).getAttributes (field);
    }
    
    /**
     * Gets all attributes for a constructor.
     */
    public static Collection getAttributes (Constructor cons) throws RepositoryError {
        if (cons == null) {
            throw new NullPointerException ("cons");
        }
        return getCachedRepository (cons.getDeclaringClass()).getAttributes (cons);
    }
    
    /**
     * Selects from a collection of attributes only those with a given class.
     */
    private static Collection getAttributes (Collection attrs, Class attributeClass) {
        HashSet result = new HashSet ();
        Iterator iter = attrs.iterator ();
        while (iter.hasNext ()) {
            Object attr = iter.next ();
            if (attr.getClass () == attributeClass) {
                result.add (attr);
            }
        }
        
        return Collections.unmodifiableCollection (result);
    }
    
    /**
     * Get all attributes of a given type from a class. For all objects o in the returned 
     * collection, <code>o.getClass() == attributeClass</code>.
     */
    public static Collection getAttributes (Class clazz, Class attributeClass) throws RepositoryError {
        return getAttributes (getAttributes (clazz), attributeClass);
    }
    
    /**
     * Get all attributes of a given type from a field. For all objects o in the returned 
     * collection, <code>o.getClass() == attributeClass</code>.
     */
    public static Collection getAttributes (Field field, Class attributeClass) throws RepositoryError {
        return getAttributes (getAttributes (field), attributeClass);
    }
    
    /**
     * Get all attributes of a given type from a constructor. For all objects o in the returned 
     * collection, <code>o.getClass() == attributeClass</code>.
     */
    public static Collection getAttributes (Constructor ctor, Class attributeClass) throws RepositoryError {
        return getAttributes (getAttributes (ctor), attributeClass);
    }
    
    /**
     * Get all attributes of a given type from a method. For all objects o in the returned 
     * collection, <code>o.getClass() == attributeClass</code>.
     */
    public static Collection getAttributes (Method method, Class attributeClass) throws RepositoryError {
        return getAttributes (getAttributes (method), attributeClass);
    }
    
    /**
     * Get all attributes of a given type from a method's parameter. For all objects o in the returned 
     * collection, <code>o.getClass() == attributeClass</code>.
     */
    public static Collection getParameterAttributes (Method method, int parameter, Class attributeClass) throws RepositoryError {
        return getAttributes (getParameterAttributes (method, parameter), attributeClass);
    }
    
    /**
     * Get all attributes of a given type from a method's parameter. For all objects o in the returned 
     * collection, <code>o.getClass() == attributeClass</code>.
     */
    public static Collection getParameterAttributes (Constructor constructor, int parameter, Class attributeClass) throws RepositoryError {
        return getAttributes (getParameterAttributes (constructor, parameter), attributeClass);
    }
    
    /**
     * Get all attributes of a given type from a method's return value. For all objects o in the returned 
     * collection, <code>o.getClass() == attributeClass</code>.
     */
    public static Collection getReturnAttributes (Method method, Class attributeClass) throws RepositoryError {
        return getAttributes (getReturnAttributes (method), attributeClass);
    }
    
    /**
     * Convenience function to test whether a collection of attributes contain
     * an attribute of a given class.
     */
    private static boolean hasAttributeType (Collection attrs, Class attributeClass) {
        Iterator iter = attrs.iterator ();
        while (iter.hasNext ()) {
            Object attr = iter.next ();
            if (attr.getClass () == attributeClass) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Tests if a class has an attribute of a given type. That is, is there any attribute
     * <code>attr</code> such that <code>attr.getClass() == attributeClass</code>?
     */
    public static boolean hasAttributeType (Class clazz, Class attributeClass) throws RepositoryError {
        return hasAttributeType (getAttributes (clazz), attributeClass);
    }
    
    /**
     * Tests if a field has an attribute of a given type. That is, is there any attribute
     * <code>attr</code> such that <code>attr.getClass() == attributeClass</code>?
     */
    public static boolean hasAttributeType (Field field, Class attributeClass) throws RepositoryError {
        return hasAttributeType (getAttributes (field), attributeClass);
    }
    
    /**
     * Tests if a constructor has an attribute of a given type. That is, is there any attribute
     * <code>attr</code> such that <code>attr.getClass() == attributeClass</code>?
     */
    public static boolean hasAttributeType (Constructor ctor, Class attributeClass) throws RepositoryError {
        return hasAttributeType (getAttributes (ctor), attributeClass);
    }
    
    /**
     * Tests if a method has an attribute of a given type. That is, is there any attribute
     * <code>attr</code> such that <code>attr.getClass() == attributeClass</code>?
     */
    public static boolean hasAttributeType (Method method, Class attributeClass) throws RepositoryError {
        return hasAttributeType (getAttributes (method), attributeClass);
    }
    
    /**
     * Tests if a method's parameter has an attribute of a given type. That is, is there any attribute
     * <code>attr</code> such that <code>attr.getClass() == attributeClass</code>?
     */
    public static boolean hasParameterAttributeType (Method method, int parameter, Class attributeClass) throws RepositoryError {
        return hasAttributeType (getParameterAttributes (method, parameter), attributeClass);
    }
    
    /**
     * Tests if a constructor's parameter has an attribute of a given type. That is, is there any attribute
     * <code>attr</code> such that <code>attr.getClass() == attributeClass</code>?
     */
    public static boolean hasParameterAttributeType (Constructor constructor, int parameter, Class attributeClass) throws RepositoryError {
        return hasAttributeType (getParameterAttributes (constructor, parameter), attributeClass);
    }
    
    /**
     * Tests if a method's return value has an attribute of a given type. That is, is there any attribute
     * <code>attr</code> such that <code>attr.getClass() == attributeClass</code>?
     */
    public static boolean hasReturnAttributeType (Method method, Class attributeClass) throws RepositoryError {
        return hasAttributeType (getReturnAttributes (method), attributeClass);
    }
    
    /**
     * Convenience function to test whether a collection of attributes contain
     * an attribute.
     */
    private static boolean hasAttribute (Collection attrs, Object attribute) throws RepositoryError {
        return attrs.contains (attribute);
    }
    
    /**
     * Tests if a class has an attribute. That is, is there any attribute
     * <code>attr</code> such that <code>attr.equals(attribute)</code>?
     */
    public static boolean hasAttribute (Class clazz, Object attribute) throws RepositoryError {
        return hasAttribute (getAttributes (clazz), attribute);
    }
    
    /**
     * Tests if a field has an attribute. That is, is there any attribute
     * <code>attr</code> such that <code>attr.equals(attribute)</code>?
     */
    public static boolean hasAttribute (Field field, Object attribute) throws RepositoryError {
        return hasAttribute (getAttributes (field), attribute);
    }
    
    /**
     * Tests if a constructor has an attribute. That is, is there any attribute
     * <code>attr</code> such that <code>attr.equals(attribute)</code>?
     */
    public static boolean hasAttribute (Constructor ctor, Object attribute) throws RepositoryError {
        return hasAttribute (getAttributes (ctor), attribute);
    }
    
    /**
     * Tests if a method has an attribute. That is, is there any attribute
     * <code>attr</code> such that <code>attr.equals(attribute)</code>?
     */
    public static boolean hasAttribute (Method method, Object attribute) throws RepositoryError {
        return hasAttribute (getAttributes (method), attribute);
    }
    
    /**
     * Tests if a method's parameter has an attribute. That is, is there any attribute
     * <code>attr</code> such that <code>attr.equals(attribute)</code>?
     */
    public static boolean hasParameterAttribute (Method method, int parameter, Object attribute) throws RepositoryError {
        return hasAttribute (getParameterAttributes (method, parameter), attribute);
    }
    
    /**
     * Tests if a constructor's parameter has an attribute. That is, is there any attribute
     * <code>attr</code> such that <code>attr.equals(attribute)</code>?
     */
    public static boolean hasParameterAttribute (Constructor constructor, int parameter, Object attribute) throws RepositoryError {
        return hasAttribute (getParameterAttributes (constructor, parameter), attribute);
    }
    
    /**
     * Tests if a method's return value has an attribute. That is, is there any attribute
     * <code>attr</code> such that <code>attr.equals(attribute)</code>?
     */
    public static boolean hasReturnAttribute (Method method, Object attribute) throws RepositoryError {
        return hasAttribute (getReturnAttributes (method), attribute);
    }
    
    /**
     * Set attributes for a given class. The class must not have attributes set for it already
     * (i.e. you can't redefine the attributes of a class at runtime). This because it
     * really messes up the concept of inheritable attributes, and because the attribute set
     * associated with a class is immutable, like the set of methods and fields.
     *
     * @param repo The repository. The repository will be sealed before any attributes are
     *             set. This to guarantee that the repository remains constant
     *             during setting.
     * @throws IllegalStateException if the class whose attributes are defined already have
     *         attributes defined for it (even if it has no attributes).
     */
    public static synchronized void setAttributes (RuntimeAttributeRepository repo) throws IllegalStateException {
        repo.seal ();
        
        Class clazz = repo.getDefinedClass ();
        if (classRepositories.get (clazz) != null) {
            throw new IllegalStateException (clazz.getName ());
        }
        Util.validateRepository (clazz, repo);
        
        DefaultCachedRepository cached = new DefaultCachedRepository (clazz, repo);
        classRepositories.put (clazz, cached);
    }
}