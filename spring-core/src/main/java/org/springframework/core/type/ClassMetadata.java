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
 * 特定类的抽象元数据的接口
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see StandardClassMetadata
 * @see org.springframework.core.type.classreading.MetadataReader#getClassMetadata()
 * @see AnnotationMetadata
 */
public interface ClassMetadata {

	/**
	 * 返回基础类的名称。
	 */
	String getClassName();

	/**
	 * 返回基础类是否表示接口。
	 */
	boolean isInterface();

	/**
	 * 返回基础类是否表示注解
	 */
	boolean isAnnotation();

	/**
	 * 返回基础类是否标记为抽象。
	 */
	boolean isAbstract();

	/**
	 * 返回基础类是否表示具体类，即既不是接口也不是抽象类。
	 */
	default boolean isConcrete() {
		return !(isInterface() || isAbstract());
	}

	/**
	 * 返回基础类是否标记为“ final”。
	 */
	boolean isFinal();

	/**
	 * 基础类是否独立,所谓独立标识是顶级类非内部类，如果作为内部类是静态
	 */
	boolean isIndependent();

	/**
	 * 返回基础类是否具有封闭类的名称（外部类）
	 */
	default boolean hasEnclosingClass() {
		return (getEnclosingClassName() != null);
	}

	/**
	 * 返回基础类的封闭类的名称；如果基础类是顶级类，则返回{@code null}。
	 */
	@Nullable
	String getEnclosingClassName();

	/**
	 * 返回基础类是否具有超类。
	 */
	default boolean hasSuperClass() {
		return (getSuperClassName() != null);
	}

	/**
	 * 返回基础类的超类的名称，如果未定义超类，则返回{@code null}。
	 */
	@Nullable
	String getSuperClassName();

	/**
	 * 返回基础类实现的所有接口的名称，如果没有，则返回一个空数组。
	 */
	String[] getInterfaceNames();

	/**
	 * 返回基础类内部所有内部类名称
	 * 这包括由类声明的公共，受保护，默认（程序包）访问和私有类和接口，但不包括继承的类和接口。, 如果不存在成员类或接口，则返回一个空数组。
	 * @since 3.1
	 */
	String[] getMemberClassNames();

}
