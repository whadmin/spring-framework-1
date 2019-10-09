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

package org.springframework.context.support;

import java.io.IOException;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.lang.Nullable;

/**
 *
 */
public abstract class AbstractRefreshableApplicationContext extends AbstractApplicationContext {

	/**
	 * 是否允许覆盖同名称的不同定义的BeanDefinition对象
	 */
	@Nullable
	private Boolean allowBeanDefinitionOverriding;

	/**
	 * 是否允许bean之间存在循环依赖
	 */
	@Nullable
	private Boolean allowCircularReferences;


	/** ApplicationContext 内部 BeanFactory */
	@Nullable
	private DefaultListableBeanFactory beanFactory;



	/** 内部BeanFactory的同步监视器。 */
	private final Object beanFactoryMonitor = new Object();


	/**
	 * 实例化一个没有父级的新AbstractRefreshableApplicationContext。
	 */
	public AbstractRefreshableApplicationContext() {
	}

	/**
	 * 使用给定的父上下文实例化一个新的AbstractRefreshableApplicationContext。
	 * @param parent the parent context
	 */
	public AbstractRefreshableApplicationContext(@Nullable ApplicationContext parent) {
		super(parent);
	}


	/**
	 * 设置是否允许覆盖同名称的不同定义的BeanDefinition对象
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowBeanDefinitionOverriding
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	/**
	 * 设置是否允许bean之间存在循环依赖
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowCircularReferences
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}


	/**
	 * 刷新BeanFactory
	 */
	@Override
	protected final void refreshBeanFactory() throws BeansException {
		/** 确定此ApplicationContext当前是否拥有BeanFactory，**/
		if (hasBeanFactory()) {
			/**
			 * 销毁ApplicationContext管理所有Bean实例，默认实现销毁此上下文中所有缓存的单例
			 * 如果Bean类型为DisposableBean则调用{@code disposablebean.destroy（）}
			 * 如果beanDefinition指定的DestroyMethodName则调用指定销毁方法
			 */
			destroyBeans();
			/** 清理此ApplicationContext拥有BeanFactory **/
			closeBeanFactory();
		}
		try {
			/**
			 * 为此ApplicationContext创建一个内部bean工厂。每次尝试{@link\refresh（）}时调用。
			 * <p>默认实现创建一个DefaultListableBeanFactory
			 */
			DefaultListableBeanFactory beanFactory = createBeanFactory();
			/** 设置内部beanFactory 唯一ID **/
			beanFactory.setSerializationId(getId());
			/**
			 * 设置内部BeanFactory 配置属性
			 *  1. 是否允许覆盖同名称的不同定义的BeanDefinition对象
			 *  2. 是否允许bean之间存在循环依赖
			 */
			customizeBeanFactory(beanFactory);
			/** 加载 BeanDefinition 模板方法，不同子类按照不同的方式去加载 **/
			loadBeanDefinitions(beanFactory);
			/** 设置 ApplicationContext 的 BeanFactory **/
			synchronized (this.beanFactoryMonitor) {
				this.beanFactory = beanFactory;
			}
		}
		catch (IOException ex) {
			throw new ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex);
		}
	}

	/**
	 * 关闭ApplicationContext刷新功能。
	 * 调用父类）
	 */
	@Override
	protected void cancelRefresh(BeansException ex) {
		synchronized (this.beanFactoryMonitor) {
			if (this.beanFactory != null) {
				this.beanFactory.setSerializationId(null);
			}
		}
		super.cancelRefresh(ex);
	}

	/**
	 * 清理此ApplicationContext拥有BeanFactory
	 */
	@Override
	protected final void closeBeanFactory() {
		synchronized (this.beanFactoryMonitor) {
			if (this.beanFactory != null) {
				this.beanFactory.setSerializationId(null);
				this.beanFactory = null;
			}
		}
	}

	/**
	 * 确定此ApplicationContext当前是否拥有BeanFactory，
	 */
	protected final boolean hasBeanFactory() {
		synchronized (this.beanFactoryMonitor) {
			return (this.beanFactory != null);
		}
	}

	/**
	 * 返回内部BeanFactory
	 */
	@Override
	public final ConfigurableListableBeanFactory getBeanFactory() {
		synchronized (this.beanFactoryMonitor) {
			if (this.beanFactory == null) {
				throw new IllegalStateException("BeanFactory not initialized or already closed - " +
						"call 'refresh' before accessing beans via the ApplicationContext");
			}
			return this.beanFactory;
		}
	}

	/**
	 * 重写以使其变为无操作，无需再判断当前ApplicationContext状态是否运行
	 */
	@Override
	protected void assertBeanFactoryActive() {
	}

	/**
	 * 为ApplicationContext创建一个内部BeanFactory。再触发{@link\refresh（）}时调用。
	 * DefaultListableBeanFactory是其默认实现
	 */
	protected DefaultListableBeanFactory createBeanFactory() {
		return new DefaultListableBeanFactory(getInternalParentBeanFactory());
	}

	/**
	 * 设置内部BeanFactory 配置属性
	 *  1. 是否允许覆盖同名称的不同定义的BeanDefinition对象
	 *  2. 是否允许bean之间存在循环依赖
	 */
	protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
		/** 1. 是否允许覆盖同名称的不同定义的BeanDefinition对象 **/
		if (this.allowBeanDefinitionOverriding != null) {
			beanFactory.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
		}
		/** 2. 是否允许bean之间存在循环依赖 **/
		if (this.allowCircularReferences != null) {
			beanFactory.setAllowCircularReferences(this.allowCircularReferences);
		}
	}

	/**
	 * 通常通过委派一个或多个bean定义读取器，将bean定义加载到给定的bean工厂中。
	 * @see org.springframework.beans.factory.support.PropertiesBeanDefinitionReader
	 * @see org.springframework.beans.factory.xml.XmlBeanDefinitionReader
	 */
	protected abstract void loadBeanDefinitions(DefaultListableBeanFactory beanFactory)
			throws BeansException, IOException;

}
