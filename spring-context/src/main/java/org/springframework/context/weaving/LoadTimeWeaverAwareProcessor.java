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

package org.springframework.context.weaving;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link BeanPostProcessor}一种实现，该实现将ApplicationContext默认{@link LoadTimeWeaver}传递给实现{@link LoadTimeWeaverAware}接口的bean。
 *
 */
public class LoadTimeWeaverAwareProcessor implements BeanPostProcessor, BeanFactoryAware {

	@Nullable
	private LoadTimeWeaver loadTimeWeaver;

	@Nullable
	private BeanFactory beanFactory;


	/**
	 * 创建一个新的{@code LoadTimeWeaverAwareProcessor}，其内部LoadTimeWeaver通过Bean名称"loadTimeWeaver"，从BeanFactory中获取
	 */
	public LoadTimeWeaverAwareProcessor() {
	}

	/**
	 * 创建一个新的{@code loadtimeweaverawareprocessor}，为其指定 LoadTimeWeaver
	 */
	public LoadTimeWeaverAwareProcessor(@Nullable LoadTimeWeaver loadTimeWeaver) {
		this.loadTimeWeaver = loadTimeWeaver;
	}

	/**
	 * 创建一个新的{@code loadtimeweaverawareprocessor}，为其指定 BeanFactory
	 */
	public LoadTimeWeaverAwareProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	/**
	 * 设置beanFactory
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	/**
	 * bean初始化前回调
	 */
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		/** 判断Bean类型是否为LoadTimeWeaverAware **/
		if (bean instanceof LoadTimeWeaverAware) {
			/** 获取LoadTimeWeaver **/
			LoadTimeWeaver ltw = this.loadTimeWeaver;
			/** 如果获取LoadTimeWeaver不存在，通过"loadTimeWeaver"名称从beanFactory获取Bean实例对象**/
			if (ltw == null) {
				Assert.state(this.beanFactory != null,
						"BeanFactory required if no LoadTimeWeaver explicitly specified");
				ltw = this.beanFactory.getBean(
						ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME, LoadTimeWeaver.class);
			}
			/** {@link LoadTimeWeaver}传递给实现{@link LoadTimeWeaverAware}接口的bean。 **/
			((LoadTimeWeaverAware) bean).setLoadTimeWeaver(ltw);
		}
		return bean;
	}

	/**
	 * 初始化Bean后回调
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String name) {
		return bean;
	}

}
