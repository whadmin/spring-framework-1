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

package org.springframework.context.annotation;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.util.Assert;

/**
 *
 * 描述Spring管理的bean的作用域元数据信息。
 * <p>默认范围是“单例”，默认是不创建范围代理。
 */
public class ScopeMetadata {

	/**
	 * 作用域，默认范围是“单例”
	 */
	private String scopeName = BeanDefinition.SCOPE_SINGLETON;

	/**
	 * 作用域代理选项，默认是不创建范围代理。
	 */
	private ScopedProxyMode scopedProxyMode = ScopedProxyMode.NO;


	/**
	 * 设置作用域
	 */
	public void setScopeName(String scopeName) {
		Assert.notNull(scopeName, "'scopeName' must not be null");
		this.scopeName = scopeName;
	}

	/**
	 * 获取作用域
	 */
	public String getScopeName() {
		return this.scopeName;
	}

	/**
	 * 设置作用域代理选项
	 */
	public void setScopedProxyMode(ScopedProxyMode scopedProxyMode) {
		Assert.notNull(scopedProxyMode, "'scopedProxyMode' must not be null");
		this.scopedProxyMode = scopedProxyMode;
	}

	/**
	 * 获取作用域代理选项
	 */
	public ScopedProxyMode getScopedProxyMode() {
		return this.scopedProxyMode;
	}

}
