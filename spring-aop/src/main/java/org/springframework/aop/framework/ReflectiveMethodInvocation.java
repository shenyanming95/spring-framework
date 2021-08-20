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

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.lang.Nullable;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring's implementation of the AOP Alliance
 * {@link org.aopalliance.intercept.MethodInvocation} interface,
 * implementing the extended
 * {@link org.springframework.aop.ProxyMethodInvocation} interface.
 *
 * <p>Invokes the target object using reflection. Subclasses can override the
 * {@link #invokeJoinpoint()} method to change this behavior, so this is also
 * a useful base class for more specialized MethodInvocation implementations.
 *
 * <p>It is possible to clone an invocation, to invoke {@link #proceed()}
 * repeatedly (once per clone), using the {@link #invocableClone()} method.
 * It is also possible to attach custom attributes to the invocation,
 * using the {@link #setUserAttribute} / {@link #getUserAttribute} methods.
 *
 * <p><b>NOTE:</b> This class is considered internal and should not be
 * directly accessed. The sole reason for it being public is compatibility
 * with existing framework integrations (e.g. Pitchfork). For any other
 * purposes, use the {@link ProxyMethodInvocation} interface instead.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @see #invokeJoinpoint
 * @see #proceed
 * @see #invocableClone
 * @see #setUserAttribute
 * @see #getUserAttribute
 */
public class ReflectiveMethodInvocation implements ProxyMethodInvocation, Cloneable {

    protected final Object proxy;

    @Nullable
    protected final Object target;

    protected final Method method;
    /**
     * List of MethodInterceptor and InterceptorAndDynamicMethodMatcher
     * that need dynamic checks.
     */
    protected final List<?> interceptorsAndDynamicMethodMatchers;
    @Nullable
    private final Class<?> targetClass;
    protected Object[] arguments;
    /**
     * Lazily initialized map of user-specific attributes for this invocation.
     */
    @Nullable
    private Map<String, Object> userAttributes;
    /**
     * Index from 0 of the current interceptor we're invoking.
     * -1 until we invoke: then the current interceptor.
     */
    private int currentInterceptorIndex = -1;


    /**
     * Construct a new ReflectiveMethodInvocation with the given arguments.
     *
     * @param proxy                                the proxy object that the invocation was made on
     * @param target                               the target object to invoke
     * @param method                               the method to invoke
     * @param arguments                            the arguments to invoke the method with
     * @param targetClass                          the target class, for MethodMatcher invocations
     * @param interceptorsAndDynamicMethodMatchers interceptors that should be applied,
     *                                             along with any InterceptorAndDynamicMethodMatchers that need evaluation at runtime.
     *                                             MethodMatchers included in this struct must already have been found to have matched
     *                                             as far as was possibly statically. Passing an array might be about 10% faster,
     *                                             but would complicate the code. And it would work only for static pointcuts.
     */
    protected ReflectiveMethodInvocation(
            Object proxy, @Nullable Object target, Method method, @Nullable Object[] arguments,
            @Nullable Class<?> targetClass, List<Object> interceptorsAndDynamicMethodMatchers) {

        this.proxy = proxy;
        this.target = target;
        this.targetClass = targetClass;
        this.method = BridgeMethodResolver.findBridgedMethod(method);
        this.arguments = AopProxyUtils.adaptArgumentsIfNecessary(method, arguments);
        this.interceptorsAndDynamicMethodMatchers = interceptorsAndDynamicMethodMatchers;
    }


    @Override
    public final Object getProxy() {
        return this.proxy;
    }

    @Override
    @Nullable
    public final Object getThis() {
        return this.target;
    }

    @Override
    public final AccessibleObject getStaticPart() {
        return this.method;
    }

    /**
     * Return the method invoked on the proxied interface.
     * May or may not correspond with a method invoked on an underlying
     * implementation of that interface.
     */
    @Override
    public final Method getMethod() {
        return this.method;
    }

    @Override
    public final Object[] getArguments() {
        return this.arguments;
    }

    @Override
    public void setArguments(Object... arguments) {
        this.arguments = arguments;
    }


    @Override
    @Nullable
    public Object proceed() throws Throwable {
        // currentInterceptorIndex是ReflectiveMethodInvocation的成员变量, 默认为-1.
        // interceptorsAndDynamicMethodMatchers就是上一步获取到的拦截器链. 它在这里就相当于
        // Servlet的过滤器链一样, 通过下标一个一个地调用链上的过滤器. 同理, 这边也是将
        // currentInterceptorIndex当成方法拦截器链的下标, 一个一个调用. 当执行到最后一个拦截器
        // 时, 调用invokeJoinPoint(), 执行目标方法. 达到先执行后返回的效果, 所以最晚执行的是原
        // 方法, 但它是整个递归调用的最快返回.
        if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
            // 当下标值等于集合size-1, 表示执行完所有拦截器, 就会回调原方法; jdk代理使用
            // 使用ReflectiveMethodInvocation.invokeJoinpoint(); cglib代理使用
            // CglibAopProxy.invokeJoinpoint();  -- 标准的多态
            return invokeJoinpoint();
        }
        // 每次先将下标+1, 然后取出链上的拦截器
        Object interceptorOrInterceptionAdvice =
                this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);
        //通过拦截器链生成的分析, 若一个通知Advice的切入点表达式携带了参数, 它就会被包装成
        // InterceptorAndDynamicMethodMatcher类型, 走这个if语句块. 取出它的方法匹配器
        // MethodMatcher, 若能匹配则取出它的拦截器Interceptor回调
        if (interceptorOrInterceptionAdvice instanceof InterceptorAndDynamicMethodMatcher) {
            // Evaluate dynamic method matcher here: static part will already have
            // been evaluated and found to match.
            InterceptorAndDynamicMethodMatcher dm =
                    (InterceptorAndDynamicMethodMatcher) interceptorOrInterceptionAdvice;
            Class<?> targetClass = (this.targetClass != null ? this.targetClass : this.method.getDeclaringClass());
            if (dm.methodMatcher.matches(this.method, targetClass, this.arguments)) {
                return dm.interceptor.invoke(this);
            } else {
                // 若方法匹配失败, 则当前拦截器就会被跳过,  并调用链中的下一个拦截器
                return proceed();
            }
        } else {
            // 如果切入点表达式不带参数, 就走这个语句块.
            // 之前分析获取拦截器链时, 知道spring是将通知Advice适配成MethodInterceprot, 所以
            // 强转成MethodInterceptor并直接调用它的invoke()方法. 由于srping在构造一个类的
            // 增强器时, 都会先加入DefaultPointcutAdvisor对象, 所以在构造拦截器链时, 都会在链
            // 上的首个元素, 适配成ExposeInvocationInterceptor, 它的作用就是将当前对象, 也就是
            // ReflectiveMethodInvocation或CglibMethodInvocation, 保存到ThreadLocal中去,
            // 效果就是在当前线程中共享此对象. 参数this指的是：ReflectiveMethodInvocation
            return ((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this);
        }
    }

    /**
     * Invoke the joinpoint using reflection.
     * Subclasses can override this to use custom invocation.
     *
     * @return the return value of the joinpoint
     * @throws Throwable if invoking the joinpoint resulted in an exception
     */
    @Nullable
    protected Object invokeJoinpoint() throws Throwable {
        return AopUtils.invokeJoinpointUsingReflection(this.target, this.method, this.arguments);
    }


    /**
     * This implementation returns a shallow copy of this invocation object,
     * including an independent copy of the original arguments array.
     * <p>We want a shallow copy in this case: We want to use the same interceptor
     * chain and other object references, but we want an independent value for the
     * current interceptor index.
     *
     * @see java.lang.Object#clone()
     */
    @Override
    public MethodInvocation invocableClone() {
        Object[] cloneArguments = this.arguments;
        if (this.arguments.length > 0) {
            // Build an independent copy of the arguments array.
            cloneArguments = this.arguments.clone();
        }
        return invocableClone(cloneArguments);
    }

    /**
     * This implementation returns a shallow copy of this invocation object,
     * using the given arguments array for the clone.
     * <p>We want a shallow copy in this case: We want to use the same interceptor
     * chain and other object references, but we want an independent value for the
     * current interceptor index.
     *
     * @see java.lang.Object#clone()
     */
    @Override
    public MethodInvocation invocableClone(Object... arguments) {
        // Force initialization of the user attributes Map,
        // for having a shared Map reference in the clone.
        if (this.userAttributes == null) {
            this.userAttributes = new HashMap<>();
        }

        // Create the MethodInvocation clone.
        try {
            ReflectiveMethodInvocation clone = (ReflectiveMethodInvocation) clone();
            clone.arguments = arguments;
            return clone;
        } catch (CloneNotSupportedException ex) {
            throw new IllegalStateException(
                    "Should be able to clone object of type [" + getClass() + "]: " + ex);
        }
    }


    @Override
    public void setUserAttribute(String key, @Nullable Object value) {
        if (value != null) {
            if (this.userAttributes == null) {
                this.userAttributes = new HashMap<>();
            }
            this.userAttributes.put(key, value);
        } else {
            if (this.userAttributes != null) {
                this.userAttributes.remove(key);
            }
        }
    }

    @Override
    @Nullable
    public Object getUserAttribute(String key) {
        return (this.userAttributes != null ? this.userAttributes.get(key) : null);
    }

    /**
     * Return user attributes associated with this invocation.
     * This method provides an invocation-bound alternative to a ThreadLocal.
     * <p>This map is initialized lazily and is not used in the AOP framework itself.
     *
     * @return any user attributes associated with this invocation
     * (never {@code null})
     */
    public Map<String, Object> getUserAttributes() {
        if (this.userAttributes == null) {
            this.userAttributes = new HashMap<>();
        }
        return this.userAttributes;
    }


    @Override
    public String toString() {
        // Don't do toString on target, it may be proxied.
        StringBuilder sb = new StringBuilder("ReflectiveMethodInvocation: ");
        sb.append(this.method).append("; ");
        if (this.target == null) {
            sb.append("target is null");
        } else {
            sb.append("target is of class [").append(this.target.getClass().getName()).append(']');
        }
        return sb.toString();
    }

}
