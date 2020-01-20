package com.github.netty.core.util;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Locale.ENGLISH;

/**
 * Lightweight container that supports resource injection
 * @author wangzihao
 *  2016/11/11/011
 *
 */
public class ApplicationX {
    private static final String[] EMPTY = {};
    private final Collection<Class<? extends Annotation>> scannerAnnotationList = new HashSet<>(
            Arrays.asList(Resource.class));
    private final Collection<Class<? extends Annotation>> injectAnnotationList = new HashSet<>(
            Arrays.asList(Resource.class));
    private final Collection<Class<? extends Annotation>> initMethodAnnotationList = new HashSet<>(
            Arrays.asList(PostConstruct.class));

    private Supplier<ClassLoader> resourceLoader = ()-> getClass().getClassLoader();
    private final Injector injector = new Injector();
    private final Scanner scanner = new Scanner();
    private final Map<String,BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>();
    private final Map<String,Object> beanInstanceMap = new ConcurrentHashMap<>();
    private final Set<String> beanSkipLifecycles = new HashSet<>();
    private final Map<Class,String[]> beanNameMap = new ConcurrentHashMap<>();
    private final Map<Class,BiFunction<String,BeanDefinition,Object>> beanFactoryMap = new LinkedHashMap<>();
    private final Collection<BeanPostProcessor> beanPostProcessorList = new LinkedHashSet<>();
    private Function<BeanDefinition,String> beanNameGenerator = new DefaultBeanNameGenerator();
    private final BiFunction<String,BeanDefinition,Object> defaultBeanFactory = new DefaultBeanFactory();

    public ApplicationX() {
        initAnnotationConfig(scannerAnnotationList,
                "javax.annotation.Resource");
        initAnnotationConfig(initMethodAnnotationList,
                "javax.annotation.PostConstruct");
        addInstance(this);
    }

    public static void main(String[] args) {
        ApplicationX app = new ApplicationX()
                .scanner("com.github.netty");
        System.out.println("app = " + app);
    }

    private static void initAnnotationConfig(Collection<Class<? extends Annotation>> annotationList,String... classNames){
        for (String className : classNames) {
            try {
                annotationList.add((Class<? extends Annotation>) Class.forName(className));
            } catch (Exception e) {
                //skip
            }
        }
    }

    public void addSkipLifecycle(String beanName){
        beanSkipLifecycles.add(beanName);
    }

    public boolean isLifecycle(String beanName){
        return !beanSkipLifecycles.contains(beanName);
    }

    public ClassLoader getClassLoader() {
        return resourceLoader.get();
    }

    public Supplier<ClassLoader> getResourceLoader() {
        return resourceLoader;
    }

