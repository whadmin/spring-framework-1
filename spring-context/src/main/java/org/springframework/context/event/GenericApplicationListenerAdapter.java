/*
 * Copyright 2002-2018 the original author or authors.
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

import java.util.Map;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * {@link GenericApplicationListener} adapter that determines supported event types
 * through introspecting the generically declared type of the target listener.
 *
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 3.0
 * @see org.springframework.context.ApplicationListener#onApplicationEvent
 */
public class GenericApplicationListenerAdapter implements GenericApplicationListener, SmartApplicationListener {

	private static final Map<Class<?>, ResolvableType> eventTypeCache = new ConcurrentReferenceHashMap<>();


	private final ApplicationListener<ApplicationEvent> delegate;

	/**
	 * 监听器指定处理的事件 ResolvableType类型
	 */
	@Nullable
	private final ResolvableType declaredEventType;

	/**
	 * 实例化 GenericApplicationListenerAdapter
	 */
	@SuppressWarnings("unchecked")
	public GenericApplicationListenerAdapter(ApplicationListener<?> delegate) {
		Assert.notNull(delegate, "Delegate listener must not be null");
		this.delegate = (ApplicationListener<ApplicationEvent>) delegate;
		this.declaredEventType = resolveDeclaredEventType(this.delegate);
	}

	/**
	 * 处理ApplicationEvent 事件。
	 */
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		this.delegate.onApplicationEvent(event);
	}


	/**
	 * 确定此侦听器是否实际支持给定的事件类型 ResolvableType
	 */
	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return supportsEventType(ResolvableType.forClass(eventType));
	}

	/**
	 * 确定此侦听器是否实际支持给定的事件类型 ResolvableType
	 */
	@Override
	@SuppressWarnings("unchecked")
	public boolean supportsEventType(ResolvableType eventType) {
		/** 如果delegate代理监听器类型是SmartApplicationListener，获取eventType类型Class对象，调用supportsEventType判断是否支持eventType事件类型 **/
		if (this.delegate instanceof SmartApplicationListener) {
			Class<? extends ApplicationEvent> eventClass = (Class<? extends ApplicationEvent>) eventType.resolve();
			return (eventClass != null && ((SmartApplicationListener) this.delegate).supportsEventType(eventClass));
		}
		/** 通过 **/
		else {
			return (this.declaredEventType == null || this.declaredEventType.isAssignableFrom(eventType));
		}
	}

	/**
	 * 确定此侦听器是否实际支持给定的源类型 Class
	 */
	@Override
	public boolean supportsSourceType(@Nullable Class<?> sourceType) {
		return !(this.delegate instanceof SmartApplicationListener) ||
				((SmartApplicationListener) this.delegate).supportsSourceType(sourceType);
	}

	/**
	 * 获取监听器优先级
	 */
	@Override
	public int getOrder() {
		return (this.delegate instanceof Ordered ? ((Ordered) this.delegate).getOrder() : Ordered.LOWEST_PRECEDENCE);
	}


	/**
	 * 获取listener监听器指定父类ApplicationListener泛型对应ResolvableType类型
	 */
	@Nullable
	private static ResolvableType resolveDeclaredEventType(ApplicationListener<ApplicationEvent> listener) {
		ResolvableType declaredEventType = resolveDeclaredEventType(listener.getClass());
		if (declaredEventType == null || declaredEventType.isAssignableFrom(ApplicationEvent.class)) {
			Class<?> targetClass = AopUtils.getTargetClass(listener);
			if (targetClass != listener.getClass()) {
				declaredEventType = resolveDeclaredEventType(targetClass);
			}
		}
		return declaredEventType;
	}

	/**
	 * 获取Class指定父类ApplicationListener泛型对应ResolvableType类型
	 */
	@Nullable
	static ResolvableType resolveDeclaredEventType(Class<?> listenerType) {
		ResolvableType eventType = eventTypeCache.get(listenerType);
		if (eventType == null) {
			eventType = ResolvableType.forClass(listenerType).as(ApplicationListener.class).getGeneric();
			eventTypeCache.put(listenerType, eventType);
		}
		return (eventType != ResolvableType.NONE ? eventType : null);
	}

}
