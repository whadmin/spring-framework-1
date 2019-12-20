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

import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.ErrorHandler;

/**
 * {@link ApplicationEventMulticaster}接口的简单实现。
 */
public class SimpleApplicationEventMulticaster extends AbstractApplicationEventMulticaster {

	@Nullable
	private Executor taskExecutor;

	@Nullable
	private ErrorHandler errorHandler;


	/**
	 * 实例化SimpleApplicationEventMulticaster
	 */
	public SimpleApplicationEventMulticaster() {
	}

	/**
	 * 实例化SimpleApplicationEventMulticaster，并给定BeanFactory
	 */
	public SimpleApplicationEventMulticaster(BeanFactory beanFactory) {
		setBeanFactory(beanFactory);
	}


	/**
	 * 设置任务执行器
	 */
	public void setTaskExecutor(@Nullable Executor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * 获取任务执行器
	 */
	@Nullable
	protected Executor getTaskExecutor() {
		return this.taskExecutor;
	}

	/**
	 * 设置错误处理器
	 */
	public void setErrorHandler(@Nullable ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	/**
	 * 获取错误处理器
	 * @since 4.1
	 */
	@Nullable
	protected ErrorHandler getErrorHandler() {
		return this.errorHandler;
	}


	/**
	 * 发布事件
	 */
	@Override
	public void multicastEvent(ApplicationEvent event) {
		multicastEvent(event, resolveDefaultEventType(event));
	}

	/**
	 * 发布事件
	 * @param event     {@link ApplicationEvent} 事件
	 * @param eventType 事件的类型
	 */
	@Override
	public void multicastEvent(final ApplicationEvent event, @Nullable ResolvableType eventType) {
		// 1 如果未指定eventType，通过ApplicationEvent对象获取事件类型
		ResolvableType type = (eventType != null ? eventType : resolveDefaultEventType(event));
		// 2 获取执行处理器
		Executor executor = getTaskExecutor();
		// 3 获取满足匹配条件的监听器（条件包括事件类型和事件源）
		for (ApplicationListener<?> listener : getApplicationListeners(event, type)) {
			// 4 如果执行处理器存在，通过执行处理器触发事件，并设置监听器订阅处理（可能是异步）
			if (executor != null) {
				executor.execute(() -> invokeListener(listener, event));
			}
			// 5 同步调用监听器订阅处理触发事件
			else {
				invokeListener(listener, event);
			}
		}
	}

	/**
	 * 获取对象接口类型类型
	 *
	 * public class GenericTestEvent<T> extends ApplicationEvent {
	 *
	 *     private final T payload;
	 *
	 *     public GenericTestEvent(Object source, T payload) {
	 *         super(source);
	 *         this.payload = payload;
	 *     }
	 *
	 *com.spring.ioc.appliction.event_new.event.GenericTestEvent<?>
	 * @param event
	 * @return
	 */
	private ResolvableType resolveDefaultEventType(ApplicationEvent event) {
		return ResolvableType.forInstance(event);
	}

	/**
	 * 指定监听器订阅处理事件
	 * @param listener 订阅监听器
	 * @param event 触发事件
	 */
	protected void invokeListener(ApplicationListener<?> listener, ApplicationEvent event) {
		//1 获取错误处理器
		ErrorHandler errorHandler = getErrorHandler();
		//2 如果设置错误处理器，发生异常交给错误处理器处理
		if (errorHandler != null) {
			try {
				//指定监听器订阅处理事件
				doInvokeListener(listener, event);
			}
			catch (Throwable err) {
				// 错误处理器处理异常
				errorHandler.handleError(err);
			}
		}
		else {
			//指定监听器订阅处理事件
			doInvokeListener(listener, event);
		}
	}

	/**
	 * 指定监听器订阅处理事件
	 * @param listener 订阅监听器
	 * @param event 触发事件
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private void doInvokeListener(ApplicationListener listener, ApplicationEvent event) {
		try {
			listener.onApplicationEvent(event);
		}
		catch (ClassCastException ex) {
			String msg = ex.getMessage();
			if (msg == null || matchesClassCastMessage(msg, event.getClass())) {
				// Possibly a lambda-defined listener which we could not resolve the generic event type for
				// -> let's suppress the exception and just log a debug message.
				Log logger = LogFactory.getLog(getClass());
				if (logger.isTraceEnabled()) {
					logger.trace("Non-matching event type for listener: " + listener, ex);
				}
			}
			else {
				throw ex;
			}
		}
	}

	private boolean matchesClassCastMessage(String classCastMessage, Class<?> eventClass) {
		// On Java 8, the message starts with the class name: "java.lang.String cannot be cast..."
		if (classCastMessage.startsWith(eventClass.getName())) {
			return true;
		}
		// On Java 11, the message starts with "class ..." a.k.a. Class.toString()
		if (classCastMessage.startsWith(eventClass.toString())) {
			return true;
		}
		// On Java 9, the message used to contain the module name: "java.base/java.lang.String cannot be cast..."
		int moduleSeparatorIndex = classCastMessage.indexOf('/');
		if (moduleSeparatorIndex != -1 && classCastMessage.startsWith(eventClass.getName(), moduleSeparatorIndex + 1)) {
			return true;
		}
		// Assuming an unrelated class cast failure...
		return false;
	}

}
