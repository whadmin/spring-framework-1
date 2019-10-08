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

package org.springframework.context.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

/**
 * 负责将类型为ApplicationListener bean对象作为ApplicationListener监听器注册到ApplicationContext
 * @author Juergen Hoeller
 * @since 4.3.4
 */
class ApplicationListenerDetector implements DestructionAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor {

	private static final Log logger = LogFactory.getLog(ApplicationListenerDetector.class);

	private final transient AbstractApplicationContext applicationContext;

	private final transient Map<String, Boolean> singletonNames = new ConcurrentHashMap<>(256);

	/**
	 * 实例化 ApplicationListenerDetector
	 */
	public ApplicationListenerDetector(AbstractApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	/**
	 * 在实例化bean后，依赖注入前回调执行记录每个bean类型是否是单例
	 */
	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
		this.singletonNames.put(beanName, beanDefinition.isSingleton());
	}

	/**
	 * 在bean初始化前回调执行
	 */
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	/**
	 * 在bean初始后前回调执行
	 * 判断bean类型是否是ApplicationListener，如果是单例作为监听器注册到ApplicationContext事件广播器中
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof ApplicationListener) {
			/** 从singletonNames判断beanName类型是否为单例 **/
			Boolean flag = this.singletonNames.get(beanName);
			/** 如果是单例作为监听器注册到applicationContext **/
			if (Boolean.TRUE.equals(flag)) {
				this.applicationContext.addApplicationListener((ApplicationListener<?>) bean);
			}
			/** 如果不是单例，且beanName是否被BeanFactory管理打印警告信息 **/
			else if (Boolean.FALSE.equals(flag)) {
				if (logger.isWarnEnabled() && !this.applicationContext.containsBean(beanName)) {
					// inner bean with other scope - can't reliably process events
					logger.warn("Inner bean '" + beanName + "' implements ApplicationListener interface " +
							"but is not reachable for event multicasting by its containing ApplicationContext " +
							"because it does not have singleton scope. Only top-level listener beans are allowed " +
							"to be of non-singleton scope.");
				}
				/** 将beanName从singletonNames中删除 **/
				this.singletonNames.remove(beanName);
			}
		}
		return bean;
	}

	/**
	 * 销毁bean回调执行
	 * 将bean对应监听器从ApplicationEventMulticaster中注销
	 */
	@Override
	public void postProcessBeforeDestruction(Object bean, String beanName) {
		if (bean instanceof ApplicationListener) {
			try {
				ApplicationEventMulticaster multicaster = this.applicationContext.getApplicationEventMulticaster();
				multicaster.removeApplicationListener((ApplicationListener<?>) bean);
				multicaster.removeApplicationListenerBean(beanName);
			}
			catch (IllegalStateException ex) {
				// ApplicationEventMulticaster not initialized yet - no need to remove a listener
			}
		}
	}

	/**
	 * 销毁bean回调执行，判断当前DestructionAwareBeanPostProcessor处理器负责处理哪些bean销毁
	 */
	@Override
	public boolean requiresDestruction(Object bean) {
		return (bean instanceof ApplicationListener);
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof ApplicationListenerDetector &&
				this.applicationContext == ((ApplicationListenerDetector) other).applicationContext));
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHashCode(this.applicationContext);
	}

}
