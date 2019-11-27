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

package org.springframework.context.event;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * 应用事件{@link ApplicationEvent}广播器接口,核心功能如下：
 * <p>
 * 1 管理多个ApplicationListener对象，其中包括（注册，删除）
 * <p>
 * 2 发布{@link ApplicationEvent} 事件
 */
public interface ApplicationEventMulticaster {


	/**
	 * 注册监听器{@link ApplicationListener}
	 *
	 * @param listener 监听器对象
	 */
	void addApplicationListener(ApplicationListener<?> listener);

	/**
	 * 注册监听器{@link ApplicationListener}
	 *
	 * @param listenerBeanName 监听器对象作为bean的名称
	 */
	void addApplicationListenerBean(String listenerBeanName);


	/**
	 * 删除监听器{@link ApplicationListener}
	 *
	 * @param listener 监听器对象
	 */
	void removeApplicationListener(ApplicationListener<?> listener);


	/**
	 * 删除监听器{@link ApplicationListener}
	 *
	 * @param listenerBeanName 监听器对象作为bean的名称
	 */
	void removeApplicationListenerBean(String listenerBeanName);

	/**
	 * 删除所有{@link ApplicationListener}
	 */
	void removeAllListeners();

	/**
	 * 发布{@link ApplicationEvent} 事件
	 *
	 * @param event {@link ApplicationEvent} 事件
	 */
	void multicastEvent(ApplicationEvent event);


	/**
	 * 发布{@link ApplicationEvent} 事件
	 *
	 * @param event     {@link ApplicationEvent} 事件
	 * @param eventType 事件的类型
	 */
	void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType);

}
