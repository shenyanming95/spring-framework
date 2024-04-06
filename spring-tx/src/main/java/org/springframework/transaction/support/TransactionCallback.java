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

package org.springframework.transaction.support;

import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionStatus;

/**
 * 事务中执行的回调接口, 与 {@link TransactionTemplate#execute(TransactionCallback)} 方法一起使用, 一般作为方法实现中的匿名类.
 * 通常用于将对事务不感知的数据访问服务的各种调用, 组装成具有事务划分的更高级别的服务方法; 作为替代方案, 可以考虑使用声明式事务划分,
 * 例如使用 {@link org.springframework.transaction.annotation.Transactional} 注解
 *
 * @param <T> the result type
 * @author Juergen Hoeller
 * @see TransactionTemplate
 * @see CallbackPreferringPlatformTransactionManager
 * @since 17.03.2003
 */
@FunctionalInterface
public interface TransactionCallback<T> {

    /**
     * 由 {@link TransactionTemplate#execute} 在事务上下文中调用, 不需要关心事务本身, 尽管它能通过给定的 TransactionStatus 对象
     * 检索和影响当前事务的状态(例如设置仅回滚). 允许返回在事务中创建的结果对象, 即域对象或者域对象的集合. 回调抛出的 RuntimeException
     * 被视为强制回滚的应用程序异常, 任何此类异常都将传播到调用者, 除非出现回滚问题, 这种情况下抛出 TransactionException
     *
     * @param status 当前事务关联的事务状态
     * @return a result object, or {@code null}
     * @see TransactionTemplate#execute
     * @see CallbackPreferringPlatformTransactionManager#execute
     */
    @Nullable
    T doInTransaction(TransactionStatus status);

}
