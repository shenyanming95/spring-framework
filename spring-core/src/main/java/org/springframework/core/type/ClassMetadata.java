/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.core.type;

import org.springframework.lang.Nullable;

/**
 * Interface that defines abstract metadata of a specific class,
 * in a form that does not require that class to be loaded yet.
 *
 * @author Juergen Hoeller
 * @see StandardClassMetadata
 * @see org.springframework.core.type.classreading.MetadataReader#getClassMetadata()
 * @see AnnotationMetadata
 * @since 2.5
 */
public interface ClassMetadata {

    /**
     * 返回底层类的名称
     */
    String getClassName();

    /**
     * 返回底层类是否表示接口
     */
    boolean isInterface();

    /**
     * 返回底层类是否表示注解
     *
     * @since 4.1
     */
    boolean isAnnotation();

    /**
     * 返回底层类是否是抽象的
     */
    boolean isAbstract();

    /**
     * 返回底层类是否是一个具体类，换句话说，就是既不是接口也不是抽象类
     */
    default boolean isConcrete() {
        return !(isInterface() || isAbstract());
    }

    /**
     * 返回底层类是否是不可继承的最终类
     */
    boolean isFinal();

    /**
     * 返回底层类是否独立，即它是一个顶层类还是嵌套类（静态内部类）
     */
    boolean isIndependent();

    /**
     * 返回底层类是否具有封闭类，即底层类是内部/嵌套类或方法中的本地类），如果此方法返回false，则底层类是顶级类
     */
    default boolean hasEnclosingClass() {
        return (getEnclosingClassName() != null);
    }

    /**
     * 如果底层类是顶级类，则返回底层类的封闭类名称
     */
    @Nullable
    String getEnclosingClassName();

    /**
     * 返回底层类是否有超类
     */
    default boolean hasSuperClass() {
        return (getSuperClassName() != null);
    }

    /**
     * 如果底层类有超类，返回它的超类名称
     */
    @Nullable
    String getSuperClassName();

    /**
     * 返回底层类实现的所有接口的名称，如果没有则返回空数组
     */
    String[] getInterfaceNames();

    /**
     * 返回底层类的所有类名称，包括公共，受保护，默认（包）访问，以及类声明的私有类和接口，但不包括
     * 继承的类和接口。如果不存在成员类或接口，则返回空数组
     *
     * @since 3.1
     */
    String[] getMemberClassNames();

}
