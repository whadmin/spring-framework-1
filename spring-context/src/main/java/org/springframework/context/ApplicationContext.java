/*
 * Copyright 2002-2014 the original author or authors.
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

import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;


public interface ApplicationContext extends EnvironmentCapable, ListableBeanFactory, HierarchicalBeanFactory,
		MessageSource, ApplicationEventPublisher, ResourcePatternResolver {

	/**
	 * 返回此ApplicationContext文的唯一ID。
	 */
	@Nullable
	String getId();

	/**
	 * 返回ApplicationContext所属的已部署应用程序的名称。
	 */
	String getApplicationName();

	/**
	 * 返回此ApplicationContext的显示名称
	 */
	String getDisplayName();

	/**
	 * 返回首次加载此ApplicationContext时的时间戳。
	 */
	long getStartupDate();

	/**
	 * 返回此ApplicationContext父级ApplicationContext
	 */
	@Nullable
	ApplicationContext getParent();

	/**
	 * 返回此应用程序上下文的内部BeanFactory
	 */
	AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException;

}
