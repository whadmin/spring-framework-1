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

package org.springframework.context.event;

import java.lang.reflect.Method;

import org.springframework.context.ApplicationListener;

/**
 * 创建{@link ApplicationListener}的工厂
 * <p>
 * 负责将{@link EventListener}注解修饰的方法，通过工厂创建为{@link ApplicationListener}实例
 *
 * @since 4.2
 */
public interface EventListenerFactory {

	/**
	 * 指定此工厂是否支持指定的{@link方法}。
	 *
	 * @param method {@link EventListener}注解修饰方法
	 * @return 如果该工厂支持指定的方法返回{@code true}
	 */
	boolean supportsMethod(Method method);

	/**
	 * 为{@link EventListener}注解修饰的方法，创建一个{@link ApplicationListener}实例
	 *
	 * @param beanName bean的名称
	 * @param type     实例的目标类型
	 * @param method   {@link EventListener}注解修饰方法
	 * @return {@link ApplicationListener} 监听器
	 */
	ApplicationListener<?> createApplicationListener(String beanName, Class<?> type, Method method);

}
