/*
 * Copyright 2002-2018 the original author or authors.
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

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.*;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

    /**
     * Determine whether the Advisors contain matching introductions.
     */
    private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
        for (Advisor advisor : advisors) {
            if (advisor instanceof IntroductionAdvisor) {
                IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
                if (ia.getClassFilter().matches(actualClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
            Advised config, Method method, @Nullable Class<?> targetClass) {

        // 获取Advisor适配器的注册接口, 默认实现类：DefaultAdvisorAdapterRegistry
        AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
        Advisor[] advisors = config.getAdvisors();
        // 参数config就是ProxyFactory, 从它身上先获取目标类(被代理类)所有的增强器
        // 然后创建列表 interceptorList, 它就是方法要返回的对象
        List<Object> interceptorList = new ArrayList<>(advisors.length);
        // 获取被代理类的Class对象
        Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
        Boolean hasIntroductions = null;
        // 遍历所有的增强器, 通过AdvisorAdapterRegistry接口, 将增强器适配成 org.aopalliance.intercept.MethodInterceptor类
        for (Advisor advisor : advisors) {
            // 若增强器属于切入点增强器. 我们常用的通知都是属于这种类型的增强器
            if (advisor instanceof PointcutAdvisor) {
                // Add it conditionally.
                PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
                // 若aop配置类AdvisedSupport(即变量config)有预先过滤; 或者切入点能匹配上当前的Class类型
                if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
                    // 获取方法匹配器, 判断能否匹配上当前执行的方法Method
                    MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
                    boolean match;
                    if (mm instanceof IntroductionAwareMethodMatcher) {
                        if (hasIntroductions == null) {
                            hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
                        }
                        // 切入点的方法匹配器实现是：AspectJExpressionPointcut, 在设置切点的时候可
                        // 以用切点语言来更加精确的表示拦截哪个方法.如果切入点表达式携带了方法参数,
                        // 例如：execution(* com.sym.saveStudent(..)) && args(id,name)
                        // 它的isRuntime()方法就会返回true, spring就会把拦截器interceptor和方法
                        // 匹配器MethodMatcher重新包装成InterceptorAndDynamicMethodMatcher对象
                        match = ((IntroductionAwareMethodMatcher) mm).matches(method, actualClass, hasIntroductions);
                    } else {
                        match = mm.matches(method, actualClass);
                    }
                    if (match) {
                        // 调用增强器适配器AdvisorAdapterRegistry, 将增强器适配成MethodInterceptor.
                        // 这里会选取合适的AdvisorAdapter接口, 去适配增强器advisor. 如果有多个
                        // AdvisorAdapter能适配上此增强器, 就会返回多个MethodInterceptor
                        MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
                        if (mm.isRuntime()) {
                            // Creating a new object instance in the getInterceptors() method
                            // isn't a problem as we normally cache created chains.
                            for (MethodInterceptor interceptor : interceptors) {
                                interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
                            }
                        } else {
                            // 如果切入点表达式没加方法参数, 就直接添加到返回集合中
                            interceptorList.addAll(Arrays.asList(interceptors));
                        }
                    }
                }
            }
            // 如果增强器是 IntroductionAdvisor 类型
            else if (advisor instanceof IntroductionAdvisor) {
                IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
                if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
                    Interceptor[] interceptors = registry.getInterceptors(advisor);
                    interceptorList.addAll(Arrays.asList(interceptors));
                }
            }
            // 增强器是其它类型的
            else {
                Interceptor[] interceptors = registry.getInterceptors(advisor);
                interceptorList.addAll(Arrays.asList(interceptors));
            }
        }

        return interceptorList;
    }

}
