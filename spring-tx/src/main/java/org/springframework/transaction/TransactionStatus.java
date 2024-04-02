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

import java.io.Flushable;

/**
 * 用于表示当前事务的状态, 包含事务的一些元数据和控制方法, 允许应用程序对事务进行操作和查询.
 * 可以通过本类检索事务状态信息, 并以编程方式请求回滚(而不是抛出会导致自动回滚事务的异常)
 * <p>
 * 它继承了{@link SavepointManager}, 因此提供对 savepoint 管理器的访问, 但是 savepoint
 * 是需要底层事务管理器支持的情况才可以使用.
 *
 * @author Juergen Hoeller
 * @see #setRollbackOnly()
 * @see PlatformTransactionManager#getTransaction
 * @see org.springframework.transaction.support.TransactionCallback#doInTransaction
 * @see org.springframework.transaction.interceptor.TransactionInterceptor#currentTransactionStatus()
 * @since 27.03.2003
 */
public interface TransactionStatus extends TransactionExecution, SavepointManager, Flushable {

    /**
     * 判断当前事务是否存在 savepoint, 即是否创建了基于 savepoint 的嵌套事务.
     * 该方法主要与 {@link #isNewTransaction()} 一起用于诊断目的.
     * 对于自定义 savepoint 的编程处理, 使用 {@link SavepointManager} 提供的操作.
     *
     * @see #isNewTransaction()
     * @see #createSavepoint()
     * @see #rollbackToSavepoint(Object)
     * @see #releaseSavepoint(Object)
     */
    boolean hasSavepoint();

    /**
     * 将底层会话刷入数据库中, 例如: 所有受影响的 Hibernate/JPA 会话.
     * 但实际上只是一个提示, 如果底层事务管理器没有 flush 概念, 则调用此方法无效果.
     * 刷新信号可能会应用于主要资源或事务同步, 具体取决于底层资源.
     */
    @Override
    void flush();

}
