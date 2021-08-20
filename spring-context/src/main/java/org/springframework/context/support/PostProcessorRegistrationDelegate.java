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

package org.springframework.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

    private PostProcessorRegistrationDelegate() {
    }


    public static void invokeBeanFactoryPostProcessors(
            ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

        // 局部变量 processedBeans 表示已执行过的beanFactory, 记录在此, 防止重复执行
        Set<String> processedBeans = new HashSet<>();

        if (beanFactory instanceof BeanDefinitionRegistry) {
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            // 用来保存 BeanFactoryPostProcessor接口
            List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
            // 用来保存 BeanDefinitionRegistryBeanPostProcessor接口
            List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();
            // 这边是遍历执行方法参数的beanFactoryPostProcessors, 将其分类保存到上面定义的
            // 两个成员变量; 默认方法参数的集合为空, 这里就不会执行...
            for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
                if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
                    BeanDefinitionRegistryPostProcessor registryProcessor =
                            (BeanDefinitionRegistryPostProcessor) postProcessor;
                    // 注意一点, spring会在这里先回调一次postProcessBeanDefinitionRegistry()
                    registryProcessor.postProcessBeanDefinitionRegistry(registry);
                    registryProcessors.add(registryProcessor);
                } else {
                    regularPostProcessors.add(postProcessor);
                }
            }

            // Do not initialize FactoryBeans here: We need to leave all regular beans
            // uninitialized to let the bean factory post-processors apply to them!
            // Separate between BeanDefinitionRegistryPostProcessors that implement
            // PriorityOrdered, Ordered, and the rest.
            List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

            // 提醒下, 在此时, 我们创建的配置类还没有生效, 意味着spring容器里面没有任何我们定义
            // 的组件, 就只有它自己定义的, 所以它在这里找BeanDefinitionRegistryPostProcessor
            // 类型的bean组件名称, 就只有默认的：(详见AnnotationConfigUtils类的67行)
            // 默认的组件是：ConfigurationClassPostProcessor
            String[] postProcessorNames =
                    beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);

            /*
             * 这边的代码就是比较经典了, spring根据PriorityOrdered接口、Orderd接口将
             * BeanDefinitionRegistryPostProcessor接口分为三个等级, 优先执行实现了
             * PriorityOrdered, 再执行实现了Orderd, 最后执行普通接口(即未实现上面两个接口的后处
             * 理器); 然后每个等级集合内按照方法getOrder()来决定优先级, 值越小优先级越高!!!
             */

            for (String ppName : postProcessorNames) {
                //先处理实现了PriorityOrderd接口的BeanFactory后置处理器
                if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                    currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    processedBeans.add(ppName);
                }
            }
            // 按照getOrder()方法的大小, 排序, 值越小在ArrayList里面就越靠前, 越先执行
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            // 保存这些接口到registryPostProcessors集合中(最前面定义的两个局部变量)
            registryProcessors.addAll(currentRegistryProcessors);
            // 执行回调方法, 但是这边在容器启动时, 只有一个ConfigurationClassPostProcessor
            // 它的作用就是解析创建IOC上下文指定的配置类, 将配置类指定区域内的组件的
            // BeanDefinition注册到registry上, 也即执行之前分析的类扫描机制
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
            currentRegistryProcessors.clear();

            /*
             * 再处理实现了Orderd接口的BeanFactory后置处理器
             */
            // 由于spring自己定义的组件ConfigurationClassPostProcessor执行了, 它把我们在配置
            // 类中指定包扫描路径的组件的BeanDefinition注册进IOC容器里, 所以这里再执行这个方法
            // 就会把ConfigurationClassPostProcessor 和 我们自己定义的组件都找出来
            postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            for (String ppName : postProcessorNames) {
                // 当接口未被执行过,  并且实现了Ordered接口, 将其加入到orderedPostProcessors中
                // 这边会是这样, 如果我们自定义的BeanDefinitionRegistryPostProcessor实现了
                // PriorityOrdered接口, 它在这里就只会添加到orderedPostProcessors, 毕竟
                // 在处理PriorityOrdered接口时, 我们定义的组件还没有注册进来...
                if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
                    currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    processedBeans.add(ppName);
                }
            }
            // 同样的处理, 先排序, 再标识, 最终按顺序依次执行回调
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            registryProcessors.addAll(currentRegistryProcessors);
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
            currentRegistryProcessors.clear();

            /*
             * 最后处理普通的BeanFactory后置处理器,
             * 注意：这边的处理你会发现是以循环的方式实现, 个人猜测是防止用户在BeanFactory后置处
             * 理里面又添加新的BeanFactory后置处理器, 所以它会一直调用, 直到没有新的处理器出现..
             */
            boolean reiterate = true;
            while (reiterate) {
                reiterate = false;
                postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
                for (String ppName : postProcessorNames) {
                    if (!processedBeans.contains(ppName)) {
                        currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                        processedBeans.add(ppName);
                        reiterate = true;
                    }
                }
                sortPostProcessors(currentRegistryProcessors, beanFactory);
                registryProcessors.addAll(currentRegistryProcessors);
                invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
                currentRegistryProcessors.clear();
            }

            // 上面都是执行BeanDefinitionRegistryPostProcessor的回调方法, 但是
            // BeanDefinitionRegistryPostProcessor也是BeanFactoryPostProcessor的子接口
            // 所以这边就执行父接口的postProcessBeanFactory()方法
            invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
            invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
        }
        // 如果当前Bean工厂不是BeanDefinitionRegistry类型, 就直接遍历回调所有
        // beanFactoryPostProcessor的postProcessBeanFactory()方法
        else {
            // Invoke factory processors registered with the context instance.
            invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
        }

        /*
         * 截止到这, spring已经处理完所有的BeanDefinitionRegistryPostProcessor,
         * 接下来就是处理所有的BeanFactoryPostProcessor
         */
        // 获取容器内所有实现BeanFactoryPostProcessor, 这里会把实现了
        // BeanDefinitionRegistryPostProcessor接口的Bean也找到...就需要靠processedBeans
        // 来区别那些已经执行过的, 和未执行的
        String[] postProcessorNames =
                beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

        // 跟上面一样, spring处理实现BeanFactoryPostProcessor接口也是分为三个等级：
        // 先回调PriorityOrdered, 再回调Ordered, 最后回调普通接口, 下面三个变量
        // 就是分别保存这些接口的集合
        List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
        List<String> orderedPostProcessorNames = new ArrayList<>();
        List<String> nonOrderedPostProcessorNames = new ArrayList<>();
        // 遍历这些BeanFactoryPostProcessor接口, 对号入座, 添加到指定的集合中
        for (String ppName : postProcessorNames) {
            if (processedBeans.contains(ppName)) {
                // 已经被执行过了, 直接跳过
            } else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
            } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
                orderedPostProcessorNames.add(ppName);
            } else {
                nonOrderedPostProcessorNames.add(ppName);
            }
        }

        // 对实现了PriorityOrdered接口的BeanFactoryPostProcessor进行排序后, 依次回调
        sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

        // 对实现了Ordered接口的BeanFactoryPostProcessor进行排序后, 依次回调
        List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
        for (String postProcessorName : orderedPostProcessorNames) {
            orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
        }
        sortPostProcessors(orderedPostProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

        // 对普通的BeanFactoryPostProcessor接口直接回调
        List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
        for (String postProcessorName : nonOrderedPostProcessorNames) {
            nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
        }
        invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

        //清除缓存的合并bean定义，因为后处理器可能已经修改了原始元数据，例如替换值中的占位符...
        beanFactory.clearMetadataCache();
    }

    public static void registerBeanPostProcessors(
            ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

        // 找到当前Bean工厂所有已被注册的BeanPostProcessor的组件名
        String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

        // 为IOC容器注册BeanPostProcessorChecker, 它可以校验Bean后置处理器是不是符合
        // 数量要求, 目标值=已注册的+扫描到但未注册的+方法最后一行注册的...
        int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
        beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

        /*
         * 按照三个优先级, 按照顺序依次注册BeanPostProcessor
         */
        //实现PriorityOrdered接口的
        List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
        //属于内部BeanPostProcessor
        List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
        //实现Ordered接口
        List<String> orderedPostProcessorNames = new ArrayList<>();
        //未实现任何接口
        List<String> nonOrderedPostProcessorNames = new ArrayList<>();
        // 遍历之前找到所有扫描到但未注册的BeanPostProcessor, 按照实现的接口对号入座, 添加到
        // 上面定义的4个集合中...
        for (String ppName : postProcessorNames) {
            if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
                priorityOrderedPostProcessors.add(pp);
                if (pp instanceof MergedBeanDefinitionPostProcessor) {
                    internalPostProcessors.add(pp);
                }
            } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
                orderedPostProcessorNames.add(ppName);
            } else {
                nonOrderedPostProcessorNames.add(ppName);
            }
        }

        // 先注册实现PriorityOrdered接口的Bean后置处理器
        sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

        // 然后注册实现Ordered接口的Bean后置处理器
        List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
        for (String ppName : orderedPostProcessorNames) {
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            orderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        sortPostProcessors(orderedPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, orderedPostProcessors);

        // 再注册没有实现任何优先级接口的Bean后置处理器
        List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
        for (String ppName : nonOrderedPostProcessorNames) {
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            nonOrderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

        // 最终重新注册所有内部的Bean后置处理器
        sortPostProcessors(internalPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, internalPostProcessors);

        // 方法末尾, spring还会注册一个ApplicationListenerDetector, 它的作用是校验Bean
        // 是不是一个ApplicationListener, 是的话就把它添加到IOC容器的监听器集合中...
        beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
    }

    private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
        // Nothing to sort?
        if (postProcessors.size() <= 1) {
            return;
        }
        Comparator<Object> comparatorToUse = null;
        if (beanFactory instanceof DefaultListableBeanFactory) {
            comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
        }
        if (comparatorToUse == null) {
            comparatorToUse = OrderComparator.INSTANCE;
        }
        postProcessors.sort(comparatorToUse);
    }

    /**
     * Invoke the given BeanDefinitionRegistryPostProcessor beans.
     */
    private static void invokeBeanDefinitionRegistryPostProcessors(
            Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

        for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
            postProcessor.postProcessBeanDefinitionRegistry(registry);
        }
    }

    /**
     * Invoke the given BeanFactoryPostProcessor beans.
     */
    private static void invokeBeanFactoryPostProcessors(
            Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

        for (BeanFactoryPostProcessor postProcessor : postProcessors) {
            postProcessor.postProcessBeanFactory(beanFactory);
        }
    }

    /**
     * Register the given BeanPostProcessor beans.
     */
    private static void registerBeanPostProcessors(
            ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

        for (BeanPostProcessor postProcessor : postProcessors) {
            beanFactory.addBeanPostProcessor(postProcessor);
        }
    }


    /**
     * BeanPostProcessor that logs an info message when a bean is created during
     * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
     * getting processed by all BeanPostProcessors.
     */
    private static final class BeanPostProcessorChecker implements BeanPostProcessor {

        private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

        private final ConfigurableListableBeanFactory beanFactory;

        private final int beanPostProcessorTargetCount;

        public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
            this.beanFactory = beanFactory;
            this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
                    this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
                if (logger.isInfoEnabled()) {
                    logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
                            "] is not eligible for getting processed by all BeanPostProcessors " +
                            "(for example: not eligible for auto-proxying)");
                }
            }
            return bean;
        }

        private boolean isInfrastructureBean(@Nullable String beanName) {
            if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
                BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
                return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
            }
            return false;
        }
    }

}
