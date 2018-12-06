package com.github.netty.core.util;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * 反射工具类.
 * 提供调用getter/setter方法, 访问私有变量, 调用私有方法, 获取泛型类型Class, 被AOP过的真实类等工具函数.
 * @author acer01
 *
 */
@SuppressWarnings("rawtypes")
public class ReflectUtil {

    private static final String POINT = ".";
	
	private static final String SETTER_PREFIX = "set";

	private static final String GETTER_PREFIX = "get";

	private static final String CGLIB_CLASS_SEPARATOR = "$$";

	/** Suffix for array class names: {@code "[]"}. */
	public static final String ARRAY_SUFFIX = "[]";

	/** Prefix for internal array class names: {@code "["}. */
	private static final String INTERNAL_ARRAY_PREFIX = "[";

	/** Prefix for internal non-primitive array class names: {@code "[L"}. */
	private static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L";

	/** The package separator character: {@code '.'}. */
	private static final char PACKAGE_SEPARATOR = '.';

	/** The path separator character: {@code '/'}. */
	private static final char PATH_SEPARATOR = '/';

	/** The inner class separator character: {@code '$'}. */
	private static final char INNER_CLASS_SEPARATOR = '$';

	/** The ".class" file suffix. */
	public static final String CLASS_FILE_SUFFIX = ".class";
	private static final Set<Class<?>> javaLanguageInterfaces;


	/**
	 * Map with primitive wrapper type as key and corresponding primitive
	 * type as value, for example: Integer.class -> int.class.
	 */
	private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new IdentityHashMap<>(8);

	/**
	 * Map with primitive type as key and corresponding wrapper
	 * type as value, for example: int.class -> Integer.class.
	 */
	private static final Map<Class<?>, Class<?>> primitiveTypeToWrapperMap = new IdentityHashMap<>(8);

	/**
	 * Map with primitive type name as key and corresponding primitive
	 * type as value, for example: "int" -> "int.class".
	 */
	private static final Map<String, Class<?>> primitiveTypeNameMap = new HashMap<>(32);

