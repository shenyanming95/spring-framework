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

package org.springframework.transaction;

import org.springframework.dao.DataAccessException;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * spring 事务基础的核心接口, 应用程序可以直接使用, 不过官方推荐通过AOP使用{@link TransactionTemplate}或者声明式事务.
 * 建议继承 {@link AbstractPlatformTransactionManager} 实现事务逻辑, 该抽象类预先实现定义的传播行为并负责事务同步
 * 处理, 子类为底层事务实现模板方法, 例如: begin, suspend, resume, commit.
 * <p>
 * 默认实现, 可以作为其它事务策略的实现指南:
 * {@link org.springframework.transaction.jta.JtaTransactionManager} and
 * {@link org.springframework.jdbc.datasource.DataSourceTransactionManager},
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.transaction.support.TransactionTemplate
 * @see org.springframework.transaction.interceptor.TransactionInterceptor
 * @see org.springframework.transaction.ReactiveTransactionManager
 * @since 16.05.2003
 */
public interface PlatformTransactionManager extends TransactionManager {

    /**
     * 根据指定的事务传播行为, 当前当前活跃的事务, 或者创建新的事务. 需注意, 隔离级别或超时设置,
     * 仅仅适用于新的事务, 因此正在执行的活跃事务会忽略这两个配置.
     * <p>
     * 此外, 并非所有的事务定义配置都被事务管理器支持, 正确的事务管理器实现, 应该在遇到不支持的
     * 事务定义配置时抛出异常; 但有一个事务定义配置是意外, 即 read-only 标志, 若事务管理器不
     * 不支持只读设置, 应该忽略该配置, 本质上只读标志只是潜在优化的提示.
     *
     * @param definition TransactionDefinition 实例（默认可以为null ），描述传播行为、隔离级别、超时等。
     * @return transaction 表示新创建或正活跃的事务状态对象.
     * @throws TransactionException             出现查找、创建或系统错误时
     * @throws IllegalTransactionStateException 如果给定的事务定义无法执行, 例如: 如果当前活动的事务与指定的传播行为冲突
     * @see TransactionDefinition#getPropagationBehavior
     * @see TransactionDefinition#getIsolationLevel
     * @see TransactionDefinition#getTimeout
     * @see TransactionDefinition#isReadOnly
     */
    TransactionStatus getTransaction(@Nullable TransactionDefinition definition) throws TransactionException;

    /**
     * 提交参数指定的事务及其状态, 若事务已通过编程方式标记为仅回滚, 则执行回滚.
     * 若事务不是新创建的事务, 忽略提交以正确参与上下文事务; 若前一个事务已暂停(以便创建新的事务),
     * 则在提交新事务后恢复前一个事务.
     * <p>
     * 注意: commit方法调用完成时, 不论是正常还是抛异常, 事务都必须完全完成并清理.
     * <p>
     * 如果此方法引发 {@link TransactionException} 以外的异常, 某些 before-commit 错误会导致尝试失败;
     * 例如: O/R映射工具可能会在提交之前尝试刷新对数据库的更改, 从而抛出 {@link DataAccessException} 导致
     * 事务失败, 这种情况下, 原始异常应该向上抛给调用者.
     *
     * @param status {@code getTransaction} 方法返回的对象
     * @throws UnexpectedRollbackException      事务协调器发起意外回滚的情况
     * @throws HeuristicCompletionException     事务协调器启发式决策(即作出了一种"不确定"的决定)导致的事务失败
     * @throws TransactionSystemException       发生提交或系统错误（通常由基本资源故障引起）
     * @throws IllegalTransactionStateException 如果给定事务已完成（即提交或回滚）
     * @see TransactionStatus#setRollbackOnly
     */
    void commit(TransactionStatus status) throws TransactionException;

    /**
     * 执行给定事务的回滚.
     * 如果给定的事务不是新事务, 则将其设置为仅回滚即可正确参与上下文的事务; 如果前一个事务已暂停(以便创建新事务)
     * 则在回滚新事务后恢复前一个事务.
     * <p>
     * 如果提交方法{@link #commit(TransactionStatus)} commit} 方法抛出异常, 请勿对事务做回滚操作.
     * 当提交返回时, 即使出现提交异常, 事务也已经完成并被清理, 因此, 提交失败后的回滚将抛出 {@link IllegalTransactionStateException}
     *
     * @param status {@code getTransaction} 方法返回的对象
     * @throws TransactionSystemException       发生回滚或系统错误（通常由基本资源故障引起）
     * @throws IllegalTransactionStateException 如果给定事务已完成（即提交或回滚）
     */
    void rollback(TransactionStatus status) throws TransactionException;

}
