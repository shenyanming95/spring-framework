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

package org.springframework.context.annotation;

/**
 * Enumerates the various scoped-proxy options.
 *
 * <p>For a more complete discussion of exactly what a scoped proxy is, see the
 * section of the Spring reference documentation entitled '<em>Scoped beans as
 * dependencies</em>'.
 *
 * @author Mark Fisher
 * @see ScopeMetadata
 * @since 2.5
 */
public enum ScopedProxyMode {

    /**
     * 除非在组件扫描设置了不同的默认值，否则此选项等同于下面的No模式
     */
    DEFAULT,

    /**
     * 不会创建任何代理
     * <p>This proxy-mode is not typically useful when used with a
     * non-singleton scoped instance, which should favor the use of the
     * {@link #INTERFACES} or {@link #TARGET_CLASS} proxy-modes instead if it
     * is to be used as a dependency.
     */
    NO,

    /**
     * 创建JDK动态代理，基于接口
     */
    INTERFACES,

    /**
     * 创建CGLIB动态代理，基于类
     */
    TARGET_CLASS

}
