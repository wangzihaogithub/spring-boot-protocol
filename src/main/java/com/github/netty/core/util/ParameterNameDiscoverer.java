package com.github.netty.core.util;

import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 基于ASM参数名称解析
 * @author 84215
 */
public class ParameterNameDiscoverer {

    private LoggerX logger = new LoggerX(getClass());

    static final int ASM_VERSION = Opcodes.ASM7;

    // marker object for classes that do not have any debug info
    private static final Map<Member, String[]> NO_DEBUG_INFO_MAP = Collections.emptyMap();

    // the cache uses a nested index (value is a map) to keep the top level cache relatively small in size
    private final Map<Class<?>, Map<Member, String[]>> parameterNamesCache = new ConcurrentHashMap<>(32);

    private static final Map<Class<?>, Method[]> declaredMethodsCache = new ConcurrentReferenceHashMap<>(256);

    /**
     * Cache for {@link Class#getDeclaredFields()}, allowing for fast iteration.
     */
//    private static final Map<Class<?>, Field[]> declaredFieldsCache = new ConcurrentReferenceHashMap<>(256);
    private static final Method[] NO_METHODS = {};

    public String[] getParameterNames(Method method) {
        Method originalMethod = findBridgedMethod(method);
        Class<?> declaringClass = originalMethod.getDeclaringClass();
        Map<Member, String[]> map = this.parameterNamesCache.get(declaringClass);
        if (map == null) {
            map = inspectClass(declaringClass);
            this.parameterNamesCache.put(declaringClass, map);
        }
        if (map != NO_DEBUG_INFO_MAP) {
            return map.get(originalMethod);
        }
        return null;
    }

    public String[] getParameterNames(Constructor<?> ctor) {
        Class<?> declaringClass = ctor.getDeclaringClass();
        Map<Member, String[]> map = this.parameterNamesCache.get(declaringClass);
        if (map == null) {
            map = inspectClass(declaringClass);
            this.parameterNamesCache.put(declaringClass, map);
        }
        if (map != NO_DEBUG_INFO_MAP) {
            return map.get(ctor);
        }
        return null;
    }

