/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.support;

import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 * @since 2.0
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

    /**
     * Maximum number of suppressed exceptions to preserve.
     */
    private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;


    // 单例Bean的缓存Map : bean name --> bean instance.
    // spring 循环依赖一级缓存, 用来保存所有已经创建好的单例Bean(实例化完+初始化完)
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

    // spring 循环依赖三级缓存, 用来保存单例Bean的创建工厂ObjectFactory
    private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

    // spring 循环依赖二级缓存, 用来保存那些正在创建中(实例化完+未初始化), 官方说法是提前曝光Bean
    private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);

    /**
     * Set of registered singletons, containing the bean names in registration order.
     */
    private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

    //当spring正在创建Bean时，就会把BeanName放到这个集合
    private final Set<String> singletonsCurrentlyInCreation =
            Collections.newSetFromMap(new ConcurrentHashMap<>(16));

    //spring创建Bean之前会检查是否满足创建条件，当不需要检查时，会把BeanName放到下面这个集合
    private final Set<String> inCreationCheckExclusions =
            Collections.newSetFromMap(new ConcurrentHashMap<>(16));
    /**
     * Disposable bean instances: bean name to disposable instance.
     */
    private final Map<String, Object> disposableBeans = new LinkedHashMap<>();
    /**
     * Map between containing bean names: bean name to Set of bean names that the bean contains.
     */
    private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);
    // 保存被其它Bean依赖的Bean集合, 比如Bean2,Bean3的创建依赖于Bean1, 那么Bean1就会作为
    // key保存在此Map中, 它的value值为Bean2和Bean3 (此Map可以理解为倒排索引)
    private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);
    // 保存Bean所依赖的其它Bean集合, 比如 Bean2,Bean3的创建依赖于Bean1,那么Bean2,Bean3就
    // 会作为key保存在此Map中, 它们的value值都是Bean1 (此Map可以理解成正向索引)
    private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);
    /**
     * Collection of suppressed Exceptions, available for associating related causes.
     */
    @Nullable
    private Set<Exception> suppressedExceptions;
    /**
     * Flag that indicates whether we're currently within destroySingletons.
     */
    private boolean singletonsCurrentlyInDestruction = false;

    @Override
    public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
        Assert.notNull(beanName, "Bean name must not be null");
        Assert.notNull(singletonObject, "Singleton object must not be null");
        synchronized (this.singletonObjects) {
            Object oldObject = this.singletonObjects.get(beanName);
            if (oldObject != null) {
                throw new IllegalStateException("Could not register object [" + singletonObject +
                        "] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
            }
            addSingleton(beanName, singletonObject);
        }
    }

    /**
     * Add the given singleton object to the singleton cache of this factory.
     * <p>To be called for eager registration of singletons.
     *
     * @param beanName        the name of the bean
     * @param singletonObject the singleton object
     */
    protected void addSingleton(String beanName, Object singletonObject) {
        synchronized (this.singletonObjects) {
            this.singletonObjects.put(beanName, singletonObject);
            this.singletonFactories.remove(beanName);
            this.earlySingletonObjects.remove(beanName);
            this.registeredSingletons.add(beanName);
        }
    }

    /**
     * Add the given singleton factory for building the specified singleton
     * if necessary.
     * <p>To be called for eager registration of singletons, e.g. to be able to
     * resolve circular references.
     *
     * @param beanName         the name of the bean
     * @param singletonFactory the factory for the singleton object
     */
    protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
        Assert.notNull(singletonFactory, "Singleton factory must not be null");
        synchronized (this.singletonObjects) {
            // 如果一级缓存不包含beanName
            if (!this.singletonObjects.containsKey(beanName)) {
                // 将ObjectFactory 保存到三级缓存中, 同时清空二级缓存
                this.singletonFactories.put(beanName, singletonFactory);
                this.earlySingletonObjects.remove(beanName);
                this.registeredSingletons.add(beanName);
            }
        }
    }

    @Override
    @Nullable
    public Object getSingleton(String beanName) {
        return getSingleton(beanName, true);
    }

    /**
     * Return the (raw) singleton object registered under the given name.
     * <p>Checks already instantiated singletons and also allows for an early
     * reference to a currently created singleton (resolving a circular reference).
     *
     * @param beanName            the name of the bean to look for
     * @param allowEarlyReference whether early references should be created or not
     * @return the registered singleton object, or {@code null} if none found
     */
    @Nullable
    protected Object getSingleton(String beanName, boolean allowEarlyReference) {
        // 先从一级缓存中获取beanName对应的Bean实例
        Object singletonObject = this.singletonObjects.get(beanName);
        // 如果缓存中取不到Bean, 并且此Bean正在创建, 就会进入到if语句块中
        if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
            // 对一级缓存加锁处理
            synchronized (this.singletonObjects) {
                // 从二级缓存中获取Bean, 如果Bean不为空, 则直接返回
                singletonObject = this.earlySingletonObjects.get(beanName);
                // 如果Bean为空, 并且参数allowEarlyReference为true(默认此值都为true)
                if (singletonObject == null && allowEarlyReference) {
                    // 从三级缓存中获取Bean的创建工厂ObjectFactory
                    ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                    if (singletonFactory != null) {
                        // 如果此ObjectFactory不为空, 就调用它获取Bean实例；
                        // 同时将它保存到二级缓存中, 清空三级缓存, 即由三级缓存升级到二级缓存
                        singletonObject = singletonFactory.getObject();
                        this.earlySingletonObjects.put(beanName, singletonObject);
                        this.singletonFactories.remove(beanName);
                    }
                }
            }
        }
        // 如果缓存中取到Bean, 或者此Bean没有正被创建, 就会直接返回
        return singletonObject;
    }

    /**
     * Return the (raw) singleton object registered under the given name,
     * creating and registering a new one if none registered yet.
     *
     * @param beanName         the name of the bean
     * @param singletonFactory the ObjectFactory to lazily create the singleton
     *                         with, if necessary
     * @return the registered singleton object
     */
    public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
        Assert.notNull(beanName, "Bean name must not be null");
        synchronized (this.singletonObjects) {
            // 双检锁检查，确保缓存中确实没有当前Bean的实例对象
            Object singletonObject = this.singletonObjects.get(beanName);
            // 缓存中不存在就调用方法参数singletonFactory来创建一个Bean
            if (singletonObject == null) {
                if (this.singletonsCurrentlyInDestruction) {
                    throw new BeanCreationNotAllowedException(beanName,
                            "Singleton bean creation not allowed while singletons of this factory are in destruction " +
                                    "(Do not request a bean from a BeanFactory in a destroy method implementation!)");
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
                }
                //创建Bean之前的检查，与下面的创建Bean之后检查相对应
                //根据成员变量inCreationCheckExclusions和singletonsCurrentlyInCreation来判断
                //Bean是否正在被创建，因为它是往singletonsCurrentlyInCreation(set集合)添加
                //BeanName,如果添加不进去，说明此时正在创建此Bean,就会抛出异常
                //BeanCurrentlyInCreationException
                beforeSingletonCreation(beanName);
                boolean newSingleton = false;
                // 用来处理异常用的
                boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
                if (recordSuppressedExceptions) {
                    this.suppressedExceptions = new LinkedHashSet<>();
                }
                try {
                    // 调用参数singletonFactory来创建Bean，根据doGetBean()方法的源码，这个
                    // singletonFactory是接口ObjectFactory的一个匿名实现类，实际调用的是
                    // AbstractAutowireCapableBeanFactory的createBean()方法
                    singletonObject = singletonFactory.getObject();
                    newSingleton = true;
                } catch (IllegalStateException ex) {
                    // Has the singleton object implicitly appeared in the meantime ->
                    // if yes, proceed with it since the exception indicates that state.
                    singletonObject = this.singletonObjects.get(beanName);
                    if (singletonObject == null) {
                        throw ex;
                    }
                } catch (BeanCreationException ex) {
                    // 设置创建Bean异常的关联原因
                    if (recordSuppressedExceptions) {
                        for (Exception suppressedException : this.suppressedExceptions) {
                            ex.addRelatedCause(suppressedException);
                        }
                    }
                    throw ex;
                } finally {
                    if (recordSuppressedExceptions) {
                        this.suppressedExceptions = null;
                    }
                    //创建Bean之后的检查，与上面的创建Bean之前检查相对应
                    //这里会把BeanName从singletonsCurrentlyInCreation(set集合)移除掉，如果移除
                    //失败，说明singletonsCurrentlyInCreation根本不存在此BeanName，就会抛出
                    //IllegalStateException
                    afterSingletonCreation(beanName);
                }
                //在没有异常情况下，创建完Bean之后，newSingleton会变为true，此时spring就会将其
                //保存到缓存ingletonObjects中
                if (newSingleton) {
                    addSingleton(beanName, singletonObject);
                }
            }
            // 如果缓存singletonObjects已经存在当前Bean，就直接返回
            return singletonObject;
        }
    }

    /**
     * Register an exception that happened to get suppressed during the creation of a
     * singleton bean instance, e.g. a temporary circular reference resolution problem.
     * <p>The default implementation preserves any given exception in this registry's
     * collection of suppressed exceptions, up to a limit of 100 exceptions, adding
     * them as related causes to an eventual top-level {@link BeanCreationException}.
     *
     * @param ex the Exception to register
     * @see BeanCreationException#getRelatedCauses()
     */
    protected void onSuppressedException(Exception ex) {
        synchronized (this.singletonObjects) {
            if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
                this.suppressedExceptions.add(ex);
            }
        }
    }

    /**
     * Remove the bean with the given name from the singleton cache of this factory,
     * to be able to clean up eager registration of a singleton if creation failed.
     *
     * @param beanName the name of the bean
     * @see #getSingletonMutex()
     */
    protected void removeSingleton(String beanName) {
        synchronized (this.singletonObjects) {
            this.singletonObjects.remove(beanName);
            this.singletonFactories.remove(beanName);
            this.earlySingletonObjects.remove(beanName);
            this.registeredSingletons.remove(beanName);
        }
    }

    @Override
    public boolean containsSingleton(String beanName) {
        return this.singletonObjects.containsKey(beanName);
    }

    @Override
    public String[] getSingletonNames() {
        synchronized (this.singletonObjects) {
            return StringUtils.toStringArray(this.registeredSingletons);
        }
    }

    @Override
    public int getSingletonCount() {
        synchronized (this.singletonObjects) {
            return this.registeredSingletons.size();
        }
    }


    public void setCurrentlyInCreation(String beanName, boolean inCreation) {
        Assert.notNull(beanName, "Bean name must not be null");
        if (!inCreation) {
            this.inCreationCheckExclusions.add(beanName);
        } else {
            this.inCreationCheckExclusions.remove(beanName);
        }
    }

    public boolean isCurrentlyInCreation(String beanName) {
        Assert.notNull(beanName, "Bean name must not be null");
        return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
    }

    protected boolean isActuallyInCreation(String beanName) {
        return isSingletonCurrentlyInCreation(beanName);
    }

    /**
     * Return whether the specified singleton bean is currently in creation
     * (within the entire factory).
     *
     * @param beanName the name of the bean
     */
    public boolean isSingletonCurrentlyInCreation(String beanName) {
        return this.singletonsCurrentlyInCreation.contains(beanName);
    }

    /**
     * Callback before singleton creation.
     * <p>The default implementation register the singleton as currently in creation.
     *
     * @param beanName the name of the singleton about to be created
     * @see #isSingletonCurrentlyInCreation
     */
    protected void beforeSingletonCreation(String beanName) {
        if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }
    }

    /**
     * Callback after singleton creation.
     * <p>The default implementation marks the singleton as not in creation anymore.
     *
     * @param beanName the name of the singleton that has been created
     * @see #isSingletonCurrentlyInCreation
     */
    protected void afterSingletonCreation(String beanName) {
        if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
            throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
        }
    }


    /**
     * Add the given bean to the list of disposable beans in this registry.
     * <p>Disposable beans usually correspond to registered singletons,
     * matching the bean name but potentially being a different instance
     * (for example, a DisposableBean adapter for a singleton that does not
     * naturally implement Spring's DisposableBean interface).
     *
     * @param beanName the name of the bean
     * @param bean     the bean instance
     */
    public void registerDisposableBean(String beanName, DisposableBean bean) {
        synchronized (this.disposableBeans) {
            this.disposableBeans.put(beanName, bean);
        }
    }

    /**
     * Register a containment relationship between two beans,
     * e.g. between an inner bean and its containing outer bean.
     * <p>Also registers the containing bean as dependent on the contained bean
     * in terms of destruction order.
     *
     * @param containedBeanName  the name of the contained (inner) bean
     * @param containingBeanName the name of the containing (outer) bean
     * @see #registerDependentBean
     */
    public void registerContainedBean(String containedBeanName, String containingBeanName) {
        synchronized (this.containedBeanMap) {
            Set<String> containedBeans =
                    this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
            if (!containedBeans.add(containedBeanName)) {
                return;
            }
        }
        registerDependentBean(containedBeanName, containingBeanName);
    }

    /**
     * Register a dependent bean for the given bean,
     * to be destroyed before the given bean is destroyed.
     *
     * @param beanName          the name of the bean
     * @param dependentBeanName the name of the dependent bean
     */
    public void registerDependentBean(String beanName, String dependentBeanName) {
        String canonicalName = canonicalName(beanName);

        synchronized (this.dependentBeanMap) {
            Set<String> dependentBeans =
                    this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
            if (!dependentBeans.add(dependentBeanName)) {
                return;
            }
        }

        synchronized (this.dependenciesForBeanMap) {
            Set<String> dependenciesForBean =
                    this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
            dependenciesForBean.add(canonicalName);
        }
    }

    /**
     * Determine whether the specified dependent bean has been registered as
     * dependent on the given bean or on any of its transitive dependencies.
     *
     * @param beanName          the name of the bean to check
     * @param dependentBeanName the name of the dependent bean
     * @since 4.0
     */
    protected boolean isDependent(String beanName, String dependentBeanName) {
        synchronized (this.dependentBeanMap) {
            return isDependent(beanName, dependentBeanName, null);
        }
    }

    /**
     * 此方法是一个递归方法,它会一层一层判断，Bean之间的依赖是否存在回路
     *
     * @param beanName          当前要校验的Bean名称
     * @param dependentBeanName 当前Bean所依赖的Bean名称
     * @param alreadySeen       当已经校验好的Bean名称
     * @return true 发生循环依赖了
     */
    private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
        // 递归中止条件一：当前BeanName已经被校验过，未发生循环依赖，方法返回false
        if (alreadySeen != null && alreadySeen.contains(beanName)) {
            return false;
        }
        // 获取当前要校验Bean的别名
        String canonicalName = canonicalName(beanName);
        // 这里有点绕, 需要注意：获取依赖于当前待校验Bean的其它Bean集合(BeanName集合)
        // 即：Bean1和Bean2的创建依赖于当前Bean, 则Bean1和Bean2就会放在dependentBeans上
        Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
        // 递归中止条件二：当前Bean没有被其它Bean依赖, 方法返回false
        if (dependentBeans == null) {
            return false;
        }
        // 递归中止条件三：依赖于当前Bean的其它Bean集合中包含当前Bean所依赖的Bean, 即发生循环依赖了, 方法返回true
        if (dependentBeans.contains(dependentBeanName)) {
            return true;
        }
        for (String transitiveDependency : dependentBeans) {
            if (alreadySeen == null) {
                alreadySeen = new HashSet<>();
            }
            // 代码走到这边, 说明当前beanName与dependentBeanName不存在依赖关系, 可以把
            // 当前beanName放入到集合alreadySeen中.
            alreadySeen.add(beanName);
            // 这边递归的意思类似这样：
            // 假设当前beanName为B, 被它依赖的Bean为A, 即A依赖于B, 关系为 A → B
            // 而当前beanName又依赖于C, 关系为 B → C, 那么是不是要判断 AC之间是不是也存在依赖?
            // 所以这里就是拿着依赖于B的集合transitiveDependency(上例中A的集合)来与
            // dependentBeanName(上例中的C)判断循环依赖问题, 只要其中一个元素存在循环依赖.
            // 方法立马返回true
            if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
                return true;
            }
        }
        // 能到这里, 表示肯定没有循环依赖啦！！！
        return false;
    }

    /**
     * Determine whether a dependent bean has been registered for the given name.
     *
     * @param beanName the name of the bean to check
     */
    protected boolean hasDependentBean(String beanName) {
        return this.dependentBeanMap.containsKey(beanName);
    }

    /**
     * Return the names of all beans which depend on the specified bean, if any.
     *
     * @param beanName the name of the bean
     * @return the array of dependent bean names, or an empty array if none
     */
    public String[] getDependentBeans(String beanName) {
        Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
        if (dependentBeans == null) {
            return new String[0];
        }
        synchronized (this.dependentBeanMap) {
            return StringUtils.toStringArray(dependentBeans);
        }
    }

    /**
     * Return the names of all beans that the specified bean depends on, if any.
     *
     * @param beanName the name of the bean
     * @return the array of names of beans which the bean depends on,
     * or an empty array if none
     */
    public String[] getDependenciesForBean(String beanName) {
        Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
        if (dependenciesForBean == null) {
            return new String[0];
        }
        synchronized (this.dependenciesForBeanMap) {
            return StringUtils.toStringArray(dependenciesForBean);
        }
    }

    public void destroySingletons() {
        if (logger.isTraceEnabled()) {
            logger.trace("Destroying singletons in " + this);
        }
        synchronized (this.singletonObjects) {
            this.singletonsCurrentlyInDestruction = true;
        }

        String[] disposableBeanNames;
        synchronized (this.disposableBeans) {
            disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
        }
        for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
            destroySingleton(disposableBeanNames[i]);
        }

        this.containedBeanMap.clear();
        this.dependentBeanMap.clear();
        this.dependenciesForBeanMap.clear();

        clearSingletonCache();
    }

    /**
     * Clear all cached singleton instances in this registry.
     *
     * @since 4.3.15
     */
    protected void clearSingletonCache() {
        synchronized (this.singletonObjects) {
            this.singletonObjects.clear();
            this.singletonFactories.clear();
            this.earlySingletonObjects.clear();
            this.registeredSingletons.clear();
            this.singletonsCurrentlyInDestruction = false;
        }
    }

    /**
     * Destroy the given bean. Delegates to {@code destroyBean}
     * if a corresponding disposable bean instance is found.
     *
     * @param beanName the name of the bean
     * @see #destroyBean
     */
    public void destroySingleton(String beanName) {
        // Remove a registered singleton of the given name, if any.
        removeSingleton(beanName);

        // Destroy the corresponding DisposableBean instance.
        DisposableBean disposableBean;
        synchronized (this.disposableBeans) {
            disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
        }
        destroyBean(beanName, disposableBean);
    }

    /**
     * Destroy the given bean. Must destroy beans that depend on the given
     * bean before the bean itself. Should not throw any exceptions.
     *
     * @param beanName the name of the bean
     * @param bean     the bean instance to destroy
     */
    protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
        // Trigger destruction of dependent beans first...
        Set<String> dependencies;
        synchronized (this.dependentBeanMap) {
            // Within full synchronization in order to guarantee a disconnected Set
            dependencies = this.dependentBeanMap.remove(beanName);
        }
        if (dependencies != null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
            }
            for (String dependentBeanName : dependencies) {
                destroySingleton(dependentBeanName);
            }
        }

        // Actually destroy the bean now...
        if (bean != null) {
            try {
                bean.destroy();
            } catch (Throwable ex) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
                }
            }
        }

        // Trigger destruction of contained beans...
        Set<String> containedBeans;
        synchronized (this.containedBeanMap) {
            // Within full synchronization in order to guarantee a disconnected Set
            containedBeans = this.containedBeanMap.remove(beanName);
        }
        if (containedBeans != null) {
            for (String containedBeanName : containedBeans) {
                destroySingleton(containedBeanName);
            }
        }

        // Remove destroyed bean from other beans' dependencies.
        synchronized (this.dependentBeanMap) {
            for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Set<String>> entry = it.next();
                Set<String> dependenciesToClean = entry.getValue();
                dependenciesToClean.remove(beanName);
                if (dependenciesToClean.isEmpty()) {
                    it.remove();
                }
            }
        }

        // Remove destroyed bean's prepared dependency information.
        this.dependenciesForBeanMap.remove(beanName);
    }

    /**
     * Exposes the singleton mutex to subclasses and external collaborators.
     * <p>Subclasses should synchronize on the given Object if they perform
     * any sort of extended singleton creation phase. In particular, subclasses
     * should <i>not</i> have their own mutexes involved in singleton creation,
     * to avoid the potential for deadlocks in lazy-init situations.
     */
    @Override
    public final Object getSingletonMutex() {
        return this.singletonObjects;
    }

}
