package com.github.netty.core.util;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * ReflectUtil
 * Provide utility functions for calling getter/setter methods, accessing private variables, calling private methods, getting generic type classes, real classes that have been aoped, and so on.
 * @author wangzihao
 */
public class ReflectUtil {

	/** Suffix for array class names: {@code "[]"}. */
	private static final String ARRAY_SUFFIX = "[]";

	/** Prefix for internal array class names: {@code "["}. */
	private static final String INTERNAL_ARRAY_PREFIX = "[";

	/** Prefix for internal non-primitive array class names: {@code "[L"}. */
	private static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L";

	/** The package separator character: {@code '.'}. */
	private static final char PACKAGE_SEPARATOR = '.';

	/**
	 * Map with primitive wrapper type as key and corresponding primitive
	 * type as value, for example: Integer.class -> int.class.
	 */
	private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPER_TYPE_MAP = new IdentityHashMap<>(8);

	/**
	 * Map with primitive type name as key and corresponding primitive
	 * type as value, for example: "int" -> "int.class".
	 */
	private static final Map<String, Class<?>> PRIMITIVE_TYPE_NAME_MAP = new HashMap<>(32);

	/**
	 * Map with common Java language class name as key and corresponding Class as value.
	 * Primarily for efficient deserialization of remote invocations.
	 */
	private static final Map<String, Class<?>> COMMON_CLASS_CACHE = new HashMap<>(64);

	private static final ConcurrentReferenceHashMap<Integer,Method> METHOD_CACHE = new ConcurrentReferenceHashMap<>();

	public static Class<?> resolveClassName(String className, ClassLoader classLoader)
			throws IllegalArgumentException {

		try {
			return forName(className, classLoader);
		}
		catch (IllegalAccessError err) {
			throw new IllegalStateException("Readability mismatch in inheritance hierarchy of class [" +
					className + "]: " + err.getMessage(), err);
		}
		catch (LinkageError err) {
			throw new IllegalArgumentException("Unresolvable class definition for class [" + className + "]", err);
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalArgumentException("Could not find class [" + className + "]", ex);
		}
	}

	private static Class<?> resolvePrimitiveClassName(String name) {
		Class<?> result = null;
		// Most class names will be quite long, considering that they
		// SHOULD sit in a package, so a length check is worthwhile.
		if (name != null && name.length() <= 8) {
			// Could be a primitive - likely.
			result = PRIMITIVE_TYPE_NAME_MAP.get(name);
		}
		return result;
	}

	public static Class<?> forName(String name, ClassLoader classLoader)
			throws ClassNotFoundException, LinkageError {

		Objects.requireNonNull(name, "Name must not be null");

		Class<?> clazz = resolvePrimitiveClassName(name);
		if (clazz == null) {
			clazz = COMMON_CLASS_CACHE.get(name);
		}
		if (clazz != null) {
			return clazz;
		}

		// "java.lang.String[]" style arrays
		if (name.endsWith(ARRAY_SUFFIX)) {
			String elementClassName = name.substring(0, name.length() - ARRAY_SUFFIX.length());
			Class<?> elementClass = forName(elementClassName, classLoader);
			return Array.newInstance(elementClass, 0).getClass();
		}

		// "[Ljava.lang.String;" style arrays
		if (name.startsWith(NON_PRIMITIVE_ARRAY_PREFIX) && name.endsWith(";")) {
			String elementName = name.substring(NON_PRIMITIVE_ARRAY_PREFIX.length(), name.length() - 1);
			Class<?> elementClass = forName(elementName, classLoader);
			return Array.newInstance(elementClass, 0).getClass();
		}

		// "[[I" or "[[Ljava.lang.String;" style arrays
		if (name.startsWith(INTERNAL_ARRAY_PREFIX)) {
			String elementName = name.substring(INTERNAL_ARRAY_PREFIX.length());
			Class<?> elementClass = forName(elementName, classLoader);
			return Array.newInstance(elementClass, 0).getClass();
		}

		ClassLoader clToUse = classLoader;
		if (clToUse == null) {
			clToUse = getDefaultClassLoader();
		}
		try {
			return Class.forName(name, false, clToUse);
		}
		catch (ClassNotFoundException ex) {
			int lastDotIndex = name.lastIndexOf(PACKAGE_SEPARATOR);
			if (lastDotIndex != -1) {
				String innerClassName =
						name.substring(0, lastDotIndex) + '$' + name.substring(lastDotIndex + 1);
				try {
					return Class.forName(innerClassName, false, clToUse);
				}
				catch (ClassNotFoundException ex2) {
					// Swallow - let original exception get through
				}
			}
			throw ex;
		}
	}

