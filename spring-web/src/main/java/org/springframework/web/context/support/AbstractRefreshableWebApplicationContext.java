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

package org.springframework.web.context.support;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.AbstractRefreshableConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.ui.context.Theme;
import org.springframework.ui.context.ThemeSource;
import org.springframework.ui.context.support.UiApplicationContextUtils;
import org.springframework.util.Assert;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ServletConfigAware;
import org.springframework.web.context.ServletContextAware;

/**
 *
 */
public abstract class AbstractRefreshableWebApplicationContext extends AbstractRefreshableConfigApplicationContext
		implements ConfigurableWebApplicationContext, ThemeSource {

	/**
	 * web环境servletContext
	 */
	@Nullable
	private ServletContext servletContext;

	/**
	 * web环境ServletConfig
	 */
	@Nullable
	private ServletConfig servletConfig;


	/** 命名空间，如果为root，则为{@code null}。 */
	@Nullable
	private String namespace;


	/** 主题功能组件 */
	@Nullable
	private ThemeSource themeSource;


	/**
	 * 实例化AbstractRefreshableWebApplicationContext
	 */
	public AbstractRefreshableWebApplicationContext() {
		setDisplayName("Root WebApplicationContext");
	}


	/**
	 * 设置ServletContext
	 */
	@Override
	public void setServletContext(@Nullable ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	/**
	 * 获取ServletContext
	 */
	@Override
	@Nullable
	public ServletContext getServletContext() {
		return this.servletContext;
	}

	/**
	 * 设置ServletConfig
	 */
	@Override
	public void setServletConfig(@Nullable ServletConfig servletConfig) {
		this.servletConfig = servletConfig;
		if (servletConfig != null && this.servletContext == null) {
			setServletContext(servletConfig.getServletContext());
		}
	}

	/**
	 * 获取ServletConfig
	 */
	@Override
	@Nullable
	public ServletConfig getServletConfig() {
		return this.servletConfig;
	}

	/**
	 * 设置命名空间
	 */
	@Override
	public void setNamespace(@Nullable String namespace) {
		this.namespace = namespace;
		if (namespace != null) {
			setDisplayName("WebApplicationContext for namespace '" + namespace + "'");
		}
	}

	/**
	 * 返回命名空间
	 */
	@Override
	@Nullable
	public String getNamespace() {
		return this.namespace;
	}

	/**
	 * 返回资源配置文件数组
	 */
	@Override
	public String[] getConfigLocations() {
		return super.getConfigLocations();
	}

	/**
	 * 返回应用名称
	 */
	@Override
	public String getApplicationName() {
		return (this.servletContext != null ? this.servletContext.getContextPath() : "");
	}

	/**
	 * 重写了父类实现，创建并返回一个新的Environment 类型为{@link StandardServletEnvironment}
	 */
	@Override
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardServletEnvironment();
	}

	/**
	 * 重写父类方法，对于WEB环境 ApplicationContext内部BeanFactory做特殊处理
	 */
	@Override
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		/**
		 * 注册BeanPostProcessor 实现类ServletContextAwareProcessor 给beanFactory，负责在bean初始化前判断是否实现ServletContextAware，ServletConfigAware
		 * 如果实现调用实现接口方法将ServletContext，ServletConfig设置给bean **/
		beanFactory.addBeanPostProcessor(new ServletContextAwareProcessor(this.servletContext, this.servletConfig));

		/** 设置忽略自动装配的接口 **/
		beanFactory.ignoreDependencyInterface(ServletContextAware.class);
		beanFactory.ignoreDependencyInterface(ServletConfigAware.class);

		/** 将WEB环境特定的域（scope）处理类注册到beanFactory中 **/
		WebApplicationContextUtils.registerWebApplicationScopes(beanFactory, this.servletContext);

		/** 将WEB环境特定对象ServletContext,ServletConfig信息注册到BeanFactory中**/
		WebApplicationContextUtils.registerEnvironmentBeans(beanFactory, this.servletContext, this.servletConfig);
	}

	/**
	 * 获取ServletContext根目录资源
	 */
	@Override
	protected Resource getResourceByPath(String path) {
		Assert.state(this.servletContext != null, "No ServletContext available");
		return new ServletContextResource(this.servletContext, path);
	}

	/**
	 * 获取ServletContext根目录 资源模式解析器
	 */
	@Override
	protected ResourcePatternResolver getResourcePatternResolver() {
		return new ServletContextResourcePatternResolver(this);
	}

	/**
	 * 重写父类方法，初始化主题功能组件。
	 */
	@Override
	protected void onRefresh() {
		this.themeSource = UiApplicationContextUtils.initThemeSource(this);
	}

	/**
	 * 重写父类方法 初始化PropertySources
	 * 新建Environment，如果类型ConfigurableWebEnvironment则调用initPropertySources初始化
	 */
	@Override
	protected void initPropertySources() {
		ConfigurableEnvironment env = getEnvironment();
		if (env instanceof ConfigurableWebEnvironment) {
			((ConfigurableWebEnvironment) env).initPropertySources(this.servletContext, this.servletConfig);
		}
	}

	/**
	 * 返回主题功能组件themeSource
	 */
	@Override
	@Nullable
	public Theme getTheme(String themeName) {
		Assert.state(this.themeSource != null, "No ThemeSource available");
		return this.themeSource.getTheme(themeName);
	}

}
