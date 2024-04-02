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

package org.springframework.transaction;

import org.springframework.lang.Nullable;

/**
 * 定义符合 spring 的事务属性的接口.
 * 只有启动一个实际的新事务, 才会应用隔离级别和超时设置. 由于只有{@link #PROPAGATION_REQUIRED}、
 * {@link #PROPAGATION_REQUIRES_NEW}和{@link #PROPAGATION_NESTED}会导致这种情况, 因此
 * 在其它隔离级别指定这些设置没有作用, 而且并非所有事务管理器都支持这些高级功能, 因此在给定非默认值时可能会引发相应的异常.
 * <p>
 * {@link #isReadOnly()} 适用于任何事务上下文, 无论是由实际资源事务支持还是在资源级别进行非事务操作.
 * 在后一种情况下, 该标志仅适用于应用程序内的托管资源, 例如 Hibernate {@code Session}
 *
 * @see PlatformTransactionManager#getTransaction(TransactionDefinition)
 * @see org.springframework.transaction.support.DefaultTransactionDefinition
 * @see org.springframework.transaction.interceptor.TransactionAttribute
 */
public interface TransactionDefinition {

    /**
     * 当前有事务就用当前的, 没有就创建一个新的.
     */
    int PROPAGATION_REQUIRED = 0;

    /**
     * 支持当前事务, 如果当前事务不存在则以非事务的方式执行.
     * 注意: 对于具有事务同步的事务管理器(transaction managers), {@code PROPAGATION_SUPPORTS}与根本没有事务的方式不同,
     * 因为定义同步可能适用的事务范围, 因此相同的资源（JDBC Connection 、 Hibernate Session等）将在整个指定范围内共享, 即
     * 实际的执行情况取决于事务管理器的实际同步配置.
     * <p>
     * spring不推荐我们使用{@code PROPAGATION_SUPPORTS} 特别不要依赖 {@code PROPAGATION_SUPPORTS} 范围内的 {@code PROPAGATION_REQUIRED}、
     * {@code PROPAGATION_REQUIRES_NEW} 可能会导致运行时同步冲突; 如果一定要这样配置, 确保正确配置事务管理器, 通常切换到“实际事务同步”
     * (synchronization on actual transaction)
     *
     * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#setTransactionSynchronization
     * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#SYNCHRONIZATION_ON_ACTUAL_TRANSACTION
     */
    int PROPAGATION_SUPPORTS = 1;

    /**
     * 当前一定要有事务, 如果没有则抛异常
     */
    int PROPAGATION_MANDATORY = 2;

    /**
     * 不管当前是否存在事务, 都会新创建一个事务, 若存在当前事务则挂起当前事务, 已存在的被挂起的事务在适当时恢复.
     * 注意: 当前事务挂起, 并不是所有事务管理器都支持的; {@code PROPAGATION_REQUIRES_NEW} 特别适用于
     * {@code javax.transaction.TransactionManager}
     *
     * @see org.springframework.transaction.jta.JtaTransactionManager#setTransactionManager
     */
    int PROPAGATION_REQUIRES_NEW = 3;


    /**
     * 不支持事务, 以非事务的方式执行. 如果当前存在事务, 那么也会以非事务的方式执行.
     * 注意事务同步在 {@code PROPAGATION_NOT_SUPPORTED} 范围内不可用, 若当前
     * 有同步, 则会将当前同步暂停并适当恢复.
     *
     * @see org.springframework.transaction.jta.JtaTransactionManager#setTransactionManager
     */
    int PROPAGATION_NOT_SUPPORTED = 4;


    /**
     * 不支持事务, 若当前存在事务, 则抛出异常.
     * 注意: 事务同步在{@code PROPAGATION_NEVER}范围内不可用.
     */
    int PROPAGATION_NEVER = 5;


    /**
     * 如果当前有事务, 则在上下文中参加一个嵌套事务, 嵌套事务的执行受到外部事务的影响,
     * 但嵌套事务的提交或回滚不影响外部事务; 如果当前没有事务, 则新起一个事务, 此时
     * 等价于 {@link #PROPAGATION_REQUIRED}.
     * <p>
     * 注意: 嵌套事务适用于特定的事务管理器, 开箱即用仅适用于 JDBC 3.0 驱动程序的
     * {@link org.springframework.jdbc.datasource.DataSourceTransactionManager},
     * 一些 JTA 提供程序也可能支持嵌套事务。
     *
     * @see org.springframework.jdbc.datasource.DataSourceTransactionManager
     */
    int PROPAGATION_NESTED = 6;


    /**
     * 事务的默认隔离级别, 在使用该隔离级别时, 事务管理器将根据底层数据源的默认隔离级别来确定事务的隔离级别.
     *
     * @see java.sql.Connection
     */
    int ISOLATION_DEFAULT = -1;

    /**
     * 事务的最低隔离级别, 可能发生脏读、不可重复读和幻读.
     * 此级别允许在提交该行中的任何更改之前由一个事务更改的行由另一个事务读取(脏读), 如果回滚任何更改, 第二个事务将检索到无效行.
     *
     * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
     */
    int ISOLATION_READ_UNCOMMITTED = 1;  // same as java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;

    /**
     * 事务只能读取其它事务提交的数据, 可以防止脏读, 但是可能出现不可重复读、幻读;
     *
     * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
     */
    int ISOLATION_READ_COMMITTED = 2;  // same as java.sql.Connection.TRANSACTION_READ_COMMITTED;

