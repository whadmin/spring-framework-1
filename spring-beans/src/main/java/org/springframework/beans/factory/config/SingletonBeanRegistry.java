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

package org.springframework.beans.factory.config;

import org.springframework.lang.Nullable;

/**
 * 单例bean注册接口
 */
public interface SingletonBeanRegistry {

	/**
	 * 向单例bean注册表注册单例bean实例singletonObject，同时指定名称
	 */
	void registerSingleton(String beanName, Object singletonObject);

	/**
	 * 获取单例bean注册表指定beanName名称对应bean实例
	 */
	@Nullable
	Object getSingleton(String beanName);

	/**
	 * 单例bean注册表是否注册指定bean名称
	 */
	boolean containsSingleton(String beanName);

	/**
	 * 返回单例bean注册表所有注册bean的名称
	 */
	String[] getSingletonNames();

	/**
	 * 返回单例bean注册表所有注册bean的数量
	 */
	int getSingletonCount();

	/**
	 * 返回单例bean注册表
	 */
	Object getSingletonMutex();

}
