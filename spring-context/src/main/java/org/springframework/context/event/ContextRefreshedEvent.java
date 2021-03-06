/*
 * Copyright 2002-2012 the original author or authors.
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

import org.springframework.context.ApplicationContext;

/**
 * ApplicationContext初始化或刷新完成后触发的事件
 */
@SuppressWarnings("serial")
public class ContextRefreshedEvent extends ApplicationContextEvent {

	/**
	 * 实例化一个新的ContextRefreshedEvent
	 * @param source 与事件相关联源头 {@code ApplicationContext}
	 */
	public ContextRefreshedEvent(ApplicationContext source) {
		super(source);
	}

}
