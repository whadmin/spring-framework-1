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

package org.springframework.web.servlet.handler;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.util.ObjectUtils;

/**
 *
 * 检查应用程序上下文application context中所有bean（类型），将满足作为处理器Handler条件的Bean进行注册
 *
 * 1 这里每个Bean对应Handler，却并没有要求实现Controller接口或Controller子实现，但我们知道在实现上是必须的
 *
 *    public class HelloWorldController implements Controller
 *
 * 2 这里满足作为处理器Handler条件是调用determineUrlsForHandler模板方法能否获取请求Url
 *
 */
public abstract class AbstractDetectingUrlHandlerMapping extends AbstractUrlHandlerMapping {

	/**
	 * 如果当前应用程序上下文application context存在父应用程序上下文application context
	 * 是否将父应用程序上下文application context中每一个Bean注册为一个处理器Handler
	 */
	private boolean detectHandlersInAncestorContexts = false;


	public void setDetectHandlersInAncestorContexts(boolean detectHandlersInAncestorContexts) {
		this.detectHandlersInAncestorContexts = detectHandlersInAncestorContexts;
	}


	/**
	 * 重写初始化应用程序上下文application context
	 */
	@Override
	public void initApplicationContext() throws ApplicationContextException {
		//1 调用父类{@link super#initApplicationContext()}
		super.initApplicationContext();
		//2 将检查应用程序上下文application context中所有bean（类型）,并将每一个Bean注册为一个处理器Handler
		detectHandlers();
	}

	/**
	 * 检查应用程序上下文application context中所有bean（类型），将满足作为处理器Handler条件的Bean进行注册
	 * 1 这里每个Bean对应Handler
	 * 2 这里满足作为处理器Handler条件是调用determineUrlsForHandler模板方法能否获取请求Url
	 * @see #determineUrlsForHandler(String)
	 */
	protected void detectHandlers() throws BeansException {
		//获取实际使用的ApplicationContext。
		ApplicationContext applicationContext = obtainApplicationContext();
		//检查应用程序上下文application context中所有bean（类型）
		//如果设置启用detectHandlersInAncestorContexts，同时会从父程序上下文application context中所有bean，并将其合并
		String[] beanNames = (this.detectHandlersInAncestorContexts ?
				BeanFactoryUtils.beanNamesForTypeIncludingAncestors(applicationContext, Object.class) :
				applicationContext.getBeanNamesForType(Object.class));

		//遍历beanNames,获取bean作为Handler关联的请求Url
		for (String beanName : beanNames) {
			//确定给定处理程序bean的URL。
			String[] urls = determineUrlsForHandler(beanName);
			//将能获取配置请求URLbean，作为一个Bean注册
			if (!ObjectUtils.isEmpty(urls)) {
				// URL paths found: Let's consider it a handler.
				registerHandler(urls, beanName);
			}
		}
		//打印日志
		if ((logger.isDebugEnabled() && !getHandlerMap().isEmpty()) || logger.isTraceEnabled()) {
			logger.debug("Detected " + getHandlerMap().size() + " mappings in " + formatMappingName());
		}
	}


	/**
	 * 确定给定处理程序bean的URL。
	 * @param beanName 候选bean的名称
	 * @return 为bean确定的URL，如果没有，则返回一个空数组
	 */
	protected abstract String[] determineUrlsForHandler(String beanName);

}
