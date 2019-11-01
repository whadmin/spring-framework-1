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

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * beanFactory
 */
public interface BeanFactory {

	/**
	 * 作为&FactoryBean名称前缀的字符串
	 */
	String FACTORY_BEAN_PREFIX = "&";

	/**
	 * 根据bean的名字，获取在IOC容器中得到bean实例
	 */
	Object getBean(String name) throws BeansException;

	/**
	 * 根据bean的Class类型，获取在IOC容器中得到bean实例
	 */
	<T> T getBean(Class<T> requiredType) throws BeansException;

	/**
	 * 根据bean的名字和Class类型，获取在IOC容器中得到bean实例
	 */
	<T> T getBean(String name, Class<T> requiredType) throws BeansException;

	/**
	 * 根据bean的名字和参数，获取在IOC容器中得到bean实例，这里参数用于实例化Bean，实例化Bean方式通常对应有参构造方法和有参工厂方法
	 */
	Object getBean(String name, Object... args) throws BeansException;

	/**
	 * 根据bean的类型和参数，获取在IOC容器中得到bean实例，这里参数用于实例化Bean，实例化Bean方式通常对应有参构造方法和有参工厂方法
	 */
	<T> T getBean(Class<T> requiredType, Object... args) throws BeansException;

	/**
	 * 根据bean的Class类型返回bean实例构造对象
	 */
	<T> ObjectProvider<T> getBeanProvider(Class<T> requiredType);

	/**
	 * 根据bean的ResolvableType类型返回bean实例构造对象
	 */
	<T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType);

	/**
	 * 判断指定名称bean，是否注册到IOC容器
	 */
	boolean containsBean(String name);

	/**
	 * bean 是否被定义成单实例
	 */
	boolean isSingleton(String name) throws NoSuchBeanDefinitionException;

	/**
	 * bean 是否被定义成原型实例
	 */
	boolean isPrototype(String name) throws NoSuchBeanDefinitionException;

	/**
	 * 判断指定名称bean，是否和ResolvableType类型匹配
	 */
	boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * 判断指定名称bean，是否和Class类型匹配
	 */
	boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * 根据bean的名字，获取在IOC容器中得到bean实例Class类型
	 */
	@Nullable
	Class<?> getType(String name) throws NoSuchBeanDefinitionException;

	/**
	 * 根据bean的名字，获取在IOC容器中得到bean实例Class类型
	 */
	@Nullable
	Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;

	/**
	 * 根据bean的名字，获取别名
	 */
	String[] getAliases(String name);

}