    public void setResourceLoader(Supplier<ClassLoader> resourceLoader) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader);
    }

    public void addInjectAnnotation(Class<? extends Annotation>... classes){
        Collections.addAll(injectAnnotationList, classes);
    }

    public void addScanAnnotation(Class<? extends Annotation>... classes){
        Collections.addAll(scannerAnnotationList, classes);
    }

    public Object addInstance(Object instance){
        return addInstance(instance,true);
    }

    public Object addInstance(Object instance,boolean isLifecycle){
        return addInstance(null,instance,isLifecycle);
    }

    public Object addInstance(String beanName,Object instance,boolean isLifecycle){
        Class beanType = instance.getClass();
        BeanDefinition definition = newBeanDefinition(beanType);
        definition.setBeanSupplier(()->instance);
        if(!isLifecycle) {
            addSkipLifecycle(beanName);
        }
        if(beanName == null){
            beanName = beanNameGenerator.apply(definition);
        }
        addBeanDefinition(beanName,definition);
        Object oldInstance = beanInstanceMap.remove(beanName, instance);
        getBean(beanName);
        return oldInstance;
    }

    public String[] getBeanNamesForType(){
        return beanDefinitionMap.keySet().toArray(new String[0]);
    }

    public ApplicationX scanner(ClassLoader classLoader){
        try {
            for(String rootPackage : scanner.getRootPackageList()){
                scanner.doScan(rootPackage,classLoader,(clazz)->{
                    BeanDefinition definition = newBeanDefinition(clazz);
                    String beanName = beanNameGenerator.apply(definition);
                    addBeanDefinition(beanName,definition);
                });
            }
            for (Map.Entry<String, BeanDefinition> entry : beanDefinitionMap.entrySet()) {
                String beanName = entry.getKey();
                BeanDefinition definition = entry.getValue();
                if(!definition.isLazyInit() && definition.isSingleton()){
                    getBean(beanName);
                }
            }
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("scanner error="+e,e);
        }
    }

    public ApplicationX scanner(String... rootPackage){
        addScanPackage(rootPackage);
        return scanner(getClassLoader());
    }

    public ApplicationX addExcludesPackage(String... excludesPackages){
        if(excludesPackages != null) {
            scanner.getExcludeList().addAll(Arrays.asList(excludesPackages));
        }
        return this;
    }

    public ApplicationX addScanPackage(String...rootPackages){
        if(rootPackages != null) {
            scanner.getRootPackageList().addAll(Arrays.asList(rootPackages));
        }
        return this;
    }

    public ApplicationX addBeanPostProcessor(BeanPostProcessor beanPostProcessor){
        addInstance(beanPostProcessor,true);
        beanPostProcessorList.add(beanPostProcessor);
        return this;
    }

    public ApplicationX addBeanFactory(Class type, BiFunction<String,BeanDefinition, Object> beanFactory){
        addInstance(beanFactory,true);
        beanFactoryMap.put(type,beanFactory);
        return this;
    }

    public BeanDefinition[] getBeanDefinitions(Class clazz){
        String[] beanNames = beanNameMap.get(clazz);
        BeanDefinition[] beanDefinitions = new BeanDefinition[beanNames.length];
        for (int i = 0; i < beanNames.length; i++) {
            beanDefinitions[i] = getBeanDefinition(beanNames[i]);
        }
        return beanDefinitions;
    }

    public BeanDefinition getBeanDefinition(String beanName) {
        BeanDefinition definition = beanDefinitionMap.get(beanName);
        return definition;
    }

    public String[] getBeanNamesForType(Class clazz) {
        Collection<String> result = new ArrayList<>();
        for (Map.Entry<String,BeanDefinition> entry : beanDefinitionMap.entrySet()) {
            BeanDefinition definition = entry.getValue();
            Class beanType = definition.getBeanClass();
            if(clazz.isAssignableFrom(beanType)){
                String beanName = entry.getKey();
                result.add(beanName);
            }
        }
        return result.toArray(EMPTY);
    }

    public <T>T getBean(Class<T> clazz) {
        String[] beanNames = getBeanNamesForType(clazz);
        if(beanNames.length != 1){
            throw new IllegalStateException("Found more bean. "+Arrays.toString(beanNames));
        }
        return getBean(beanNames[0]);
     }

    public <T>T getBean(String beanName){
        BeanDefinition definition = beanDefinitionMap.get(beanName);
        if(definition == null) {
            throw new IllegalStateException("getBean error. bean is not definition. beanName="+beanName);
        }
        Object instance = definition.isSingleton()? beanInstanceMap.get(beanName): null;
        if(instance == null) {
            BiFunction<String,BeanDefinition, Object> beanFactory = getBeanFactory(definition.getBeanClass());
            instance = beanFactory.apply(beanName,definition);
        }
        if(definition.isSingleton()){
            beanInstanceMap.put(beanName, instance);
        }
        return (T) instance;
    }

    public <T>Collection<T> getBeanForAnnotation(Class<? extends Annotation> annotationType){
        Collection<T> list = new LinkedHashSet<>();
        for (Map.Entry<String, BeanDefinition> entry : beanDefinitionMap.entrySet()) {
            String beanName = entry.getKey();
            BeanDefinition definition = entry.getValue();
            Annotation annotation = ReflectUtil.findAnnotation(definition.getBeanClass(),annotationType);
            if(annotation != null) {
                list.add(getBean(beanName));
            }
        }
        return list;
    }

    public <T>Collection<T> getBeanForType(Class<T> clazz){
        Collection<T> result = new ArrayList<>();
        for (String beanName : getBeanNamesForType(clazz)) {
            result.add(getBean(beanName));
        }
        return result;
    }

    private Object initializeBean(String beanName, Object bean, BeanDefinition definition) throws IllegalStateException{
        invokeBeanAwareMethods(beanName,bean,definition);
        Object wrappedBean = bean;
        wrappedBean = applyBeanBeforeInitialization(beanName,wrappedBean);
        invokeBeanInitialization(beanName,bean,definition);
        wrappedBean = applyBeanAfterInitialization(beanName,wrappedBean);
        return wrappedBean;
    }

    private void invokeBeanAwareMethods(String beanName, Object bean, BeanDefinition definition) throws IllegalStateException{
        if(bean instanceof Aware){
            if(bean instanceof BeanNameAware){
                ((BeanNameAware) bean).setBeanName(beanName);
            }
            if(bean instanceof ApplicationAware){
                ((ApplicationAware) bean).setApplication(this);
            }
        }
    }

    private void invokeBeanInitialization(String beanName, Object bean, BeanDefinition definition) throws IllegalStateException{
        boolean isInitializingBean = bean instanceof InitializingBean;
        if(isInitializingBean){
            try {
                ((InitializingBean)bean).afterPropertiesSet();
            } catch (Exception e) {
                throw new IllegalStateException("invokeBeanInitialization afterPropertiesSet beanName="+beanName+".error="+e,e);
            }
        }
        String initMethodName = definition.getInitMethodName();
        if (initMethodName != null && initMethodName.length() > 0 &&
                !(isInitializingBean && "afterPropertiesSet".equals(initMethodName))) {
            try {
                bean.getClass().getMethod(initMethodName).invoke(bean);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new IllegalStateException("invokeBeanInitialization initMethodName beanName="+beanName+",initMethodName"+initMethodName+",error="+e,e);
            }
        }
    }

    private Object applyBeanBeforeInitialization(String beanName, Object bean) throws IllegalStateException{
        Object result = bean;
        for (BeanPostProcessor processor : beanPostProcessorList) {
            Object current;
            try {
                current = processor.postProcessBeforeInitialization(result, beanName);
            } catch (Exception e) {
                throw new IllegalStateException("applyBeanBeforeInitialization error="+e, e);
            }
            if (current == null) {
                return result;
            }
            result = current;
        }
        return result;
    }

    private Object applyBeanAfterInitialization(String beanName, Object bean) throws IllegalStateException{
        Object result = bean;
        for (BeanPostProcessor processor : beanPostProcessorList) {
            Object current;
            try {
                current = processor.postProcessAfterInitialization(result, beanName);
            } catch (Exception e) {
                throw new IllegalStateException("applyBeanAfterInitialization error="+e, e);
            }
            if (current == null) {
                return result;
            }
            result = current;
        }
        return result;
    }

    public BeanDefinition newBeanDefinition(Class beanType){
        Lazy lazyAnnotation = findAnnotation(beanType,Lazy.class);
        Scope scopeAnnotation = findAnnotation(beanType,Scope.class);
        BeanDefinition definition = new BeanDefinition();
        definition.setBeanClass(beanType);
        definition.setScope(scopeAnnotation == null? BeanDefinition.SCOPE_SINGLETON : scopeAnnotation.value());
        definition.setLazyInit(lazyAnnotation == null? false : lazyAnnotation.value());
        definition.setInitMethodName(findInitMethodName(beanType));
        return definition;
    }

    public BeanDefinition addBeanDefinition(String beanName,BeanDefinition definition){
        return addBeanDefinition(beanName,definition,beanNameMap,beanDefinitionMap);
    }

    public BeanDefinition addBeanDefinition(String beanName,BeanDefinition definition,
                                            Map<Class,String[]> beanNameMap,
                                            Map<String,BeanDefinition> beanDefinitionMap){
        Class beanType = definition.getBeanClass();
        String[] oldBeanNames = beanNameMap.get(beanType);
        Set<String> nameSet = oldBeanNames != null? new LinkedHashSet<>(Arrays.asList(oldBeanNames)):new LinkedHashSet<>(1);
        nameSet.add(beanName);

        beanNameMap.put(beanType,nameSet.toArray(EMPTY));
        return beanDefinitionMap.put(beanName,definition);
    }

    private BiFunction<String,BeanDefinition, Object> getBeanFactory(Class beanType){
        BiFunction<String,BeanDefinition, Object> beanFactory = null;
        for(Class type = beanType; type != null; type = type.getSuperclass()) {
            beanFactory = beanFactoryMap.get(type);
            if(beanFactory != null){
                break;
            }
        }
        if(beanFactory == null){
            beanFactory = defaultBeanFactory;
        }
        return beanFactory;
    }

    private static boolean isAbstract(Class clazz){
        int modifier = clazz.getModifiers();
        return Modifier.isInterface(modifier) || Modifier.isAbstract(modifier);
    }

    private static boolean isExistAnnotation(Class clazz, Collection<Class<? extends Annotation>> annotations){
        for(Annotation a : clazz.getAnnotations()){
            Class aClass = a.annotationType();
            for(Class e : annotations){
                if(e.isAssignableFrom(aClass)) {
                    return true;
                }
            }
        }
        return false;
    }

