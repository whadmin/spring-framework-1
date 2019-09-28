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
 * 管理多个{@link ApplicationListener}监听器并向其发布 ApplicationEvent 事件
 */
public interface ApplicationEventMulticaster {

	/**
	 * 添加一个侦听器以通知所有事件。
	 */
	void addApplicationListener(ApplicationListener<?> listener);

	/**
	 * 添加一个侦听器bean，以通知所有事件
	 */
	void addApplicationListenerBean(String listenerBeanName);

	/**
	 * 从通知列表中删除一个侦听器。
	 */
	void removeApplicationListener(ApplicationListener<?> listener);

	/**
	 * 从通知列表中删除一个侦听器bean。
	 */
	void removeApplicationListenerBean(String listenerBeanName);

	/**
	 * 删除在此多播器上注册的所有侦听器。
	 */
	void removeAllListeners();

	/**
	 * 多播 ApplicationEvent 事件给注册的监听器
	 */
	void multicastEvent(ApplicationEvent event);

	/**
	 * 多播 ApplicationEvent 事件给注册的监听器
	 */
	void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType);

}
