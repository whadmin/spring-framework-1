/*
 * Copyright 2002-2015 the original author or authors.
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


public interface MethodMetadata extends AnnotatedTypeMetadata {

	/**
	 * 返回方法的名称。
	 */
	String getMethodName();

	/**
	 * 返回声明此方法的类的标准名称。
	 */
	String getDeclaringClassName();

	/**
	 * 返回此方法的声明的返回类型的全限定名称。
	 */
	String getReturnTypeName();

	/**
	 * 返回底层方法是否有效地抽象：
	 */
	boolean isAbstract();

	/**
	 * 返回基础方法是否标记为 'static'.
	 */
	boolean isStatic();

	/**
	 * 返回基础方法是否标记为'final'.
	 */
	boolean isFinal();

	/**
	 * 返回基础方法是否可重写(未标记为static，final或private)
	 */
	boolean isOverridable();

}
