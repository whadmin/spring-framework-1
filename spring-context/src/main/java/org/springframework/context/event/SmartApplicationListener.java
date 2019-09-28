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

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;

/**
 * 标准{@link ApplicationListener}接口的扩展变体,
 * 支持监听器能否选择触发事件类型，源类型
 */
public interface SmartApplicationListener extends ApplicationListener<ApplicationEvent>, Ordered {

	/**
	 * 确定此侦听器是否实际支持给定的事件类型
	 */
	boolean supportsEventType(Class<? extends ApplicationEvent> eventType);

	/**
	 * 确定此侦听器是否实际支持给定的源类型
	 */
	default boolean supportsSourceType(@Nullable Class<?> sourceType) {
		return true;
	}

	/**
	 * 返回监听器的优先级
	 */
	@Override
	default int getOrder() {
		return LOWEST_PRECEDENCE;
	}

}
