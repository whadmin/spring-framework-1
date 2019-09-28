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
	 * 根据bean的名字，获取在IOC容器中得到bean实例，
	 * 通过第二个参数Object[] args可以给bean的属性赋值，赋值的方式有两种：构造方法和工厂方法
	 * 通过这种方式获取的bean必须把scope属性设置为prototype，也就是非单例模式。
	 */
	Object getBean(String name, Object... args) throws BeansException;

	/**
	 * 根据bean的名字和Class类型，获取在IOC容器中得到bean实例
	 * 通过第二个参数Object[] args可以给bean的属性赋值，赋值的方式有两种：构造方法和工厂方法
	 * 通过这种方式获取的bean必须把scope属性设置为prototype，也就是非单例模式。
	 */
	<T> T getBean(Class<T> requiredType, Object... args) throws BeansException;

	/**
	 * 根据bean的Class类型返回指定bean构造程序
	 */
	<T> ObjectProvider<T> getBeanProvider(Class<T> requiredType);

	/**
	 * 根据bean的ResolvableType类型返回指定bean构造程序
	 */
	<T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType);

	/**
	 * 提供对bean的检索，看看是否在IOC容器有这个名字的bean
	 */
	boolean containsBean(String name);

	/**
	 * 是否为单实例bean
	 */
	boolean isSingleton(String name) throws NoSuchBeanDefinitionException;

	/**
	 * 是否为原型bean(每次getBean都创建独立的bean实例)
	 */
	boolean isPrototype(String name) throws NoSuchBeanDefinitionException;

	/**
	 * 根据bean的名字，获取在IOC容器中得到bean实例并判断是否和指定参数类型ResolvableType是否匹配
	 */
	boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * 根据bean的名字，获取在IOC容器中得到bean实例并判断是否和指定参数类型Class是否匹配
	 */
	boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * 根据bean的名字，获取在IOC容器中得到bean实例Class类型
	 */
	@Nullable
	Class<?> getType(String name) throws NoSuchBeanDefinitionException;

	/**
	 * 根据bean的名字，获取在IOC容器中得到bean实例Class类型
	 * allowFactoryBeanInit：FactoryBean可以仅出于确定其对象类型的目的而对其进行初始化
	 */
	@Nullable
	Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;

	/**
	 * 根据bean的名字，获取实例的别名数组
	 */
	String[] getAliases(String name);

}
