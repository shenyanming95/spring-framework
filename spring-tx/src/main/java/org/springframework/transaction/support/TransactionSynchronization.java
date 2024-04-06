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

import org.springframework.core.Ordered;
import org.springframework.transaction.TransactionDefinition;

import java.io.Flushable;

/**
 * 事务同步回调接口, 由 {@link AbstractPlatformTransactionManager} 支持.
 * {@link TransactionSynchronization} 实现类可以实现{@link Ordered} 接口来影响回调顺序, 未实现 Ordered 接口将附件到同步链的尾部.
 * spring 半身执行的系统同步使用特定的顺序值, 允许其与执行顺序进行细粒度的交互.
 *
 * @see TransactionSynchronizationManager
 * @see AbstractPlatformTransactionManager
 * @see org.springframework.jdbc.datasource.DataSourceUtils#CONNECTION_SYNCHRONIZATION_ORDER
 * @since 02.06.2003
 */
public interface TransactionSynchronization extends Flushable {

    /**
     * 正确提交时的完成状态
     */
    int STATUS_COMMITTED = 0;

    /**
     * 正确回滚时的完成状态
     */
    int STATUS_ROLLED_BACK = 1;

    /**
     * 启发式混合完成或系统错误时的完成状态.
     * 启发式混合完成, Heuristic Mixed Completion, 是分布式事务处理中的一种复杂情况, 表示事务参与者之间发生分歧, 有些提交事务, 有些
     * 回滚事务, 导致事务提交和回滚的不一致状态.
     */
    int STATUS_UNKNOWN = 2;


    /**
     * 暂停此 TransactionSynchronization, 若它管理着资源, 应该从 {@link TransactionSynchronizationManager} 取消绑定资源.
     *
     * @see TransactionSynchronizationManager#unbindResource
     */
    default void suspend() {
    }

    /**
     * 恢复此 TransactionSynchronization, 如果管理资源, 应该将资源重新绑定到 {@link TransactionSynchronizationManager}
     *
     * @see TransactionSynchronizationManager#bindResource
     */
    default void resume() {
    }

    /**
     * 将底层会话刷新到数据存储（如果适用）例如 Hibernate/JPA 会话。
     *
     * @see org.springframework.transaction.TransactionStatus#flush()
     */
    @Override
    default void flush() {
    }

    /**
     * 在事务提交之前调用, 在 {@link #beforeCompletion()} 之前, 可以将事务 O/R 映射会话刷新到数据库.
     * 此回调并不意味着事务将实际提交, 调用此方法后事务仍然可能回滚, 这个方法旨在执行仅在提交仍有机会发生时
     * 才相关的工作, 例如: SQL语句刷新到数据库.
     * 注意: 此方法如果抛出异常, 异常将被传播到调用者并导致事务回滚!!
     *
     * @param readOnly 事务是否被定义为只读事务
     * @throws RuntimeException 出现错误时, 将传播到调用者（注意：不要在这里抛出 TransactionException 子类！）
     * @see #beforeCompletion
     */
    default void beforeCommit(boolean readOnly) {
    }

    /**
     * 在事务提交/回滚之前调用, 在 {@link #beforeCommit(boolean)} 之后, 可以在事务完成之前清理资源.
     * 即使 {@link #beforeCommit(boolean)} 抛出异常, 该方法也会在 {@code beforeCommit} 之后调用.
     * 不管执行结果如何, 该方法允许在事务完成之前关闭资源.
     * <p>
     * Invoked before transaction commit/rollback.
     * Can perform resource cleanup <i>before</i> transaction completion.
     * <p>This method will be invoked after {@code beforeCommit}, even when
     * {@code beforeCommit} threw an exception. This callback allows for
     * closing resources before transaction completion, for any outcome.
     *
     * @throws RuntimeException 出现错误时, 将被记录但不会传播（注意：不要在这里抛出 TransactionException 子类！）
     * @see #beforeCommit
     * @see #afterCompletion
     */
    default void beforeCompletion() {
    }

    /**
     * 事务提交之后调用, 主事务成功提交后可以立即执行进一步的操作, 例如去人消息或电子邮件等.
     * 注意: 事务已经提交, 但事务资源可能仍处于活跃状态并且可以访问. 所以, 此时触发的任何
     * 数据访问代码仍将“参与”原始事务, 从而允许执行一些清理(不会再有提交!) 除非它明确声明
     * 它需要再单独的事务中允许, 所以此处调用的任何事务操作使用 {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW}
     *
     * @throws RuntimeException 出现错误时, 将传播到调用者（注意：不要在这里抛出 TransactionException 子类！）
     */
    default void afterCommit() {
    }

    /**
     * 事务提交 or 回滚后调用, 可以在事务完成后清理资源
     * 注意: 事务已经提交, 但事务资源可能仍处于活跃状态并且可以访问. 所以, 此时触发的任何
     * 数据访问代码仍将“参与”原始事务, 从而允许执行一些清理(不会再有提交!) 除非它明确声明
     * 它需要再单独的事务中允许, 所以此处调用的任何事务操作使用 {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW}
     *
     * @param status 根据上面定义的各个 {@code STATUS_*} 常量的完成状态
     * @throws RuntimeException 出现错误时, 将被记录但不会传播（注意：不要在这里抛出 TransactionException 子类！）
     * @see #STATUS_COMMITTED
     * @see #STATUS_ROLLED_BACK
     * @see #STATUS_UNKNOWN
     * @see #beforeCompletion
     */
    default void afterCompletion(int status) {
    }

}