	public static ClassLoader getDefaultClassLoader() {
		ClassLoader cl = null;
		try {
			cl = Thread.currentThread().getContextClassLoader();
		}
		catch (Throwable ex) {
			// Cannot access thread context ClassLoader - falling back...
		}
		if (cl == null) {
			// No thread context class loader -> use class loader of this class.
			cl = ReflectUtil.class.getClassLoader();
			if (cl == null) {
				// getClassLoader() returning null indicates the bootstrap ClassLoader
				try {
					cl = ClassLoader.getSystemClassLoader();
				}
				catch (Throwable ex) {
					// Cannot access system ClassLoader - oh well, maybe the caller can live with null...
				}
			}
		}
		return cl;
	}

	public static Class[] getInterfaces(Class sourceClass){
		Set<Class> interfaceList = new HashSet<>();
		for(Class currClass = sourceClass; currClass != null && currClass != Object.class; currClass = currClass.getSuperclass()){
			Collections.addAll(interfaceList,currClass.getInterfaces());
		}
		if(sourceClass.isInterface()){
			interfaceList.add(sourceClass);
		}
		return interfaceList.toArray(new Class[interfaceList.size()]);
	}

	public static boolean hasInterface(Class sourceClass){
		if(sourceClass.isInterface()){
			return true;
		}
		for(Class currClass = sourceClass; currClass != null && currClass != Object.class; currClass = currClass.getSuperclass()){
			Class[] interfaces = currClass.getInterfaces();
			if(interfaces != null && interfaces.length > 0){
				return true;
			}
		}
		return false;
	}

