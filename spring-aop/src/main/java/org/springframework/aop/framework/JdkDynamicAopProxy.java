/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.aop.framework;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DecoratingProxy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * JDK-based {@link AopProxy} implementation for the Spring AOP framework,
 * based on JDK {@link java.lang.reflect.Proxy dynamic proxies}.
 *
 * <p>Creates a dynamic proxy, implementing the interfaces exposed by
 * the AopProxy. Dynamic proxies <i>cannot</i> be used to proxy methods
 * defined in classes, rather than interfaces.
 *
 * <p>Objects of this type should be obtained through proxy factories,
 * configured by an {@link AdvisedSupport} class. This class is internal
 * to Spring's AOP framework and need not be used directly by client code.
 *
 * <p>Proxies created using this class will be thread-safe if the
 * underlying (target) class is thread-safe.
 *
 * <p>Proxies are serializable so long as all Advisors (including Advices
 * and Pointcuts) and the TargetSource are serializable.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Dave Syer
 * @see java.lang.reflect.Proxy
 * @see AdvisedSupport
 * @see ProxyFactory
 */
final class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {

    /**
     * use serialVersionUID from Spring 1.2 for interoperability.
     */
    private static final long serialVersionUID = 5531744639992436476L;


    /*
     * NOTE: We could avoid the code duplication between this class and the CGLIB
     * proxies by refactoring "invoke" into a template method. However, this approach
     * adds at least 10% performance overhead versus a copy-paste solution, so we sacrifice
     * elegance for performance. (We have a good test suite to ensure that the different
     * proxies behave the same :-)
     * This way, we can also more easily take advantage of minor optimizations in each class.
     */

    /**
     * We use a static Log to avoid serialization issues.
     */
    private static final Log logger = LogFactory.getLog(JdkDynamicAopProxy.class);

    /**
     * Config used to configure this proxy.
     */
    private final AdvisedSupport advised;

    /**
     * Is the {@link #equals} method defined on the proxied interfaces?
     */
    private boolean equalsDefined;

    /**
     * Is the {@link #hashCode} method defined on the proxied interfaces?
     */
    private boolean hashCodeDefined;


    /**
     * Construct a new JdkDynamicAopProxy for the given AOP configuration.
     *
     * @param config the AOP configuration as AdvisedSupport object
     * @throws AopConfigException if the config is invalid. We try to throw an informative
     *                            exception in this case, rather than let a mysterious failure happen later.
     */
    public JdkDynamicAopProxy(AdvisedSupport config) throws AopConfigException {
        Assert.notNull(config, "AdvisedSupport must not be null");
        if (config.getAdvisors().length == 0 && config.getTargetSource() == AdvisedSupport.EMPTY_TARGET_SOURCE) {
            throw new AopConfigException("No advisors and no TargetSource specified");
        }
        this.advised = config;
    }


    @Override
    public Object getProxy() {
        return getProxy(ClassUtils.getDefaultClassLoader());
    }

    @Override
    public Object getProxy(@Nullable ClassLoader classLoader) {
        if (logger.isTraceEnabled()) {
            logger.trace("Creating JDK dynamic proxy: " + this.advised.getTargetSource());
        }
        // 找出被代理类的所有接口, 这里可能还会加上spring自带的3个接口, 分别是：
        // SpringProxy、Advised、DecoratingProxy(根据被代理类条件判断是否要添加)
        Class<?>[] proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(this.advised, true);
        // 判断被代理类的所有接口, 是不是有重写了equal()和hashcode()方法, 如果设置了
        // 会把JdkDynamicAopProxy的equalsDefined和hashCodeDefined变量置为true..
        findDefinedEqualsAndHashCodeMethods(proxiedInterfaces);
        // 标准的JDK创建代理对象的代码
        return Proxy.newProxyInstance(classLoader, proxiedInterfaces, this);
    }

    /**
     * Finds any {@link #equals} or {@link #hashCode} method that may be defined
     * on the supplied set of interfaces.
     *
     * @param proxiedInterfaces the interfaces to introspect
     */
    private void findDefinedEqualsAndHashCodeMethods(Class<?>[] proxiedInterfaces) {
        for (Class<?> proxiedInterface : proxiedInterfaces) {
            Method[] methods = proxiedInterface.getDeclaredMethods();
            for (Method method : methods) {
                if (AopUtils.isEqualsMethod(method)) {
                    this.equalsDefined = true;
                }
                if (AopUtils.isHashCodeMethod(method)) {
                    this.hashCodeDefined = true;
                }
                if (this.equalsDefined && this.hashCodeDefined) {
                    return;
                }
            }
        }
    }


    /**
     * Implementation of {@code InvocationHandler.invoke}.
     * <p>Callers will see exactly the exception thrown by the target,
     * unless a hook method throws an exception.
     */
    @Override
    @Nullable
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object oldProxy = null;
        boolean setProxyContext = false;
        // TargetSource保存着被代理类的实例对象(可以用它来回调反射方法)
        TargetSource targetSource = this.advised.targetSource;
        Object target = null;