	/**
	 * Map with common Java language class name as key and corresponding Class as value.
	 * Primarily for efficient deserialization of remote invocations.
	 */
	private static final Map<String, Class<?>> commonClassCache = new HashMap<>(64);


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
			result = primitiveTypeNameMap.get(name);
		}
		return result;
	}

	public static Class<?> forName(String name, ClassLoader classLoader)
			throws ClassNotFoundException, LinkageError {

		Objects.requireNonNull(name, "Name must not be null");

		Class<?> clazz = resolvePrimitiveClassName(name);
		if (clazz == null) {
			clazz = commonClassCache.get(name);
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
						name.substring(0, lastDotIndex) + INNER_CLASS_SEPARATOR + name.substring(lastDotIndex + 1);
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

	/**
	 * 调用Getter方法.
	 * 支持多级，如：对象名.对象名.方法
	 */
	public static Object invokeGetter(Object obj, String propertyName) {
		Object object = obj;
		String[] splits = propertyName.split(POINT);

		for (String name :splits){
			String getterMethodName = GETTER_PREFIX + StringUtil.capitalize(name);
			object = invokeMethod(object, getterMethodName, new Class[] {}, new Object[] {});
		}
		return object;
	}

	/**
	 * 调用Setter方法, 仅匹配方法名。
	 * 支持多级，如：对象名.对象名.方法
	 */
	public static void invokeSetter(Object obj, String propertyName, Object value) {
		Object object = obj;
		String[] names = propertyName.split( ".");

		for (int i=0; i<names.length; i++){
			if(i<names.length-1){
				String getterMethodName = GETTER_PREFIX + StringUtil.capitalize(names[i]);
				object = invokeMethod(object, getterMethodName, new Class[] {}, new Object[] {});
			}else{
				String setterMethodName = SETTER_PREFIX + StringUtil.capitalize(names[i]);
				invokeMethodByName(object, setterMethodName, new Object[] { value });
			}
		}
	}

	/**
	 * 直接读取对象属性值, 无视private/protected修饰符, 不经过getter函数.
	 */
	public static Object getFieldValue(final Object obj, final String fieldName) {
		Field field = getAccessibleField(obj, fieldName);
		if (field == null) {
			throw new IllegalArgumentException("在 [" + obj.getClass() + "] 中，没有找到 [" + fieldName + "] 字段 ");
		}
		Object result = null;
		try {
			result = field.get(obj);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * 直接设置对象属性值, 无视private/protected修饰符, 不经过setter函数.
	 */
	public static void setFieldValue(final Object obj, final String fieldName, final Object value) {
		Field field = getAccessibleField(obj, fieldName);
		if (field == null) {
			throw new IllegalArgumentException("在 [" + obj.getClass() + "] 中，没有找到 [" + fieldName + "] 字段 ");
		}
		try {
			field.set(obj, value);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}

    /**
     * 执行任意方法一次
     * @param obj
     * @param methodList
     * @param args
     * @return
     */
	public static Object invokeMethodOnce(Object obj, List<Method> methodList, Object...args) {
		try {
			for(Method method : methodList) {
				if(method != null) {
					return method.invoke(obj, args);
				}
			}
		} catch (Exception e) {
		    //
		}
        return null;
	}

    /**
     * 执行方法
     * @param obj
     * @param method
     * @param args
     * @return
     */
    public static Object invokeMethod(Object obj, Method method, Object...args) {
        try {
            if(method != null) {
                return method.invoke(obj, args);
            }
        } catch (Exception e) {
            //
        }
        return null;
    }

	/**
	 * 直接调用对象方法, 无视private/protected修饰符.
	 * 用于一次性调用的情况，否则应使用getAccessibleMethod()函数获得Method后反复调用.
	 * 同时匹配方法名+参数类型，
	 */
	public static Object invokeMethod(final Object obj, final String methodName, final Class<?>[] parameterTypes,
			final Object[] args) {
		if (obj == null || methodName == null){
			return null;
		}
		Method method = getAccessibleMethod(obj.getClass(), methodName, parameterTypes);
		if (method == null) {
			throw new IllegalArgumentException("在 [" + obj.getClass() + "] 中，没有找到 [" + methodName + "] 方法 ");
		}
		try {
			return method.invoke(obj, args);
		} catch (Exception e) {
			String msg = "method: "+method+", obj: "+obj+", args: "+args+"";
			throw convertReflectionExceptionToUnchecked(msg, e);
		}
	}

	/**
	 * 直接调用对象方法, 无视private/protected修饰符，
	 * 用于一次性调用的情况，否则应使用getAccessibleMethodByName()函数获得Method后反复调用.
	 * 只匹配函数名，如果有多个同名函数调用第一个。
	 */
	public static Object invokeMethodByName(final Object obj, final String methodName, final Object[] args) {
		Method method = getAccessibleMethodByName(obj.getClass(), methodName, args.length);
		if (method == null) {
			throw new IllegalArgumentException("在 [" + obj.getClass() + "] 中，没有找到 [" + methodName + "] 方法 ");
		}
		try {
			return method.invoke(obj, args);
		} catch (Exception e) {
			String msg = "method: "+method+", obj: "+obj+", args: "+args+"";
			throw convertReflectionExceptionToUnchecked(msg, e);
		}
	}

	/**
	 * 循环向上转型, 获取对象的DeclaredField, 并强制设置为可访问.
	 * 如向上转型到Object仍无法找到, 返回null.
	 */
	public static Field getAccessibleField(final Object obj, final String fieldName) {
		Objects.requireNonNull(obj, "object can't be null");
		Objects.requireNonNull(fieldName, "fieldName can't be blank");
		for (Class<?> superClass = obj.getClass(); superClass != Object.class; superClass = superClass.getSuperclass()) {
			try {
				Field field = superClass.getDeclaredField(fieldName);
				makeAccessible(field);
				return field;
			} catch (NoSuchFieldException e) {//NOSONAR
				// Field不在当前类定义,继续向上转型
			}
		}
		return null;
	}

	/**
	 * 循环向上转型, 获取对象的DeclaredMethod,并强制设置为可访问.
	 * 如向上转型到Object仍无法找到, 返回null.
	 * 匹配函数名+参数类型。
	 * 用于方法需要被多次调用的情况. 先使用本函数先取得Method,然后调用Method.invoke(Object obj, Object... args)
	 */
	public static Method getAccessibleMethod(final Class clazz, final String methodName,
			final Class<?>... parameterTypes) {
		Objects.requireNonNull(methodName, "methodName can't be blank");
		for (Class<?> searchType = clazz; searchType != Object.class; searchType = searchType.getSuperclass()) {
			try {
				Method method = searchType.getDeclaredMethod(methodName, parameterTypes);
				makeAccessible(method);
				return method;
			} catch (NoSuchMethodException e) {
				// Method不在当前类定义,继续向上转型
			}
		}
		return null;
	}

	/**
	 * 循环向上转型, 获取对象的DeclaredMethod,并强制设置为可访问.
	 * 如向上转型到Object仍无法找到, 返回null.
	 * 只匹配函数名。
	 * 用于方法需要被多次调用的情况. 先使用本函数先取得Method,然后调用Method.invoke(Object obj, Object... args)
	 */
	public static Method getAccessibleMethodByName(final Class clazz, final String methodName, int argsNum) {
		Objects.requireNonNull(clazz, "object can't be null");
		Objects.requireNonNull(methodName, "methodName can't be blank");
		for (Class<?> searchType = clazz; searchType != Object.class; searchType = searchType.getSuperclass()) {
			Method[] methods = searchType.getDeclaredMethods();
			for (Method method : methods) {
                Class<?>[]  types = method.getParameterTypes();

				if (method.getName().equals(methodName) && types.length == argsNum) {
					makeAccessible(method);
					return method;
				}
			}
		}
		return null;
	}

	/**
	 * 改变private/protected的方法为public，尽量不调用实际改动的语句，避免JDK的SecurityManager抱怨。
	 */
	public static void makeAccessible(Method method) {
		boolean accessBoolean = (!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers())) && !method.isAccessible();
		if (accessBoolean) {
			method.setAccessible(true);
		}
	}

	/**
	 * 改变private/protected的成员变量为public，尽量不调用实际改动的语句，避免JDK的SecurityManager抱怨。
	 */
	public static void makeAccessible(Field field) {
//	    boolean accessBoolean = (!Modifier.isPublic(field.getModifiers()) || !Modifier.isPublic(field.getDeclaringClass().getModifiers()) || Modifier.isFinal(field.getModifiers())) && !field.isAccessible();
//		if (accessBoolean) {
			field.setAccessible(true);
//		}
	}

	/**
	 * 通过反射, 获得Class定义中声明的泛型参数的类型, 注意泛型必须定义在父类处
	 * 如无法找到, 返回Object.class.
	 * eg.
	 * public UserDao extends HibernateDao<User>
	 * @param clazz The class to introspect
	 * @return the first generic declaration, or Object.class if cannot be determined
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<T> getClassGenricType(final Class clazz) {
		return getClassGenricType(clazz, 0);
	}

	/**
	 * 通过反射, 获得Class定义中声明的父类的泛型参数的类型.
	 * 如无法找到, 返回Object.class.
	 * 如public UserDao extends HibernateDao<User,Long>
	 * @param clazz clazz The class to introspect
	 * @param index the Index of the generic ddeclaration,start from 0.
	 * @return the index generic declaration, or Object.class if cannot be determined
	 */
	private static Class getClassGenricType(final Class clazz, final int index) {

		Type genType = clazz.getGenericSuperclass();

		if (!(genType instanceof ParameterizedType)) {

			return Object.class;
		}

		Type[] params = ((ParameterizedType) genType).getActualTypeArguments();

		if (index >= params.length || index < 0) {
			return Object.class;
		}
		if (!(params[index] instanceof Class)) {
			return Object.class;
		}

		return (Class) params[index];
	}

	/**
	 * 将反射时的checked exception转换为unchecked exception.
	 */
	private static RuntimeException convertReflectionExceptionToUnchecked(String msg, Exception e) {
		if (e instanceof IllegalAccessException || e instanceof IllegalArgumentException
				|| e instanceof NoSuchMethodException) {
			return new IllegalArgumentException(msg, e);
		} else if (e instanceof InvocationTargetException) {
			return new RuntimeException(msg, ((InvocationTargetException) e).getTargetException());
		}
		return new RuntimeException(msg, e);
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
		return className.substring(lastDotIndex + 1) + CLASS_FILE_SUFFIX;
	}

	private static void registerCommonClasses(Class<?>... commonClasses) {
		for (Class<?> clazz : commonClasses) {
			commonClassCache.put(clazz.getName(), clazz);
		}
	}


	static {
		primitiveWrapperTypeMap.put(Boolean.class, boolean.class);
		primitiveWrapperTypeMap.put(Byte.class, byte.class);
		primitiveWrapperTypeMap.put(Character.class, char.class);
		primitiveWrapperTypeMap.put(Double.class, double.class);
		primitiveWrapperTypeMap.put(Float.class, float.class);
		primitiveWrapperTypeMap.put(Integer.class, int.class);
		primitiveWrapperTypeMap.put(Long.class, long.class);
		primitiveWrapperTypeMap.put(Short.class, short.class);

		// Map entry iteration is less expensive to initialize than forEach with lambdas
		for (Map.Entry<Class<?>, Class<?>> entry : primitiveWrapperTypeMap.entrySet()) {
			primitiveTypeToWrapperMap.put(entry.getValue(), entry.getKey());
			registerCommonClasses(entry.getKey());
		}

		Set<Class<?>> primitiveTypes = new HashSet<>(32);
		primitiveTypes.addAll(primitiveWrapperTypeMap.values());
		Collections.addAll(primitiveTypes, boolean[].class, byte[].class, char[].class,
				double[].class, float[].class, int[].class, long[].class, short[].class);
		primitiveTypes.add(void.class);
		for (Class<?> primitiveType : primitiveTypes) {
			primitiveTypeNameMap.put(primitiveType.getName(), primitiveType);
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
		javaLanguageInterfaces = new HashSet<>(Arrays.asList(javaLanguageInterfaceArray));
	}

}
