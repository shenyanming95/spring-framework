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

/**
 * 事务当前状态的通用表示, 用于 {@link TransactionStatus} 和 {@link ReactiveTransaction} 的基础接口
 *
 * @author Juergen Hoeller
 * @since 5.2
 */
public interface TransactionExecution {

    /**
     * 判断当前事务是否为新事务, 若为false可能是已存在的事务或者没有事务.
     */
    boolean isNewTransaction();

    /**
     * 设置事务仅回滚
     */
    void setRollbackOnly();

    /**
     * 返回事务是否已被标记为仅回滚
     */
    boolean isRollbackOnly();

    /**
     * 返回此事务是否完成, 即是否已经提交或回滚
     */
    boolean isCompleted();

}
