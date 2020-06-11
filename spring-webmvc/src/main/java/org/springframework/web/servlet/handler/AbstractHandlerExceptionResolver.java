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

package org.springframework.web.servlet.handler;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * HandlerExceptionResolver 实现类的基类。
 * <p>
 * 1 提供当前异常解析器能处理 Handler处理程序对象的集合
 * 2 提供当前异常解析器能处理 Handler处理程序对象的Class数组
 * 3 如果没有设置1，2 表示能处理所有Handler处理程序
 *
 * @since 3.0
 */
public abstract class AbstractHandlerExceptionResolver implements HandlerExceptionResolver, Ordered {

	private static final String HEADER_CACHE_CONTROL = "Cache-Control";


	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * 顺序，优先级最低
	 */
	private int order = Ordered.LOWEST_PRECEDENCE;

	/**
	 * 异常解析器能处理 Handler处理程序对象的集合
	 */
	@Nullable
	private Set<?> mappedHandlers;

	/**
	 * 异常解析器能处理 Handler处理程序对象的Class数组
	 */
	@Nullable
	private Class<?>[] mappedHandlerClasses;

	/**
	 * 打印警告日志
	 */
	@Nullable
	private Log warnLogger;

	/**
	 * 防止响应缓存
	 */
	private boolean preventResponseCaching = false;


	/**
	 * 设置排序优先级别
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	/**
	 * 获取排序优先级别
	 */
	@Override
	public int getOrder() {
		return this.order;
	}


	public void setMappedHandlers(Set<?> mappedHandlers) {
		this.mappedHandlers = mappedHandlers;
	}

	public void setMappedHandlerClasses(Class<?>... mappedHandlerClasses) {
		this.mappedHandlerClasses = mappedHandlerClasses;
	}


	public void setWarnLogCategory(String loggerName) {
		this.warnLogger = (StringUtils.hasLength(loggerName) ? LogFactory.getLog(loggerName) : null);
	}

	public void setPreventResponseCaching(boolean preventResponseCaching) {
		this.preventResponseCaching = preventResponseCaching;
	}


	/**
	 * 使用warnLogger,打印异常
	 */
	protected void logException(Exception ex, HttpServletRequest request) {
		if (this.warnLogger != null && this.warnLogger.isWarnEnabled()) {
			this.warnLogger.warn(buildLogMessage(ex, request));
		}
	}

	protected String buildLogMessage(Exception ex, HttpServletRequest request) {
		return "Resolved [" + ex + "]";
	}

	/**
	 * 设置阻止响应缓存
	 */
	protected void prepareResponse(Exception ex, HttpServletResponse response) {
		if (this.preventResponseCaching) {
			preventCaching(response);
		}
	}

	protected void preventCaching(HttpServletResponse response) {
		response.addHeader(HEADER_CACHE_CONTROL, "no-store");
	}

	/**
	 * 检查此异常解析器是否能处理给定 handler处理程序。
	 */
	protected boolean shouldApplyTo(HttpServletRequest request, @Nullable Object handler) {
		if (handler != null) {
			// <1> 如果 mappedHandlers 包含 handler 对象，则返回 true
			if (this.mappedHandlers != null && this.mappedHandlers.contains(handler)) {
				return true;
			}
			// <2> 如果 mappedHandlerClasses 包含 handler 的类型，则返回 true
			if (this.mappedHandlerClasses != null) {
				for (Class<?> handlerClass : this.mappedHandlerClasses) {
					if (handlerClass.isInstance(handler)) {
						return true;
					}
				}
			}
		}
		// <3> 如果 mappedHandlers 和 mappedHandlerClasses 都为空，说明直接所有handler处理程序
		return (this.mappedHandlers == null && this.mappedHandlerClasses == null);
	}

	/**
	 * Check whether this resolver is supposed to apply (i.e. if the supplied handler
	 * matches any of the configured {@linkplain #setMappedHandlers handlers} or
	 * {@linkplain #setMappedHandlerClasses handler classes}), and then delegate
	 * to the {@link #doResolveException} template method.
	 */
	@Override
	@Nullable
	public ModelAndView resolveException(
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler, Exception ex) {

		/** <1>  检查此异常解析器是否支持处理给定 handler处理程序。**/
		if (shouldApplyTo(request, handler)) {
			/** <2>  设置阻止响应缓存   **/
			prepareResponse(ex, response);
			/** <3>  调用模板方法，子类处理异常   **/
			ModelAndView result = doResolveException(request, response, handler, ex);
			/** <4>  打印日志   **/
			if (result != null) {
				if (logger.isDebugEnabled() && (this.warnLogger == null || !this.warnLogger.isWarnEnabled())) {
					logger.debug("Resolved [" + ex + "]" + (result.isEmpty() ? "" : " to " + result));
				}
				logException(ex, request);
			}
			return result;
		} else {
			//不支持返回null
			return null;
		}
	}


	/**
	 * Actually resolve the given exception that got thrown during handler execution,
	 * returning a {@link ModelAndView} that represents a specific error page if appropriate.
	 * <p>May be overridden in subclasses, in order to apply specific exception checks.
	 * Note that this template method will be invoked <i>after</i> checking whether this
	 * resolved applies ("mappedHandlers" etc), so an implementation may simply proceed
	 * with its actual exception handling.
	 *
	 * @param request  current HTTP request
	 * @param response current HTTP response
	 * @param handler  the executed handler, or {@code null} if none chosen at the time
	 *                 of the exception (for example, if multipart resolution failed)
	 * @param ex       the exception that got thrown during handler execution
	 * @return a corresponding {@code ModelAndView} to forward to,
	 * or {@code null} for default processing in the resolution chain
	 */
	@Nullable
	protected abstract ModelAndView doResolveException(
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler, Exception ex);

}