    /**
     * Inspects the target class. Exceptions will be logged and a maker map returned
     * to indicate the lack of debug information.
     */
    private Map<Member, String[]> inspectClass(Class<?> clazz) {
        InputStream is = clazz.getResourceAsStream(ReflectUtil.getClassFileName(clazz));
        if (is == null) {
            // We couldn't load the class file, which is not fatal as it
            // simply means this method of discovering parameter names won't work.
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot find '.class' file for class [" + clazz +
                        "] - unable to determine constructor/method parameter names");
            }
            return NO_DEBUG_INFO_MAP;
        }
        try {
            ClassReader classReader = new ClassReader(is);
            Map<Member, String[]> map = new ConcurrentHashMap<>(32);
            classReader.accept(new ParameterNameDiscoveringVisitor(clazz, map), 0);
            return map;
        }
        catch (IOException ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Exception thrown while reading '.class' file for class [" + clazz +
                        "] - unable to determine constructor/method parameter names", ex);
            }
        }
        catch (IllegalArgumentException ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("ASM ClassReader failed to parse class file [" + clazz +
                        "], probably due to a new Java class file version that isn't supported yet " +
                        "- unable to determine constructor/method parameter names", ex);
            }
        }
        finally {
            try {
                is.close();
            }
            catch (IOException ex) {
                // ignore
            }
        }
        return NO_DEBUG_INFO_MAP;
    }

    /**
     * Helper class that inspects all methods (constructor included) and then
     * attempts to find the parameter names for that member.
     */
    private static class ParameterNameDiscoveringVisitor extends ClassVisitor {

        private static final String STATIC_CLASS_INIT = "<clinit>";

        private final Class<?> clazz;

        private final Map<Member, String[]> memberMap;

        public ParameterNameDiscoveringVisitor(Class<?> clazz, Map<Member, String[]> memberMap) {
            super(ASM_VERSION);
            this.clazz = clazz;
            this.memberMap = memberMap;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // exclude synthetic + bridged && static class initialization
            if (!isSyntheticOrBridged(access) && !STATIC_CLASS_INIT.equals(name)) {
                return new LocalVariableTableVisitor(this.clazz, this.memberMap, name, desc, isStatic(access));
            }
            return null;
        }

        private static boolean isSyntheticOrBridged(int access) {
            return (((access & Opcodes.ACC_SYNTHETIC) | (access & Opcodes.ACC_BRIDGE)) > 0);
        }

        private static boolean isStatic(int access) {
            return ((access & Opcodes.ACC_STATIC) > 0);
        }
    }


    private static class LocalVariableTableVisitor extends MethodVisitor {

        private static final String CONSTRUCTOR = "<init>";

        private final Class<?> clazz;

        private final Map<Member, String[]> memberMap;

        private final String name;

        private final Type[] args;

        private final String[] parameterNames;

        private final boolean isStatic;

        private boolean hasLvtInfo = false;

        /*
         * The nth entry contains the slot index of the LVT table entry holding the
         * argument name for the nth parameter.
         */
        private final int[] lvtSlotIndex;

        public LocalVariableTableVisitor(Class<?> clazz, Map<Member, String[]> map, String name, String desc, boolean isStatic) {
            super(ASM_VERSION);
            this.clazz = clazz;
            this.memberMap = map;
            this.name = name;
            this.args = Type.getArgumentTypes(desc);
            this.parameterNames = new String[this.args.length];
            this.isStatic = isStatic;
            this.lvtSlotIndex = computeLvtSlotIndices(isStatic, this.args);
        }

        @Override
        public void visitLocalVariable(String name, String description, String signature, Label start, Label end, int index) {
            this.hasLvtInfo = true;
            for (int i = 0; i < this.lvtSlotIndex.length; i++) {
                if (this.lvtSlotIndex[i] == index) {
                    this.parameterNames[i] = name;
                }
            }
        }

        @Override
        public void visitEnd() {
            if (this.hasLvtInfo || (this.isStatic && this.parameterNames.length == 0)) {
                // visitLocalVariable will never be called for static no args methods
                // which doesn't use any local variables.
                // This means that hasLvtInfo could be false for that kind of methods
                // even if the class has local variable info.
                this.memberMap.put(resolveMember(), this.parameterNames);
            }
        }

        private Member resolveMember() {
            ClassLoader loader = this.clazz.getClassLoader();
            Class<?>[] argTypes = new Class<?>[this.args.length];
            for (int i = 0; i < this.args.length; i++) {
                argTypes[i] = ReflectUtil.resolveClassName(this.args[i].getClassName(), loader);
            }
            try {
                if (CONSTRUCTOR.equals(this.name)) {
                    return this.clazz.getDeclaredConstructor(argTypes);
                }
                return this.clazz.getDeclaredMethod(this.name, argTypes);
            }
            catch (NoSuchMethodException ex) {
                throw new IllegalStateException("Method [" + this.name +
                        "] was discovered in the .class file but cannot be resolved in the class object", ex);
            }
        }

        private static int[] computeLvtSlotIndices(boolean isStatic, Type[] paramTypes) {
            int[] lvtIndex = new int[paramTypes.length];
            int nextIndex = (isStatic ? 0 : 1);
            for (int i = 0; i < paramTypes.length; i++) {
                lvtIndex[i] = nextIndex;
                if (isWideType(paramTypes[i])) {
                    nextIndex += 2;
                }
                else {
                    nextIndex++;
                }
            }
            return lvtIndex;
        }

        private static boolean isWideType(Type aType) {
            // float is not a wide type
            return (aType == Type.LONG_TYPE || aType == Type.DOUBLE_TYPE);
        }
    }

    public static Method[] getAllDeclaredMethods(Class<?> leafClass) {
        final List<Method> methods = new ArrayList<>(32);
        doWithMethods(leafClass, methods::add,null);
        return methods.toArray(new Method[0]);
    }

    private static List<Method> findConcreteMethodsOnInterfaces(Class<?> clazz) {
        List<Method> result = null;
        for (Class<?> ifc : clazz.getInterfaces()) {
            for (Method ifcMethod : ifc.getMethods()) {
                if (!Modifier.isAbstract(ifcMethod.getModifiers())) {
                    if (result == null) {
                        result = new LinkedList<>();
                    }
                    result.add(ifcMethod);
                }
            }
        }
        return result;
    }

    private static Method[] getDeclaredMethods(Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class must not be null");
        Method[] result = declaredMethodsCache.get(clazz);
        if (result == null) {
            try {
                Method[] declaredMethods = clazz.getDeclaredMethods();
                List<Method> defaultMethods = findConcreteMethodsOnInterfaces(clazz);
                if (defaultMethods != null) {
                    result = new Method[declaredMethods.length + defaultMethods.size()];
                    System.arraycopy(declaredMethods, 0, result, 0, declaredMethods.length);
                    int index = declaredMethods.length;
                    for (Method defaultMethod : defaultMethods) {
                        result[index] = defaultMethod;
                        index++;
                    }
                }
                else {
                    result = declaredMethods;
                }
                declaredMethodsCache.put(clazz, (result.length == 0 ? NO_METHODS : result));
            }
            catch (Throwable ex) {
                throw new IllegalStateException("Failed to introspect Class [" + clazz.getName() +
                        "] from ClassLoader [" + clazz.getClassLoader() + "]", ex);
            }
        }
        return result;
    }

    public static void doWithMethods(Class<?> clazz, Consumer<Method> mc, Function<Method,Boolean> mf) {
        // Keep backing up the inheritance hierarchy.
        Method[] methods = getDeclaredMethods(clazz);
        for (Method method : methods) {
            if (mf != null && !mf.apply(method)) {
                continue;
            }
            try {
                mc.accept(method);
            }
            catch (Exception ex) {
                throw new IllegalStateException("Not allowed to access method '" + method.getName() + "': " + ex);
            }
        }
        if (clazz.getSuperclass() != null) {
            doWithMethods(clazz.getSuperclass(), mc, mf);
        }
        else if (clazz.isInterface()) {
            for (Class<?> superIfc : clazz.getInterfaces()) {
                doWithMethods(superIfc, mc, mf);
            }
        }
    }

    private static boolean isBridgedCandidateFor(Method candidateMethod, Method bridgeMethod) {
        return (!candidateMethod.isBridge() && !candidateMethod.equals(bridgeMethod) &&
                candidateMethod.getName().equals(bridgeMethod.getName()) &&
                candidateMethod.getParameterCount() == bridgeMethod.getParameterCount());
    }
    
    private static Method findBridgedMethod(Method bridgeMethod) {
        if (!bridgeMethod.isBridge()) {
            return bridgeMethod;
        }

        // Gather all methods with matching name and parameter size.
        List<Method> candidateMethods = new ArrayList<>();
        Method[] methods = getAllDeclaredMethods(bridgeMethod.getDeclaringClass());
        for (Method candidateMethod : methods) {
            if (isBridgedCandidateFor(candidateMethod, bridgeMethod)) {
                candidateMethods.add(candidateMethod);
            }
        }

        // Now perform simple quick check.
        if (candidateMethods.size() == 1) {
            return candidateMethods.get(0);
        }

        // Search for candidate match.
//        Method bridgedMethod = searchCandidates(candidateMethods, bridgeMethod);
//        if (bridgedMethod != null) {
//            // Bridged method found...
//            return bridgedMethod;
//        }
//
        // A bridge method was passed in but we couldn't find the bridged method.
        // Let's proceed with the passed-in method and hope for the best...
        return bridgeMethod;
    }

    private static Method searchCandidates(List<Method> candidateMethods, Method bridgeMethod) {
        if (candidateMethods.isEmpty()) {
            return null;
        }
        Method previousMethod = null;
        boolean sameSig = true;
        for (Method candidateMethod : candidateMethods) {
            if (isBridgeMethodFor(bridgeMethod, candidateMethod, bridgeMethod.getDeclaringClass())) {
                return candidateMethod;
            }
            else if (previousMethod != null) {
                sameSig = sameSig &&
                        Arrays.equals(candidateMethod.getGenericParameterTypes(), previousMethod.getGenericParameterTypes());
            }
            previousMethod = candidateMethod;
        }
        return (sameSig ? candidateMethods.get(0) : null);
    }

    private static boolean isBridgeMethodFor(Method bridgeMethod, Method candidateMethod, Class<?> declaringClass) {
        if (isResolvedTypeMatch(candidateMethod, bridgeMethod, declaringClass)) {
            return true;
        }
        Method method = findGenericDeclaration(bridgeMethod);
        return (method != null && isResolvedTypeMatch(method, candidateMethod, declaringClass));
    }

    private static Method findGenericDeclaration(Method bridgeMethod) {
        // Search parent types for method that has same signature as bridge.
        Class<?> superclass = bridgeMethod.getDeclaringClass().getSuperclass();
        while (superclass != null && Object.class != superclass) {
            Method method = searchForMatch(superclass, bridgeMethod);
            if (method != null && !method.isBridge()) {
                return method;
            }
            superclass = superclass.getSuperclass();
        }

        Class<?>[] interfaces = ReflectUtil.getInterfaces(bridgeMethod.getDeclaringClass());
        return searchInterfaces(interfaces, bridgeMethod);
    }

    private static Method searchInterfaces(Class<?>[] interfaces, Method bridgeMethod) {
        for (Class<?> ifc : interfaces) {
            Method method = searchForMatch(ifc, bridgeMethod);
            if (method != null && !method.isBridge()) {
                return method;
            }
            else {
                method = searchInterfaces(ifc.getInterfaces(), bridgeMethod);
                if (method != null) {
                    return method;
                }
            }
        }
        return null;
    }

    private static Method searchForMatch(Class<?> type, Method bridgeMethod) {
        try {
            return type.getDeclaredMethod(bridgeMethod.getName(), bridgeMethod.getParameterTypes());
        }
        catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private static boolean isResolvedTypeMatch(Method genericMethod, Method candidateMethod, Class<?> declaringClass) {
        java.lang.reflect.Type[] genericParameters = genericMethod.getGenericParameterTypes();
        Class<?>[] candidateParameters = candidateMethod.getParameterTypes();
        if (genericParameters.length != candidateParameters.length) {
            return false;
        }
//        for (int i = 0; i < candidateParameters.length; i++) {
//            ResolvableType genericParameter = ResolvableType.forMethodParameter(genericMethod, i, declaringClass);
//            Class<?> candidateParameter = candidateParameters[i];
//            if (candidateParameter.isArray()) {
//                // An array type: compare the component type.
//                if (!candidateParameter.getComponentType().equals(genericParameter.getComponentType().toClass())) {
//                    return false;
//                }
//            }
//            // A non-array type: compare the type itself.
//            if (!candidateParameter.equals(genericParameter.toClass())) {
//                return false;
//            }
//        }
        return true;
    }
}