    /**
     * 事务执行期间可以保持前后一致性, 能够防止脏读和不可重复读, 但是可能出现幻读.
     * (其他事务在当前事务执行期间可能会插入新的数据，导致事务在后续读取时发现了新增的数据)
     *
     * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
     */
    int ISOLATION_REPEATABLE_READ = 4;  // same as java.sql.Connection.TRANSACTION_REPEATABLE_READ;

    /**
     * 事务的最高隔离级别, 通过事务提交的串行化, 来防止脏读、不可重复读和幻读.
     * 当一个事务需要访问某些资源时, 会尝试获取相应的锁, 如果资源已经被其他事务持有了相应的锁,
     * 那么当前事务可能会被阻塞, 直到其他事务释放了锁. 因此会降低数据库的并发性.
     *
     * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
     */
    int ISOLATION_SERIALIZABLE = 8;  // same as java.sql.Connection.TRANSACTION_SERIALIZABLE;


    /**
     * 使用底层事务系统的默认超时, 如果不支持超时则不使用。
     */
    int TIMEOUT_DEFAULT = -1;

    /**
     * Return an unmodifiable {@code TransactionDefinition} with defaults.
     * <p>For customization purposes, use the modifiable
     * {@link org.springframework.transaction.support.DefaultTransactionDefinition}
     * instead.
     *
     * @since 5.2
     */
    static TransactionDefinition withDefaults() {
        return StaticTransactionDefinition.INSTANCE;
    }

    /**
     * Return the propagation behavior.
     * <p>Must return one of the {@code PROPAGATION_XXX} constants
     * defined on {@link TransactionDefinition this interface}.
     * <p>The default is {@link #PROPAGATION_REQUIRED}.
     *
     * @return the propagation behavior
     * @see #PROPAGATION_REQUIRED
     * @see org.springframework.transaction.support.TransactionSynchronizationManager#isActualTransactionActive()
     */
    default int getPropagationBehavior() {
        return PROPAGATION_REQUIRED;
    }

    /**
     * Return the isolation level.
     * <p>Must return one of the {@code ISOLATION_XXX} constants defined on
     * {@link TransactionDefinition this interface}. Those constants are designed
     * to match the values of the same constants on {@link java.sql.Connection}.
     * <p>Exclusively designed for use with {@link #PROPAGATION_REQUIRED} or
     * {@link #PROPAGATION_REQUIRES_NEW} since it only applies to newly started
     * transactions. Consider switching the "validateExistingTransactions" flag to
     * "true" on your transaction manager if you'd like isolation level declarations
     * to get rejected when participating in an existing transaction with a different
     * isolation level.
     * <p>The default is {@link #ISOLATION_DEFAULT}. Note that a transaction manager
     * that does not support custom isolation levels will throw an exception when
     * given any other level than {@link #ISOLATION_DEFAULT}.
     *
     * @return the isolation level
     * @see #ISOLATION_DEFAULT
     * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#setValidateExistingTransaction
     */
    default int getIsolationLevel() {
        return ISOLATION_DEFAULT;
    }

    /**
     * Return the transaction timeout.
     * <p>Must return a number of seconds, or {@link #TIMEOUT_DEFAULT}.
     * <p>Exclusively designed for use with {@link #PROPAGATION_REQUIRED} or
     * {@link #PROPAGATION_REQUIRES_NEW} since it only applies to newly started
     * transactions.
     * <p>Note that a transaction manager that does not support timeouts will throw
     * an exception when given any other timeout than {@link #TIMEOUT_DEFAULT}.
     * <p>The default is {@link #TIMEOUT_DEFAULT}.
     *
     * @return the transaction timeout
     */
    default int getTimeout() {
        return TIMEOUT_DEFAULT;
    }

    /**
     * Return whether to optimize as a read-only transaction.
     * <p>The read-only flag applies to any transaction context, whether backed
     * by an actual resource transaction ({@link #PROPAGATION_REQUIRED}/
     * {@link #PROPAGATION_REQUIRES_NEW}) or operating non-transactionally at
     * the resource level ({@link #PROPAGATION_SUPPORTS}). In the latter case,
     * the flag will only apply to managed resources within the application,
     * such as a Hibernate {@code Session}.
     * <p>This just serves as a hint for the actual transaction subsystem;
     * it will <i>not necessarily</i> cause failure of write access attempts.
     * A transaction manager which cannot interpret the read-only hint will
     * <i>not</i> throw an exception when asked for a read-only transaction.
     *
     * @return {@code true} if the transaction is to be optimized as read-only
     * ({@code false} by default)
     * @see org.springframework.transaction.support.TransactionSynchronization#beforeCommit(boolean)
     * @see org.springframework.transaction.support.TransactionSynchronizationManager#isCurrentTransactionReadOnly()
     */
    default boolean isReadOnly() {
        return false;
    }


    // Static builder methods

    /**
     * Return the name of this transaction. Can be {@code null}.
     * <p>This will be used as the transaction name to be shown in a
     * transaction monitor, if applicable (for example, WebLogic's).
     * <p>In case of Spring's declarative transactions, the exposed name will be
     * the {@code fully-qualified class name + "." + method name} (by default).
     *
     * @return the name of this transaction ({@code null} by default}
     * @see org.springframework.transaction.interceptor.TransactionAspectSupport
     * @see org.springframework.transaction.support.TransactionSynchronizationManager#getCurrentTransactionName()
     */
    @Nullable
    default String getName() {
        return null;
    }

}