//    private static Unsafe UNSAFE;
//    static {
//        try {
//            Field f = Unsafe.class.getDeclaredField("theUnsafe");
//            f.setAccessible(true);
//            UNSAFE =(Unsafe)f.get(null);
//        } catch (Exception e) {
//            //
//        }
//    }

    @Override
    public String toString() {
        return scanner.getRootPackageList() +" @ size = " + beanDefinitionMap.size();
    }


    /**
     * 1.扫描class文件
     * 2.创建对象并包装
     */
    private class Scanner {
        private Collection<String> excludeList = new HashSet<>();
        private Collection<String> rootPackageList = new ArrayList<>();
        private Collection<String> getExcludeList(){
            return this.excludeList;
        }

        private Collection<String> getRootPackageList() {
            return rootPackageList;
        }

        private void doScan(String basePackage,ClassLoader loader, Consumer<Class> classConsumer) throws IOException {
            String splashPath = dotToSplash(basePackage);
            URL url = loader.getResource(splashPath);
            if (url == null || existContains(url)) {
                return;
            }
            String filePath = getRootPath(url);
            List<String> names;
            if (isJarFile(filePath)) {
                names = readFromJarFile(filePath, splashPath);
            } else {
                names = readFromDirectory(filePath);
            }

            for (String name : names) {
                if (isClassFile(name)) {
                    Class clazz = toClass(name, basePackage,loader);
                    if (clazz == null) {
                        continue;
                    }
                    if(!isExistAnnotation(clazz, scannerAnnotationList)) {
                        continue;
                    }
                    classConsumer.accept(clazz);
                } else {
                    doScan(basePackage + "." + name, loader,classConsumer);
                }
            }
        }

        private boolean existContains(URL url){
            if(excludeList.isEmpty()) {
                return false;
            }
            String[] urlStr = url.getPath().split("/");
            for(String s : excludeList) {
                for(String u :urlStr) {
                    if (u.equals(s)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private Class toClass(String shortName, String basePackage,ClassLoader loader) {
            StringBuilder sb = new StringBuilder();
            shortName = trimExtension(shortName);
            if(shortName.contains(basePackage)) {
                sb.append(shortName);
            } else {
                sb.append(basePackage);
                sb.append('.');
                sb.append(shortName);
            }
            try {
                return Class.forName(sb.toString(),false,loader);
            } catch (Throwable e) {
                return null;
            }
        }

//        if(jarPath.equals("/git/api/erp.jar"))
//        jarPath = "git/api/erp.jar";
        private List<String> readFromJarFile(String jarPath, String splashedPackageName) throws IOException {
            JarInputStream jarIn = new JarInputStream(new FileInputStream(jarPath));
            JarEntry entry = jarIn.getNextJarEntry();

            List<String> nameList = new ArrayList<String>();
            while (null != entry) {
                String name = entry.getName();
                if (name.startsWith(splashedPackageName) && isClassFile(name)) {
                    nameList.add(name);
                }
                entry = jarIn.getNextJarEntry();
            }
            return nameList;
        }

        private List<String> readFromDirectory(String path) {
            File file = new File(path);
            String[] names = file.list();
            if (null == names) {
                return Collections.emptyList();
            }
            return Arrays.asList(names);
        }

        private boolean isClassFile(String name) {
            return name.endsWith(".class");
        }

        private boolean isJarFile(String name) {
            return name.endsWith(".jar");
        }

        private String getRootPath(URL url) {
            String fileUrl = url.getFile();
            int pos = fileUrl.indexOf('!');
            if (-1 == pos) {
                return fileUrl;
            }
            return fileUrl.substring(5, pos);
        }

        /**
         * "cn.fh.lightning" -> "cn/fh/lightning"
         */
        private String dotToSplash(String name) {
            return name.replaceAll("\\.", "/");
        }

        /**
         * "com/git/Apple.class" -> "com.git.Apple"
         */
        private String trimExtension(String name) {
            int pos = name.indexOf('.');
            if (-1 != pos) {
                name = name.substring(0, pos);
            }
            return name.replace("/",".");
        }

        /**
         * /application/home -> /home
         */
        private  String trimURI(String uri) {
            String trimmed = uri.substring(1);
            int splashIndex = trimmed.indexOf('/');
            return trimmed.substring(splashIndex);
        }
    }

    private <A extends Annotation> A findAnnotation(Class clazz,Class<A> find){
        return (A) clazz.getAnnotation(find);
    }

    private Annotation findAnnotation(Class clazz,Collection<Class<? extends Annotation>> finds){
        for (Class<?extends Annotation> find : finds) {
            Annotation annotation = findAnnotation(clazz,find);
            if(annotation != null){
                return annotation;
            }
        }
        return null;
    }

    private String findInitMethodName(Class clazz){
        for (Method method : clazz.getMethods()) {
            if(method.getDeclaringClass() == Object.class || method.getReturnType() != void.class){
                continue;
            }
            for (Class<? extends Annotation> initMethodAnn : initMethodAnnotationList) {
                if(method.getAnnotationsByType(initMethodAnn).length == 0) {
                    continue;
                }
                if(method.getParameterCount() != 0){
                    throw new IllegalStateException("Initialization method does not have parameters. class="+clazz+",method="+method);
                }
                return method.getName();
            }
        }
        return null;
    }

    /**
     * 自动注入
     */
    private class Injector {
        private Class findType(Annotation resourceAnn, Field field){
            if(resourceAnn == null){
                return field.getType();
            }
            Class resourceAnnotationType = getResourceAnnotationType(resourceAnn);
            Class result;
            if(resourceAnnotationType != Object.class && field.getType().isAssignableFrom(resourceAnnotationType)){
                result = resourceAnnotationType;
            }else {
                result = field.getType();
            }
            return result;
        }

        private Class getResourceAnnotationType(Annotation resourceAnn){
            try {
                Method method = resourceAnn.annotationType().getDeclaredMethod("type");
                if(method.getReturnType() == Class.class){
                    boolean isAccessible = method.isAccessible();
                    try {
                        method.setAccessible(true);
                        return (Class) method.invoke(resourceAnn);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        //skip
                    }finally {
                        method.setAccessible(isAccessible);
                    }
                }
            } catch (NoSuchMethodException e) {
                //skip
            }
            return Object.class;
        }

        private Annotation findAnnotation(Annotation[] annotations){
            for(Annotation annotation : annotations){
                Class<? extends Annotation> annotationClass = annotation.getClass();
                for(Class<? extends Annotation> injectAnnotationClass : injectAnnotationList) {
                    if (injectAnnotationClass == annotationClass){
                        return annotation;
                    }
                }
            }
            return null;
        }

        private Object findResource(Field field, Object target){
            Annotation annotation = findAnnotation(field.getDeclaredAnnotations());
            Class type = findType(annotation,field);
            if(!isAbstract(type)) {
                return getBean(type);
            }
            Collection implList = getBeanForType(type);
            for(Object impl : implList){
                //防止 自身要注入自身 已经实现的接口 从而发生死循环调用
                if(impl != target) {
                    return impl;
                }
            }
            return null;
        }

        /**
         * 是否需要注入
         * @param field
         * @return
         */
        private boolean isNeedInject(Field field){
            for(Annotation annotation : field.getAnnotations()) {
                for(Class<? extends Annotation> injectAnnotationClass : injectAnnotationList){
                    if(injectAnnotationClass.isAssignableFrom(annotation.getClass())){
                        return true;
                    }
                }
            }
            return false;
        }

        private void inject(Class clazz, Object target) {
            for(Class cClazz = clazz; cClazz != null && cClazz!=Object.class; cClazz = cClazz.getSuperclass()) {
                for (Field field : cClazz.getDeclaredFields()) {
                    if(Modifier.isFinal(field.getModifiers())){
                        continue;
                    }
                    if(!isNeedInject(field)){
                        continue;
                    }
                    Object resource = findResource(field, target);
                    if (null == resource) {
                        continue;
                    }
                    try {
                        Object oldValue = getFieldValue(field,target);
                        if(oldValue != null){
                            continue;
                        }
                        setFieldValue(field,resource,target);
                    } catch (Throwable e) {
                        throw new IllegalStateException("inject error="+e+". class="+clazz+",field="+field);
                    }
                }
            }
        }
    }

    public static class BeanDefinition {
        public static final String SCOPE_SINGLETON = "singleton";
        public static final String SCOPE_PROTOTYPE = "prototype";
        public static final int AUTOWIRE_NO = 0;
        public static final int AUTOWIRE_BY_NAME = 1;
        public static final int AUTOWIRE_BY_TYPE = 2;
        public static final int AUTOWIRE_CONSTRUCTOR = 3;

        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private Supplier<?> beanSupplier;
        private Class beanClass;
        private String beanClassName;
        private String scope = SCOPE_SINGLETON;
        private boolean primary = true;
        private boolean lazyInit = false;
        private String initMethodName;
        private int autowireMode = AUTOWIRE_NO;
        private final Map<String,Object> propertyValues = new LinkedHashMap<>();
        private volatile Boolean beforeInstantiationResolved;
        public BeanDefinition() {}

        public String getInitMethodName() {
            return initMethodName;
        }
        public void setInitMethodName(String initMethodName) {
            this.initMethodName = initMethodName;
        }
        public boolean isSingleton(){
            return SCOPE_SINGLETON.equals(scope);
        }
        public boolean isPrototype(){
            return SCOPE_PROTOTYPE.equals(scope);
        }
        public boolean isLazyInit() {
            return lazyInit;
        }
        public boolean isPrimary() {
            return primary;
        }
        public String getScope() {
            return scope;
        }
        public Map<String, Object> getPropertyValues() {
            return propertyValues;
        }
        public void setBeforeInstantiationResolved(Boolean beforeInstantiationResolved) {
            this.beforeInstantiationResolved = beforeInstantiationResolved;
        }
        public Boolean getBeforeInstantiationResolved() {
            return beforeInstantiationResolved;
        }
        public Class getBeanClass() {
            if(beanClass == null && beanClassName != null){
                try {
                    beanClass = Class.forName(beanClassName);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("getBeanClass error."+e,e);
                }
            }
            return beanClass;
        }
        public Supplier<?> getBeanSupplier() {
            return beanSupplier;
        }
        public void setAttribute(String name, Object value){
            attributes.put(name,value);
        }
        public Object removeAttribute(String name){
            return attributes.remove(name);
        }
        public Object getAttribute(String name){
            return attributes.get(name);
        }
        public void setBeanSupplier(Supplier<?> beanSupplier) {
            this.beanSupplier = beanSupplier;
        }
        public void setBeanClass(Class beanClass) {
            this.beanClass = beanClass;
        }
        public void setScope(String scope) {
            this.scope = scope;
        }
        public void setPrimary(boolean primary) {
            this.primary = primary;
        }
        public void setLazyInit(boolean lazyInit) {
            this.lazyInit = lazyInit;
        }
        public String getBeanClassName() {
            return beanClassName;
        }
        public void setBeanClassName(String beanClassName) {
            this.beanClassName = beanClassName;
        }
        public int getAutowireMode() {
            return this.autowireMode;
        }
        public void setAutowireMode(int autowireMode) {
            this.autowireMode = autowireMode;
        }
    }

    public class DefaultBeanNameGenerator implements Function<BeanDefinition,String>{
        @Override
        public String apply(BeanDefinition definition) {
            Class beanType = definition.getBeanClass();
            Annotation annotation = findAnnotation(beanType,scannerAnnotationList);
            String beanName = null;
            if(annotation != null){
                try {
                    Field valueField = annotation.getClass().getField("value");
                    Object fieldValue = getFieldValue(valueField, annotation);
                    if(fieldValue != null && fieldValue.toString().length() > 0){
                        beanName = fieldValue.toString();
                    }
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    //skip
                }
            }
            if(beanName == null) {
                String className = definition.getBeanClass().getName();
                int lastDotIndex = className.lastIndexOf('.');
                int nameEndIndex = className.indexOf("$$");
                if (nameEndIndex == -1) {
                    nameEndIndex = className.length();
                }
                String shortName = className.substring(lastDotIndex + 1, nameEndIndex);
                shortName = shortName.replace('$', '.');
                beanName = Introspector.decapitalize(shortName);
            }
            return beanName;
        }
    }

    private class DefaultBeanFactory implements BiFunction<String,BeanDefinition,Object> {
        @Override
        public Object apply(String beanName,BeanDefinition definition) {
            // Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
            Object bean = resolveBeforeInstantiation(beanName, definition);
            if (bean != null) {
                return bean;
            }

            Object beanInstanceWrapper = createBeanInstance(beanName, definition);
            populateBean(beanName,definition,beanInstanceWrapper);
            if(isLifecycle(beanName)){
                beanInstanceWrapper = initializeBean(beanName, beanInstanceWrapper, definition);
            }
            return beanInstanceWrapper;
        }

        protected Object resolveBeforeInstantiation(String beanName, BeanDefinition mbd) {
            Object bean = null;
            if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
                // Make sure bean class is actually resolved at this point.
                Class<?> targetType = resolveBeanClass(beanName, mbd);
                if (targetType != null) {
                    bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
                    if (bean != null) {
                        bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
                    }
                }
            }
            return bean;
        }

        protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
            for (BeanPostProcessor bp : beanPostProcessorList) {
                if (bp instanceof InstantiationAwareBeanPostProcessor) {
                    InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                    Object result = ibp.postProcessBeforeInstantiation(beanClass, beanName);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        }

        Class resolveBeanClass(String beanName,BeanDefinition definition){
            return definition.getBeanClass();
        }

        Object createBeanInstance(String beanName,BeanDefinition definition){
            Class<?> beanClass = resolveBeanClass(beanName,definition);
            Supplier<?> beanSupplier = definition.getBeanSupplier();
            Object beanInstance = beanSupplier != null? beanSupplier.get() : newInstance(beanClass);
            return beanInstance;
        }

        void populateBean(String beanName,BeanDefinition definition,Object beanInstanceWrapper){
            boolean continueWithPropertyPopulation = true;
            for (BeanPostProcessor bp : beanPostProcessorList) {
                if(bp instanceof InstantiationAwareBeanPostProcessor){
                    InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                    if (!ibp.postProcessAfterInstantiation(beanInstanceWrapper, beanName)) {
                        continueWithPropertyPopulation = false;
                        break;
                    }
                }
            }
            if (!continueWithPropertyPopulation) {
                return;
            }

            Map<String,Object> pvs = definition.getPropertyValues();
            if(definition.getAutowireMode() == BeanDefinition.AUTOWIRE_BY_NAME
                    || definition.getAutowireMode() == BeanDefinition.AUTOWIRE_BY_TYPE) {
                Map<String,Object> newPvs = new HashMap<>(pvs);
                if (definition.getAutowireMode() == BeanDefinition.AUTOWIRE_BY_NAME) {
                    autowireByName(beanName, definition, newPvs);
                }
                if (definition.getAutowireMode() == BeanDefinition.AUTOWIRE_BY_TYPE) {
                    autowireByType(beanName, definition, newPvs);
                }
                pvs = newPvs;
            }

            PropertyDescriptor[] filteredPds = null;
            for (BeanPostProcessor bp : beanPostProcessorList) {
                if (bp instanceof InstantiationAwareBeanPostProcessor) {
                    InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                    Map<String,Object> pvsToUse = ibp.postProcessProperties(pvs, beanInstanceWrapper, beanName);
                    if (pvsToUse == null) {
                        pvsToUse = ibp.postProcessPropertyValues(pvs, filteredPds, beanInstanceWrapper, beanName);
                        if (pvsToUse == null) {
                            return;
                        }
                    }
                    pvs = pvsToUse;
                }
            }
            applyPropertyValues(beanName,definition,beanInstanceWrapper,pvs);
        }

        public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
                throws RuntimeException {
            Object result = existingBean;
            for (BeanPostProcessor processor : beanPostProcessorList) {
                Object current = processor.postProcessAfterInitialization(result, beanName);
                if (current == null) {
                    return result;
                }
                result = current;
            }
            return result;
        }

        protected void applyPropertyValues(String beanName, BeanDefinition definition, Object bw, Map<String,Object> pvs) {
//            bw.setPropertyValues(pvs);
            Class beanType = definition.getBeanClass();
            injector.inject(beanType,bw);
        }

        private <T> T newInstance(Class<T> clazz) throws IllegalStateException{
            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                return (T) instance;
            }catch (Exception e){
                throw new IllegalStateException("newInstanceByJdk error="+e,e);
            }
        }

        private void autowireByType(String beanName,BeanDefinition definition,Map<String,Object> pvs){

        }
        private void autowireByName(String beanName,BeanDefinition definition,Map<String,Object> pvs){

        }
    }

    public interface Aware {}
    public interface BeanNameAware extends Aware{
        void setBeanName(String name);
    }

    public interface ApplicationAware extends Aware{
        void setApplication(ApplicationX applicationX) ;
    }
    public interface InitializingBean {
        void afterPropertiesSet() throws Exception;
    }

    public interface BeanPostProcessor {
        default Object postProcessBeforeInitialization(Object bean, String beanName) throws RuntimeException {
            return bean;
        }
        default Object postProcessAfterInitialization(Object bean, String beanName) throws RuntimeException {
            return bean;
        }
    }

    public interface InstantiationAwareBeanPostProcessor extends BeanPostProcessor{
        default Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws RuntimeException {
            return null;
        }

        default boolean postProcessAfterInstantiation(Object bean, String beanName) throws RuntimeException {
            return true;
        }

        default Map<String,Object> postProcessProperties(Map<String,Object> pvs,
                                                         Object bean, String beanName)throws RuntimeException {
            return null;
        }

        default Map<String,Object> postProcessPropertyValues(
                Map<String,Object> pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws RuntimeException {
            return pvs;
        }
    }
    @Target({TYPE, FIELD})
    @Retention(RUNTIME)
    public @interface Resource {
        String value() default "";
        Class<?> type() default Object.class;
    }
    @Documented
    @Retention (RUNTIME)
    @Target(METHOD)
    public @interface PostConstruct {
    }

    @Target({TYPE})
    @Retention(RUNTIME)
    public @interface Scope {
        String value() default BeanDefinition.SCOPE_SINGLETON;
    }

    @Target({TYPE})
    @Retention(RUNTIME)
    public @interface Lazy {
        boolean value() default true;
    }

    private void setFieldValue(Field field, Object fieldValue,Object target) throws IllegalAccessException {
        try {
            String fieldName = field.getName();
            Method writeMethod = target.getClass().getMethod("set"+capitalize(fieldName),field.getType());
            writeMethod.invoke(target,fieldValue);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            boolean accessible = field.isAccessible();
            try{
                field.setAccessible(true);
                field.set(target,fieldValue);
            } finally {
                field.setAccessible(accessible);
            }
        }
    }

    private Object getFieldValue(Field field,Object target) throws IllegalAccessException {
        try {
            String fieldName = field.getName();
            Method writeMethod = target.getClass().getMethod("get"+capitalize(fieldName));
            return writeMethod.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            boolean accessible = field.isAccessible();
            try{
                field.setAccessible(true);
                return field.get(target);
            } finally {
                field.setAccessible(accessible);
            }
        }
    }

    private String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
    }
}