        try {
            // this.equalsDefined表示被代理类是否有重写了equals()方法
            // 如果被代理类重写了equals(), 且当前调用的方法就是equals(), 直接比较比较结果
            if (!this.equalsDefined && AopUtils.isEqualsMethod(method)) {
                // The target does not implement the equals(Object) method itself.
                return equals(args[0]);
            }
            // this.hashCodeDefined表示被代理类是否有重写了hashCode()
            // 如果被代理类重写了hashCode(), 且当前调用的就是hashCode(), 直接返回hash值
            else if (!this.hashCodeDefined && AopUtils.isHashCodeMethod(method)) {
                // The target does not implement the hashCode() method itself.
                return hashCode();
            }
            // 如果当前Method的Class是 DecoratingProxy 类型, 直接反射调用 method
            else if (method.getDeclaringClass() == DecoratingProxy.class) {
                // There is only getDecoratedClass() declared -> dispatch to proxy config.
                return AopProxyUtils.ultimateTargetClass(this.advised);
            }
            // 如果当前Method的Class是接口, 并且还是Advised的父接口, 直接反射调用method
            else if (!this.advised.opaque && method.getDeclaringClass().isInterface() &&
                    method.getDeclaringClass().isAssignableFrom(Advised.class)) {
                // Service invocations on ProxyConfig with the proxy config...
                return AopUtils.invokeJoinpointUsingReflection(this.advised, method, args);
            }

            Object retVal;
            // 将当前代理对象proxy存放到threadLocal中, 相当于暴露它. 此值一般为false
            if (this.advised.exposeProxy) {
                // Make invocation available if necessary.
                oldProxy = AopContext.setCurrentProxy(proxy);
                setProxyContext = true;
            }

            // 获取被代理类的实例对象(提醒：proxy就是target的代理对象...)
            target = targetSource.getTarget();
            Class<?> targetClass = (target != null ? target.getClass() : null);

            // 重点方法：根据通知Advise获取拦截器链, 转到构造拦截器链
            List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

            // Check whether we have any advice. If we don't, we can fallback on direct
            // reflective invocation of the target, and avoid creating a MethodInvocation.
            if (chain.isEmpty()) {
                // 如果拦截器链为空集合, 直接反射调用原方法即可.
                Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
                retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
            } else {
                // 否则创建ReflectiveMethodInvocation实例
                MethodInvocation invocation =
                        new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
                // 这边开始递归调用(正式开始调用每个拦截器以及原方法), 转到链式调用通知
                retVal = invocation.proceed();
            }

            // Massage return value if necessary.
            Class<?> returnType = method.getReturnType();
            // 这是一种特殊的情况, 即方法返回值与被代理类是同一个对象...
            if (retVal != null && retVal == target &&
                    returnType != Object.class && returnType.isInstance(proxy) &&
                    !RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
                // Special case: it returned "this" and the return type of the method
                // is type-compatible. Note that we can't help if the target sets
                // a reference to itself in another returned object.
                retVal = proxy;
            }
            // 如果原方法有返回值, 但是链式调用后的返回值为空, 则抛出异常
            else if (retVal == null && returnType != Void.TYPE && returnType.isPrimitive()) {
                throw new AopInvocationException(
                        "Null return value from advice does not match primitive return type for: " + method);
            }
            // 返回链式调用后的结果
            return retVal;
        } finally {
            // targetSource默认实现类为SingletonTargetSource, 它对这个方法没有任何实现
            if (target != null && !targetSource.isStatic()) {
                // Must have come from TargetSource.
                targetSource.releaseTarget(target);
            }
            // 如果 setProxyContext为true, 将ThreadLocal置为原先的值
            if (setProxyContext) {
                // Restore old proxy.
                AopContext.setCurrentProxy(oldProxy);
            }
        }
    }


    /**
     * Equality means interfaces, advisors and TargetSource are equal.
     * <p>The compared object may be a JdkDynamicAopProxy instance itself
     * or a dynamic proxy wrapping a JdkDynamicAopProxy instance.
     */
    @Override
    public boolean equals(@Nullable Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }

        JdkDynamicAopProxy otherProxy;
        if (other instanceof JdkDynamicAopProxy) {
            otherProxy = (JdkDynamicAopProxy) other;
        } else if (Proxy.isProxyClass(other.getClass())) {
            InvocationHandler ih = Proxy.getInvocationHandler(other);
            if (!(ih instanceof JdkDynamicAopProxy)) {
                return false;
            }
            otherProxy = (JdkDynamicAopProxy) ih;
        } else {
            // Not a valid comparison...
            return false;
        }

        // If we get here, otherProxy is the other AopProxy.
        return AopProxyUtils.equalsInProxy(this.advised, otherProxy.advised);
    }

    /**
     * Proxy uses the hash code of the TargetSource.
     */
    @Override
    public int hashCode() {
        return JdkDynamicAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
    }

}
