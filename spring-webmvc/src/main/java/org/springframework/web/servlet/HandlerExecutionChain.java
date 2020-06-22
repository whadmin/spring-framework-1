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

package org.springframework.web.servlet;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

/**
 * 处理程序执行链，由处理程序对象和任何处理程序拦截器组成。
 * 由HandlerMapping的{@link HandlerMapping＃getHandler}方法返回。
 */
public class HandlerExecutionChain {

	/**
	 * 日志
	 */
	private static final Log logger = LogFactory.getLog(HandlerExecutionChain.class);

	/**
	 * 处理程序对象
	 */
	private final Object handler;

	/**
	 * 拦截器数组
	 */
	@Nullable
	private HandlerInterceptor[] interceptors;

	/**
	 * 拦截器列表
	 */
	@Nullable
	private List<HandlerInterceptor> interceptorList;

	/**
	 * 记录拦截器执行数组下标
	 */
	private int interceptorIndex = -1;


	/**
	 * 创建一个新的HandlerExecutionChain。
	 */
	public HandlerExecutionChain(Object handler) {
		this(handler, (HandlerInterceptor[]) null);
	}

	/**
	 * 创建一个新的HandlerExecutionChain。
	 */
	public HandlerExecutionChain(Object handler, @Nullable HandlerInterceptor... interceptors) {
		// 如果处理程序本身是一个HandlerExecutionChain。，将拷贝器拦截器，设置行创建的HandlerExecutionChain。
		if (handler instanceof HandlerExecutionChain) {
			HandlerExecutionChain originalChain = (HandlerExecutionChain) handler;
			this.handler = originalChain.getHandler();
			this.interceptorList = new ArrayList<>();
			CollectionUtils.mergeArrayIntoCollection(originalChain.getInterceptors(), this.interceptorList);
			CollectionUtils.mergeArrayIntoCollection(interceptors, this.interceptorList);
		}
		// 设置处理程序handler，拦截器数组
		else {
			this.handler = handler;
			this.interceptors = interceptors;
		}
	}


	/**
	 * 获取 处理程序对象
	 */
	public Object getHandler() {
		return this.handler;
	}


	/**
	 * 拦截器列表添加拦截器
	 */
	public void addInterceptor(HandlerInterceptor interceptor) {
		initInterceptorList().add(interceptor);
	}

	/**
	 * 拦截器列表添加拦截器
	 */
	public void addInterceptor(int index, HandlerInterceptor interceptor) {
		initInterceptorList().add(index, interceptor);
	}

	/**
	 * 拦截器列表添加拦截器
	 */
	public void addInterceptors(HandlerInterceptor... interceptors) {
		if (!ObjectUtils.isEmpty(interceptors)) {
			CollectionUtils.mergeArrayIntoCollection(interceptors, initInterceptorList());
		}
	}

	/**
	 * 初始化拦截器列表，并清空蓝机器数组
	 */
	private List<HandlerInterceptor> initInterceptorList() {
		if (this.interceptorList == null) {
			this.interceptorList = new ArrayList<>();
			if (this.interceptors != null) {
				CollectionUtils.mergeArrayIntoCollection(this.interceptors, this.interceptorList);
			}
		}
		this.interceptors = null;
		return this.interceptorList;
	}

	/**
	 * 返回{@link HandlerInterceptor}实例的数组（以给定顺序）（可以为{@code null}）
	 */
	@Nullable
	public HandlerInterceptor[] getInterceptors() {
		if (this.interceptors == null && this.interceptorList != null) {
			this.interceptors = this.interceptorList.toArray(new HandlerInterceptor[0]);
		}
		return this.interceptors;
	}


	/**
	 * 顺序执行所有拦截器{@link HandlerInterceptor#preHandle}方法，进行前置处理,所有通过返回true
	 */
	boolean applyPreHandle(HttpServletRequest request, HttpServletResponse response) throws Exception {
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			for (int i = 0; i < interceptors.length; i++) {
				HandlerInterceptor interceptor = interceptors[i];
				// 如果拦截器{@link HandlerInterceptor#preHandle}前置处理返回false
				if (!interceptor.preHandle(request, response, this.handler)) {
					// 执行顺序执行所有拦截器{@link HandlerInterceptor#afterCompletion}方法，进行后置处理
					triggerAfterCompletion(request, response, null);
					// 返回false
					return false;
				}
				this.interceptorIndex = i;
			}
		}
		return true;
	}

	/**
	 * 顺序执行所有拦截器{@link HandlerInterceptor#postHandle}方法，进行后置处理
	 */
	void applyPostHandle(HttpServletRequest request, HttpServletResponse response, @Nullable ModelAndView mv)
			throws Exception {
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			for (int i = interceptors.length - 1; i >= 0; i--) {
				HandlerInterceptor interceptor = interceptors[i];
				interceptor.postHandle(request, response, this.handler, mv);
			}
		}
	}

	/**
	 * 逆序执行所有拦截器{@link HandlerInterceptor#afterCompletion}方法，进行完成处理
	 */
	void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response, @Nullable Exception ex)
			throws Exception {

		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			for (int i = this.interceptorIndex; i >= 0; i--) {
				HandlerInterceptor interceptor = interceptors[i];
				try {
					interceptor.afterCompletion(request, response, this.handler, ex);
				}
				catch (Throwable ex2) {
					logger.error("HandlerInterceptor.afterCompletion threw exception", ex2);
				}
			}
		}
	}

	/**
	 * 逆序执行所有拦截器{@link AsyncHandlerInterceptor#afterConcurrentHandlingStarted}方法，进行异步处理
	 */
	void applyAfterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response) {
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			for (int i = interceptors.length - 1; i >= 0; i--) {
				if (interceptors[i] instanceof AsyncHandlerInterceptor) {
					try {
						AsyncHandlerInterceptor asyncInterceptor = (AsyncHandlerInterceptor) interceptors[i];
						asyncInterceptor.afterConcurrentHandlingStarted(request, response, this.handler);
					}
					catch (Throwable ex) {
						logger.error("Interceptor [" + interceptors[i] + "] failed in afterConcurrentHandlingStarted", ex);
					}
				}
			}
		}
	}



	@Override
	public String toString() {
		Object handler = getHandler();
		StringBuilder sb = new StringBuilder();
		sb.append("HandlerExecutionChain with [").append(handler).append("] and ");
		if (this.interceptorList != null) {
			sb.append(this.interceptorList.size());
		}
		else if (this.interceptors != null) {
			sb.append(this.interceptors.length);
		}
		else {
			sb.append(0);
		}
		return sb.append(" interceptors").toString();
	}

}
