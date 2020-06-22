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

package org.springframework.beans.factory;

import org.springframework.lang.Nullable;

/**
 * FactoryBean用来构造bean的实例，其自身实现就可以作为bean实例。
 *
 *
 */
public interface FactoryBean<T> {

	/**
	 */
	String OBJECT_TYPE_ATTRIBUTE = "factoryBeanObjectType";


	/**
	 * 返回此工厂管理的对象的实例
	 */
	@Nullable
	T getObject() throws Exception;

	/**
	 * 返回此FactoryBean创建的对象的类型，
	 */
	@Nullable
	Class<?> getObjectType();

	/**
	 * 判断FactoryBean管理的对象是否是单例
	 */
	default boolean isSingleton() {
		return true;
	}

}
