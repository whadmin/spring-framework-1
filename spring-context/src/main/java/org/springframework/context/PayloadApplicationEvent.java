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

package org.springframework.context;

import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.util.Assert;

/**
 * 携带任意有效载荷的{@link ApplicationEvent}。
 * @since 4.2
 */
@SuppressWarnings("serial")
public class PayloadApplicationEvent<T> extends ApplicationEvent implements ResolvableTypeProvider {

	/** 事件的有效负载类型 **/
	private final T payload;

	/**
	 * 实例化PayloadApplicationEvent
	 */
	public PayloadApplicationEvent(Object source, T payload) {
		super(source);
		Assert.notNull(payload, "Payload must not be null");
		this.payload = payload;
	}

	/**
	 * 返回ResolvableType类型
	 */
	@Override
	public ResolvableType getResolvableType() {
		return ResolvableType.forClassWithGenerics(getClass(), ResolvableType.forInstance(getPayload()));
	}

	/**
	 * 返回事件的有效负载。
	 */
	public T getPayload() {
		return this.payload;
	}
}
