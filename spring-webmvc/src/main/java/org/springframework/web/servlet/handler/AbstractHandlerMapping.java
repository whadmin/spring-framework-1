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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.PathMatcher;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.context.support.WebApplicationObjectSupport;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

/**
 *
 */
public abstract class AbstractHandlerMapping extends WebApplicationObjectSupport
		implements HandlerMapping, Ordered, BeanNameAware {

	/**
	 * 请求路径urlPath.equals("/*")，默认处理程序
	 */
	@Nullable
	private Object defaultHandler;

	/**
	 * url路径帮助程序
	 */
	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	/**
	 * 路径正则匹配帮助程序
	 */
	private PathMatcher pathMatcher = new AntPathMatcher();

	/**
	 * 配置拦截器
	 */
	private final List<Object> interceptors = new ArrayList<>();

	/**
	 * 从interceptors中解析得拦截器
	 */
	private final List<HandlerInterceptor> adaptedInterceptors = new ArrayList<>();

	/**
	 * 跨域相关的配置
	 */
	@Nullable
	private CorsConfigurationSource corsConfigurationSource;

	/**
	 * 跨域处理器
	 */
	private CorsProcessor corsProcessor = new DefaultCorsProcessor();

	/**
	 * 顺序（可以配置多个HandlerMapping，按顺序优先级执行）
	 */
	private int order = Ordered.LOWEST_PRECEDENCE;  // default: same as non-Ordered

	@Nullable
	private String beanName;


	/**
	 * 设置默认处理程序
	 */
	public void setDefaultHandler(@Nullable Object defaultHandler) {
		this.defaultHandler = defaultHandler;
	}

	/**
	 * 获取默认处理程序
	 */
	@Nullable
	public Object getDefaultHandler() {
		return this.defaultHandler;
	}


	/**
	 * @see org.springframework.web.util.UrlPathHelper#setAlwaysUseFullPath(boolean)
	 * 在获取请求路径时是否包含ServletPath
	 */
	public void setAlwaysUseFullPath(boolean alwaysUseFullPath) {
		this.urlPathHelper.setAlwaysUseFullPath(alwaysUseFullPath);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setAlwaysUseFullPath(alwaysUseFullPath);
		}
	}

	/**
	 * @see org.springframework.web.util.UrlPathHelper#setUrlDecode(boolean),
	 * 获取请求路径是否解码
	 */
	public void setUrlDecode(boolean urlDecode) {
		this.urlPathHelper.setUrlDecode(urlDecode);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setUrlDecode(urlDecode);
		}
	}

	/**
	 * @see org.springframework.web.util.UrlPathHelper#setRemoveSemicolonContent(boolean),
	 * 为“;” （分号）内容应从请求URI中删除。
	 */
	public void setRemoveSemicolonContent(boolean removeSemicolonContent) {
		this.urlPathHelper.setRemoveSemicolonContent(removeSemicolonContent);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setRemoveSemicolonContent(removeSemicolonContent);
		}
	}

	/**
	 * 设置urlPathHelper
	 */
	public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
		Assert.notNull(urlPathHelper, "UrlPathHelper must not be null");
		this.urlPathHelper = urlPathHelper;
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setUrlPathHelper(urlPathHelper);
		}
	}

	/**
	 * 获取urlPathHelper
	 */
	public UrlPathHelper getUrlPathHelper() {
		return this.urlPathHelper;
	}

	/**
	 * 设置pathMatcher
	 */
	public void setPathMatcher(PathMatcher pathMatcher) {
		Assert.notNull(pathMatcher, "PathMatcher must not be null");
		this.pathMatcher = pathMatcher;
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setPathMatcher(pathMatcher);
		}
	}

	/**
	 * 获取pathMatcher
	 */
	public PathMatcher getPathMatcher() {
		return this.pathMatcher;
	}



	/**
	 * 根据URL模式设置“全局” CORS配置。默认情况下，第一个匹配的网址格式与处理程序的CORS配置（如果有）结合在一起。
	 */
	public void setCorsConfigurations(Map<String, CorsConfiguration> corsConfigurations) {
		Assert.notNull(corsConfigurations, "corsConfigurations must not be null");
		if (!corsConfigurations.isEmpty()) {
			UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
			source.setCorsConfigurations(corsConfigurations);
			source.setPathMatcher(this.pathMatcher);
			source.setUrlPathHelper(this.urlPathHelper);
			source.setLookupPathAttributeName(LOOKUP_PATH);
			this.corsConfigurationSource = source;
		} else {
			this.corsConfigurationSource = null;
		}
	}

	/**
	 * 设置“全局” CORS配置源。默认情况下，第一个匹配的URL模式与处理程序的CORS配置（如果有）结合在一起。
	 */
	public void setCorsConfigurationSource(CorsConfigurationSource corsConfigurationSource) {
		Assert.notNull(corsConfigurationSource, "corsConfigurationSource must not be null");
		this.corsConfigurationSource = corsConfigurationSource;
	}

	/**
	 * 配置{@link CorsProcessor}
	 */
	public void setCorsProcessor(CorsProcessor corsProcessor) {
		Assert.notNull(corsProcessor, "CorsProcessor must not be null");
		this.corsProcessor = corsProcessor;
	}

	/**
	 * 返回{@link CorsProcessor}。
	 */
	public CorsProcessor getCorsProcessor() {
		return this.corsProcessor;
	}

	/**
	 * 设置排序优先级
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	/**
	 * 获取排序优先级
	 */
	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * 设置bean名称
	 */
	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}

	/**
	 * 获取bean名称
	 */
	protected String formatMappingName() {
		return this.beanName != null ? "'" + this.beanName + "'" : "<unknown>";
	}


	/**
	 * 设置拦截器，这里设置拦截器是未进行解析适配的
	 */
	public void setInterceptors(Object... interceptors) {
		this.interceptors.addAll(Arrays.asList(interceptors));
	}

	/**
	 * 初始化应用程序上下文
	 */
	@Override
	protected void initApplicationContext() throws BeansException {
		// 给子类扩展：增加拦截器，默认为空实现
		extendInterceptors(this.interceptors);
		// 找到所有MappedInterceptor类型的bean添加到adaptedInterceptors中
		detectMappedInterceptors(this.adaptedInterceptors);
		initInterceptors();
	}

	/**
	 * 给子类扩展：增加拦截器，默认为空实现
	 */
	protected void extendInterceptors(List<Object> interceptors) {
	}

	/**
	 * 检测类型为{@link MappedInterceptor}的bean，并将其添加到映射的拦截器列表中
	 */
	protected void detectMappedInterceptors(List<HandlerInterceptor> mappedInterceptors) {
		mappedInterceptors.addAll(
				BeanFactoryUtils.beansOfTypeIncludingAncestors(
						obtainApplicationContext(), MappedInterceptor.class, true, false).values());
	}

	/**
	 * 初始化拦截器，检查{@link AbstractHandlerMapping#setInterceptors(Object...)}方法设置拦截器
	 * 遍历设置拦截器，判断其类型进行适配，想符合适配标准的拦截器添加到adaptedInterceptors
	 */
	protected void initInterceptors() {
		if (!this.interceptors.isEmpty()) {
			for (int i = 0; i < this.interceptors.size(); i++) {
				Object interceptor = this.interceptors.get(i);
				if (interceptor == null) {
					throw new IllegalArgumentException("Entry number " + i + " in interceptors array is null");
				}
				this.adaptedInterceptors.add(adaptInterceptor(interceptor));
			}
		}
	}

	/**
	 * 将给定的拦截器对象进行适配
	 * 对于类型为 {@link HandlerInterceptor} 适配为 {@link HandlerInterceptor}
	 * 对于类型为 {@link WebRequestInterceptor} 适配为 {@link WebRequestHandlerInterceptorAdapter}
	 * 对于其他类型拦截器，将抛出异常
	 */
	protected HandlerInterceptor adaptInterceptor(Object interceptor) {
		if (interceptor instanceof HandlerInterceptor) {
			return (HandlerInterceptor) interceptor;
		} else if (interceptor instanceof WebRequestInterceptor) {
			return new WebRequestHandlerInterceptorAdapter((WebRequestInterceptor) interceptor);
		} else {
			throw new IllegalArgumentException("Interceptor type not supported: " + interceptor.getClass().getName());
		}
	}

	/**
	 * 将适配后的拦截器作为{@link HandlerInterceptor}数组返回。
	 */
	@Nullable
	protected final HandlerInterceptor[] getAdaptedInterceptors() {
		return (!this.adaptedInterceptors.isEmpty() ?
				this.adaptedInterceptors.toArray(new HandlerInterceptor[0]) : null);
	}

	/**
	 * 将适配后的拦截器中类型为{@link MappedInterceptor MappedInterceptors}作为数组。
	 */
	@Nullable
	protected final MappedInterceptor[] getMappedInterceptors() {
		List<MappedInterceptor> mappedInterceptors = new ArrayList<>(this.adaptedInterceptors.size());
		for (HandlerInterceptor interceptor : this.adaptedInterceptors) {
			if (interceptor instanceof MappedInterceptor) {
				mappedInterceptors.add((MappedInterceptor) interceptor);
			}
		}
		return (!mappedInterceptors.isEmpty() ? mappedInterceptors.toArray(new MappedInterceptor[0]) : null);
	}


	/**
	 * 查找给定请求的处理程序，如果未找到特定的处理程序，则退回到默认处理程序。
	 */
	@Override
	@Nullable
	public final HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		//调用模板方法，通过不同子类内部实现如何获取请求的处理程序handler
		Object handler = getHandlerInternal(request);
		//如果未找到匹配的处理程序handler，则返回默认处理程序handler
		if (handler == null) {
			handler = getDefaultHandler();
		}
		//如果为设置默认处理程序handler，返回null
		if (handler == null) {
			return null;
		}
		// 如果处理程序handler类型为String,通过应用程序上下文获取该beanm名称对象实例作为处理程序handler
		if (handler instanceof String) {
			String handlerName = (String) handler;
			handler = obtainApplicationContext().getBean(handlerName);
		}
		//创建HandlerExecutionChain包装handel
		HandlerExecutionChain executionChain = getHandlerExecutionChain(handler, request);

		if (logger.isTraceEnabled()) {
			logger.trace("Mapped to " + handler);
		} else if (logger.isDebugEnabled() && !request.getDispatcherType().equals(DispatcherType.ASYNC)) {
			logger.debug("Mapped to " + executionChain.getHandler());
		}

		//如果存在CORS跨域资源共享的支持
		if (hasCorsConfigurationSource(handler)) {
			CorsConfiguration config = (this.corsConfigurationSource != null ? this.corsConfigurationSource.getCorsConfiguration(request) : null);
			CorsConfiguration handlerConfig = getCorsConfiguration(handler, request);
			config = (config != null ? config.combine(handlerConfig) : handlerConfig);
			executionChain = getCorsHandlerExecutionChain(request, executionChain, config);
		}

		//返回
		return executionChain;
	}

	/**
	 * 子类实现的模板方法
	 */
	@Nullable
	protected abstract Object getHandlerInternal(HttpServletRequest request) throws Exception;

	/**
	 *  通过请求URL获取处理程序Handler被包装在{@link HandlerExecutionChain}处理程序执行链实例中，
	 *
	 *  处理程序执行链中管理着{@link HandlerInterceptor}实例。
	 *
	 *  DispatcherServlet执行{@link HandlerExecutionChain}处理程序执行链实例顺序是
	 *
	 *  1 将首先以给定的顺序调用每个HandlerInterceptor的{@code preHandle}方法，所有拦截器满足返回true之后
	 *  2 执行处理程序Handler
	 *  3 执行顺序调用每个HandlerInterceptor的{@code postHandle}方法
	 *  4 执行顺序调用每个HandlerInterceptor的{@code afterCompletion}方法
	 *
	 */
	protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
		HandlerExecutionChain chain = (handler instanceof HandlerExecutionChain ?
				(HandlerExecutionChain) handler : new HandlerExecutionChain(handler));
        //获取请求路径
		String lookupPath = this.urlPathHelper.getLookupPathForRequest(request, LOOKUP_PATH);
		//遍历适配后的拦截器
		for (HandlerInterceptor interceptor : this.adaptedInterceptors) {
			//对于MappedInterceptor拦截器，判断请求路径是否匹配，匹配则添加到处理程序执行链
			if (interceptor instanceof MappedInterceptor) {
				MappedInterceptor mappedInterceptor = (MappedInterceptor) interceptor;
				if (mappedInterceptor.matches(lookupPath, this.pathMatcher)) {
					chain.addInterceptor(mappedInterceptor.getInterceptor());
				}
			}
			//其他类型拦截器全部添加到处理程序执行链
			else {
				chain.addInterceptor(interceptor);
			}
		}
		//返回
		return chain;
	}

	/**
	 * 检索给定处理程序是否存在{@link CorsConfigurationSource}
	 *
	 * @since 5.2
	 */
	protected boolean hasCorsConfigurationSource(Object handler) {
		return handler instanceof CorsConfigurationSource || this.corsConfigurationSource != null;
	}

	/**
	 * 检索给定处理程序的CORS配置。
	 * @since 4.2
	 */
	@Nullable
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		Object resolvedHandler = handler;
		if (handler instanceof HandlerExecutionChain) {
			resolvedHandler = ((HandlerExecutionChain) handler).getHandler();
		}
		if (resolvedHandler instanceof CorsConfigurationSource) {
			return ((CorsConfigurationSource) resolvedHandler).getCorsConfiguration(request);
		}
		return null;
	}


	protected HandlerExecutionChain getCorsHandlerExecutionChain(HttpServletRequest request,
																 HandlerExecutionChain chain, @Nullable CorsConfiguration config) {

		if (CorsUtils.isPreFlightRequest(request)) {
			HandlerInterceptor[] interceptors = chain.getInterceptors();
			chain = new HandlerExecutionChain(new PreFlightHandler(config), interceptors);
		} else {
			chain.addInterceptor(0, new CorsInterceptor(config));
		}
		return chain;
	}


	private class PreFlightHandler implements HttpRequestHandler, CorsConfigurationSource {

		@Nullable
		private final CorsConfiguration config;

		public PreFlightHandler(@Nullable CorsConfiguration config) {
			this.config = config;
		}

		@Override
		public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
			corsProcessor.processRequest(this.config, request, response);
		}

		@Override
		@Nullable
		public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
			return this.config;
		}
	}


	private class CorsInterceptor extends HandlerInterceptorAdapter implements CorsConfigurationSource {

		@Nullable
		private final CorsConfiguration config;

		public CorsInterceptor(@Nullable CorsConfiguration config) {
			this.config = config;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
				throws Exception {

			return corsProcessor.processRequest(this.config, request, response);
		}

		@Override
		@Nullable
		public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
			return this.config;
		}
	}

}
