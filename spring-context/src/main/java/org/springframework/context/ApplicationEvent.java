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

package org.springframework.context;

import java.util.EventObject;

/**
 * 应用事件
 */
public abstract class ApplicationEvent extends EventObject {

	private static final long serialVersionUID = 7099057708183571937L;

	/** 事件发生的系统时间. */
	private final long timestamp;
	
	/**
	 * 实例化一个新的{@code ApplicationEvent}。
	 * @param source 与事件相关联源头的对象
	 */
	public ApplicationEvent(Object source) {
		super(source);
		this.timestamp = System.currentTimeMillis();
	}


	/**
	 * 事件发生的系统时间
	 */
	public final long getTimestamp() {
		return this.timestamp;
	}

}