	public static boolean hasParameterAnnotation(Class sourceClass, Collection<Class<? extends Annotation>> parameterAnnotations){
		if(parameterAnnotations == null || parameterAnnotations.isEmpty()){
			return false;
		}
		Class[] interfaces = ReflectUtil.getInterfaces(sourceClass);
		for(Class clazz : interfaces){
			for(Method method : clazz.getMethods()){
				for(Parameter parameter : method.getParameters()){
					for(Class<?extends Annotation> annotationClass : parameterAnnotations) {
						Annotation annotation = parameter.getAnnotation(annotationClass);
						if(annotation != null){
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	public static Class findClassByAnnotation(Class claz, Class<?extends Annotation>ann ){
		Annotation a;
		//类上找
		for (Class clazz = claz; clazz != null; clazz = clazz.getSuperclass()) {
			if(null != (a = clazz.getAnnotation(ann))) {
				return clazz;
			}
		}

		//接口上找
		Class[] interfaces = getInterfaces(claz);
		for(Class i : interfaces){
			for (Class clazz = i; clazz != null; clazz = clazz.getSuperclass()) {
				if(null != (a = clazz.getAnnotation(ann))) {
					return clazz;
				}
			}
		}
		return null;
	}

	public static <A extends Annotation>A  findAnnotation(Class claz, Class<A>ann ){
		Annotation a;
		//类上找
		for (Class clazz = claz; clazz != null; clazz = clazz.getSuperclass()) {
			if(null != (a = clazz.getAnnotation(ann))) {
				return (A) a;
			}
		}

		//接口上找
		Class[] interfaces = getInterfaces(claz);
		for(Class i : interfaces){
			for (Class clazz = i; clazz != null; clazz = clazz.getSuperclass()) {
				if(null != (a = clazz.getAnnotation(ann))) {
					return (A) a;
				}
			}
		}
		return null;
	}

	public static Class<?extends Annotation> findAnnotationClassByAnnotationType(Class clazz, Collection<Class<? extends Annotation>> annotations){
		for(Annotation a : clazz.getAnnotations()){
			Class<?extends Annotation> aClass = a.annotationType();
			for(Class<?extends Annotation> e : annotations){
				if(e.isAssignableFrom(aClass)) {
					return aClass;
				}
			}
		}
		return null;
	}

	public static Map<String,Object> getAnnotationValueMap(Annotation annotation) {
		if(annotation == null){
			return Collections.emptyMap();
		}
		Method[] declaredMethods = annotation.annotationType().getDeclaredMethods();
		Map<String,Object> map = new HashMap<>(declaredMethods.length);
		for (Method method : declaredMethods) {
			if(method.getParameterCount() != 0 || method.getReturnType() == void.class){
				continue;
			}
			boolean isAccessible = method.isAccessible();
			try {
				method.setAccessible(true);
				Object value = method.invoke(annotation);
				map.put(method.getName(),value);
			} catch (IllegalAccessException | InvocationTargetException e) {
				//skip
			}finally {
				method.setAccessible(isAccessible);
			}
		}
		return map;
	}

	/**
	 * Read the object property values directly, ignoring the private/protected modifier, without going through the getter function.
	 * @param obj obj
	 * @param fieldName fieldName
	 * @return ObjectValue
	 */
	public static Object getFieldValue(final Object obj, final String fieldName) {
		Field field = getAccessibleField(obj, fieldName);
		if (field == null) {
			throw new IllegalArgumentException("in [" + obj.getClass() + "] ，not found [" + fieldName + "]  ");
		}
		Object result = null;
		try {
			result = field.get(obj);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("getFieldValue error:"+e,e);
		}
		return result;
	}

	/**
	 * Loop up to get the DeclaredField of the object and force it to be accessible.
	 * if the Object cannot be found even if the Object is transformed upward, null will be returned.
	 * @param obj obj
	 * @param fieldName fieldName
     * @return Field
     */
	public static Field getAccessibleField(final Object obj, final String fieldName) {
		Objects.requireNonNull(obj, "object can't be null");
		Objects.requireNonNull(fieldName, "fieldName can't be blank");
		for (Class<?> superClass = obj.getClass(); superClass != Object.class; superClass = superClass.getSuperclass()) {
			try {
				Field field = superClass.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {

			}
		}
		return null;
	}

	/**
	 * Loop up to get the DeclaredMethod for the object and force it to be accessible.
	 * if the Object cannot be found even if the Object is transformed upward, null will be returned.
	 * matches function name + parameter type. Method. Invoke (Object obj, Object... The args)
	 * @param clazz class
	 * @param methodName methodName
	 * @param parameterTypes parameterTypes
     * @return Method
     */
	public static Method getAccessibleMethod(final Class clazz, final String methodName,
			final Class<?>... parameterTypes) {
		Objects.requireNonNull(methodName, "methodName can't be blank");
		int hash = (clazz.getName().concat(methodName).concat(Arrays.toString(parameterTypes))).hashCode();
		Method oldMethod = METHOD_CACHE.get(hash);
		if(oldMethod != null){
			return oldMethod;
		}
		for (Class<?> searchType = clazz; searchType != Object.class && searchType != null; searchType = searchType.getSuperclass()) {
			try {
				Method method = searchType.getDeclaredMethod(methodName, parameterTypes);
				method.setAccessible(true);
				METHOD_CACHE.put(hash,method);
				return method;
			} catch (NoSuchMethodException e) {
				//
			}
		}
		return null;
	}

	/**
	 * Determine the name of the class file, relative to the containing
	 * package: e.g. "String.class"
	 * @param clazz the class
	 * @return the file name of the ".class" file
	 */
	public static String getClassFileName(Class<?> clazz) {
		Objects.requireNonNull(clazz, "Class must not be null");
		String className = clazz.getName();
		int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
		return className.substring(lastDotIndex + 1) + ".class";
	}

	private static void registerCommonClasses(Class<?>... commonClasses) {
		for (Class<?> clazz : commonClasses) {
			COMMON_CLASS_CACHE.put(clazz.getName(), clazz);
		}
	}


	static {
		PRIMITIVE_WRAPPER_TYPE_MAP.put(Boolean.class, boolean.class);
		PRIMITIVE_WRAPPER_TYPE_MAP.put(Byte.class, byte.class);
		PRIMITIVE_WRAPPER_TYPE_MAP.put(Character.class, char.class);
		PRIMITIVE_WRAPPER_TYPE_MAP.put(Double.class, double.class);
		PRIMITIVE_WRAPPER_TYPE_MAP.put(Float.class, float.class);
		PRIMITIVE_WRAPPER_TYPE_MAP.put(Integer.class, int.class);
		PRIMITIVE_WRAPPER_TYPE_MAP.put(Long.class, long.class);
		PRIMITIVE_WRAPPER_TYPE_MAP.put(Short.class, short.class);

		// Map entry iteration is less expensive to initialize than forEach with lambdas
		for (Map.Entry<Class<?>, Class<?>> entry : PRIMITIVE_WRAPPER_TYPE_MAP.entrySet()) {
			registerCommonClasses(entry.getKey());
		}

		Set<Class<?>> primitiveTypes = new HashSet<>(32);
		primitiveTypes.addAll(PRIMITIVE_WRAPPER_TYPE_MAP.values());
		Collections.addAll(primitiveTypes, boolean[].class, byte[].class, char[].class,
				double[].class, float[].class, int[].class, long[].class, short[].class);
		primitiveTypes.add(void.class);
		for (Class<?> primitiveType : primitiveTypes) {
			PRIMITIVE_TYPE_NAME_MAP.put(primitiveType.getName(), primitiveType);
		}

		registerCommonClasses(Boolean[].class, Byte[].class, Character[].class, Double[].class,
				Float[].class, Integer[].class, Long[].class, Short[].class);
		registerCommonClasses(Number.class, Number[].class, String.class, String[].class,
				Class.class, Class[].class, Object.class, Object[].class);
		registerCommonClasses(Throwable.class, Exception.class, RuntimeException.class,
				Error.class, StackTraceElement.class, StackTraceElement[].class);
		registerCommonClasses(Enum.class, Iterable.class, Iterator.class, Enumeration.class,
				Collection.class, List.class, Set.class, Map.class, Map.Entry.class, Optional.class);

		Class<?>[] javaLanguageInterfaceArray = {Serializable.class, Externalizable.class,
				Closeable.class, AutoCloseable.class, Cloneable.class, Comparable.class};
		registerCommonClasses(javaLanguageInterfaceArray);
	}

}
