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

import java.io.Closeable;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.lang.Nullable;

/**
 * SPI interface to be implemented by most if not all application contexts.
 * Provides facilities to configure an application context in addition
 * to the application context client methods in the
 * {@link org.springframework.context.ApplicationContext} interface.
 *
 * <p>Configuration and lifecycle methods are encapsulated here to avoid
 * making them obvious to ApplicationContext client code. The present
 * methods should only be used by startup and shutdown code.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Sam Brannen
 * @since 03.11.2003
 */
public interface ConfigurableApplicationContext extends ApplicationContext, Lifecycle, Closeable {

	/**
	 * String表示多个配置路径之间的分隔符
	 */
	String CONFIG_LOCATION_DELIMITERS = ",; \t\n";

	/**
	 * BeanFactory中ConversionService bean的名称
	 */
	String CONVERSION_SERVICE_BEAN_NAME = "conversionService";

	/**
	 * BeanFactory中LoadTimeWeaver bean的名称
	 */
	String LOAD_TIME_WEAVER_BEAN_NAME = "loadTimeWeaver";

	/**
	 * BeanFactory中Environment bean的名称
	 */
	String ENVIRONMENT_BEAN_NAME = "environment";

	/**
	 * BeanFactory中System#getProperties() bean的名称
	 */
	String SYSTEM_PROPERTIES_BEAN_NAME = "systemProperties";

	/**
	 * BeanFactory中System#getenv() bean的名称
	 */
	String SYSTEM_ENVIRONMENT_BEAN_NAME = "systemEnvironment";

	/**
	 * 关闭钩子线程名称
	 */
	String SHUTDOWN_HOOK_THREAD_NAME = "SpringContextShutdownHook";


	/**
	 * 设置此ApplicationContext文的唯一ID。
	 */
	void setId(String id);

	/**
	 * 设置父ApplicationContext
	 */
	void setParent(@Nullable ApplicationContext parent);

	/**
	 * 设置此ApplicationContext对应Environment
	 */
	void setEnvironment(ConfigurableEnvironment environment);

	/**
	 * 返回此ApplicationContext对应Environment
	 */
	@Override
	ConfigurableEnvironment getEnvironment();

	/**
	 * 注册BeanFactoryPostProcessor，
	 * BeanFactoryPostProcessor在刷新执行postProcessBeanFactory时被调用，对BeanFactory功能做扩展
	 */
	void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor);

	/**
	 * 注册触发ApplicationEvent事件监听器ApplicationListener
	 */
	void addApplicationListener(ApplicationListener<?> listener);

	/**
	 * 注册指定的协议解析器 ProtocolResolver
	 */
	void addProtocolResolver(ProtocolResolver resolver);

	/**
	 * 刷新
	 */
	void refresh() throws BeansException, IllegalStateException;

	/**
	 * 注册一个关闭钩子名称为"SpringContextShutdownHook"线程，
	 * 在JVM关闭时被触发，调用{@code doClose()}执行实际关闭操作
	 */
	void registerShutdownHook();

	/**
	 * 关闭此ApplicationContext，释放实现可能持有的所有资源和锁
	 */
	@Override
	void close();

	/**
	 * 确定此ApplicationContext文是否处于活动状态，即，是否至少刷新一次并且尚未关闭。
	 */
	boolean isActive();

	/**
	 * 返回此应用程序上下文的内部BeanFactory
	 */
	ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;

}
