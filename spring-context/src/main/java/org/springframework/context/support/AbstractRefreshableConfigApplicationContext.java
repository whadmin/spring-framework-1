/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.context.support;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link AbstractRefreshableApplicationContext} subclass that adds common handling
 * of specified config locations. Serves as base class for XML-based application
 * context implementations such as {@link ClassPathXmlApplicationContext} and
 * {@link FileSystemXmlApplicationContext}, as well as
 * {@link org.springframework.web.context.support.XmlWebApplicationContext}.
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 * @see #setConfigLocation
 * @see #setConfigLocations
 * @see #getDefaultConfigLocations
 */
public abstract class AbstractRefreshableConfigApplicationContext extends AbstractRefreshableApplicationContext
		implements BeanNameAware, InitializingBean {

	/**
	 * 配置文件路径数组
	 */
	@Nullable
	private String[] configLocations;

	private boolean setIdCalled = false;


	/**
	 * 创建一个没有父级的新AbstractRefreshableConfigApplicationContext。
	 */
	public AbstractRefreshableConfigApplicationContext() {
	}

	/**
	 * 使用给定的父上下文创建一个新的AbstractRefreshableConfigApplicationContext。
	 */
	public AbstractRefreshableConfigApplicationContext(@Nullable ApplicationContext parent) {
		super(parent);
	}


	/**
	 * 设置此ApplicationContext的配置文件路径，多个路径可以通过CONFIG_LOCATION_DELIMITERS隔开
	 */
	public void setConfigLocation(String location) {
		setConfigLocations(StringUtils.tokenizeToStringArray(location, CONFIG_LOCATION_DELIMITERS));
	}

	/**
	 * 设置此ApplicationContext的配置文件路径
	 */
	public void setConfigLocations(@Nullable String... locations) {
		if (locations != null) {
			Assert.noNullElements(locations, "Config locations must not be null");
			this.configLocations = new String[locations.length];
			for (int i = 0; i < locations.length; i++) {
				this.configLocations[i] = resolvePath(locations[i]).trim();
			}
		}
		else {
			this.configLocations = null;
		}
	}

	/**
	 * 获取配置文件路径数组，如果不存在调用getDefaultConfigLocations()获取默认值
	 */
	@Nullable
	protected String[] getConfigLocations() {
		return (this.configLocations != null ? this.configLocations : getDefaultConfigLocations());
	}

	/**
	 * 对于未指定显式配置路径的情况，返回要使用的默认配置路径。
	 * 默认实现返回{@code null}，需要明确的配置位置。
	 */
	@Nullable
	protected String[] getDefaultConfigLocations() {
		return null;
	}

	/**
	 * 解析给定的路径，必要时用相应的环境属性值替换占位符。, 应用于配置位置。
	 * @see org.springframework.core.env.Environment#resolveRequiredPlaceholders(String)
	 */
	protected String resolvePath(String path) {
		return getEnvironment().resolveRequiredPlaceholders(path);
	}


	/**
	 * 设置唯一ID
	 */
	@Override
	public void setId(String id) {
		super.setId(id);
		this.setIdCalled = true;
	}

	/**
	 * 默认情况下将此上下文的ID设置为Bean名称。
	 */
	@Override
	public void setBeanName(String name) {
		if (!this.setIdCalled) {
			super.setId(name);
			setDisplayName("ApplicationContext '" + name + "'");
		}
	}

	/**
	 * 初始化方法，如果当前未处于活动状态，则触发{@link #refresh（）}。
	 */
	@Override
	public void afterPropertiesSet() {
		if (!isActive()) {
			refresh();
		}
	}

}
