/*
 * Copyright 2002-2014 the original author or authors.
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

/**
 * 用于管理保存点(savepoint)的接口, 保存点是事务中的标记, 用于标记事务可回滚的位置.
 * 由 {@link TransactionStatus} 扩展以公开特定事务的保存点管理功能.
 * 注意：savepoint 只能在活跃的事务中工作, 该接口是 spring 作者受到 JDBC 3.0 的
 * Savepoint 机制启发, 独立于任何特定的持久性技术.
 *
 * @see TransactionStatus
 * @see TransactionDefinition#PROPAGATION_NESTED
 * @see java.sql.Savepoint
 */
public interface SavepointManager {

    /**
     * 创建一个新的 savepoint, 后续可以通过 {@code rollbackToSavepoint} 回滚到特定的保存点,
     * 或者通过 {@code releaseSavepoint} 显式释放不再需要的 savepoint.
     * 注意: 大多数事务管理器将在事务完成时自动释放 savepoint.
     *
     * @return 表示该 savepoint 的对象, 也会传递到 {@link #rollbackToSavepoint} 或者 {@link #releaseSavepoint}
     * @throws NestedTransactionNotSupportedException 底层事务不支持保存点
     * @throws TransactionException                   无法创建保存点, 比如说事务未处于适当的状态
     * @see java.sql.Connection#setSavepoint
     */
    Object createSavepoint() throws TransactionException;

    /**
     * 回滚到指定的 savepoint 保存点, savepoint 是不会被自动释放的, 需要显式调用
     * {@link #releaseSavepoint(Object)} 或者依赖事务完成时的自动释放.
     *
     * @param savepoint 要回滚到的保存点
     * @throws NestedTransactionNotSupportedException 底层事务不支持保存点
     * @throws TransactionException                   回滚失败
     * @see java.sql.Connection#rollback(java.sql.Savepoint)
     */
    void rollbackToSavepoint(Object savepoint) throws TransactionException;

    /**
     * 显式释放指定的 savepoint 保存点.
     * 注意: 大部分事务管理器会在事务完成时自动释放 savepoint, 如果事务完成时最终会进行适当的资源清理, 那么释放应该尽可能安静地失败
     *
     * @param savepoint 要释放的保存点
     * @throws NestedTransactionNotSupportedException 底层事务不支持保存点
     * @throws TransactionException                   释放失败
     * @see java.sql.Connection#releaseSavepoint
     */
    void releaseSavepoint(Object savepoint) throws TransactionException;

}
