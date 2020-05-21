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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Abstract base class for URL-mapped {@link org.springframework.web.servlet.HandlerMapping}
 * implementations. Provides infrastructure for mapping handlers to URLs and configurable
 * URL lookup. For information on the latter, see "alwaysUseFullPath" property.
 *
 * <p>Supports direct matches, e.g. a registered "/test" matches "/test", and
 * various Ant-style pattern matches, e.g. a registered "/t*" pattern matches
 * both "/test" and "/team", "/test/*" matches all paths in the "/test" directory,
 * "/test/**" matches all paths below "/test". For details, see the
 * {@link org.springframework.util.AntPathMatcher AntPathMatcher} javadoc.
 *
 * <p>Will search all path patterns to find the most exact match for the
 * current request path. The most exact match is defined as the longest
 * path pattern that matches the current request path.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @since 16.04.2003
 */
public abstract class AbstractUrlHandlerMapping extends AbstractHandlerMapping implements MatchableHandlerMapping {

	/**
	 * 根路径 / 的处理器
	 */
	@Nullable
	private Object rootHandler;

	/**
	 * 是否使用尾部斜杠匹配
	 * 如果启用，URL模式（例如“ / users”）也将匹配到“ / users /”。
	 */
	private boolean useTrailingSlashMatch = false;

	/**
	 * 是否延迟初始化Handler
	 */
	private boolean lazyInitHandlers = false;

	/**
	 * 保存请求URL->Handler Map容器
	 */
	private final Map<String, Object> handlerMap = new LinkedHashMap<>();


	/**
	 * 设置根路径请求（“ /”）处理程序Handler
	 */
	public void setRootHandler(@Nullable Object rootHandler) {
		this.rootHandler = rootHandler;
	}

	/**
	 * 返回根路径请求（“ /”）处理程序Handler
	 */
	@Nullable
	public Object getRootHandler() {
		return this.rootHandler;
	}

	/**
	 * 设置是否使用尾部斜杠匹配
	 */
	public void setUseTrailingSlashMatch(boolean useTrailingSlashMatch) {
		this.useTrailingSlashMatch = useTrailingSlashMatch;
	}

	/**
	 * 返回是否使用尾部斜杠匹配
	 */
	public boolean useTrailingSlashMatch() {
		return this.useTrailingSlashMatch;
	}

	/**
	 * 设置是否延迟初始化处理程序
	 */
	public void setLazyInitHandlers(boolean lazyInitHandlers) {
		this.lazyInitHandlers = lazyInitHandlers;
	}

	/**
	 * 查找给定请求的URL路径的处理程序。
	 */
	@Override
	@Nullable
	protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
		//通过UrlPathHelper工具获取查Http请求中请求路径
		//http请求 http://localhost:8080/beanNameUrlHandlerMapping_war/hello
		//请求路径 = /hello
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		//将请求路径设置到 request LOOKUP_PATH属性中
		request.setAttribute(LOOKUP_PATH, lookupPath);
		//通过查找将请求路径对应处理程序handler
		Object handler = lookupHandler(lookupPath, request);
        //如果没有找到处理程序handler
		if (handler == null) {
			Object rawHandler = null;
			//判断请求路径是否为根路径，如果是获取根路径处理程序handler
			if ("/".equals(lookupPath)) {
				rawHandler = getRootHandler();
			}
			//若还是没有匹配上处理器handler，返回默认的处理程序handler
			if (rawHandler == null) {
				rawHandler = getDefaultHandler();
			}
			//找到处理程序handler
			if (rawHandler != null) {
				// 如果处理程序handler类型为String,通过应用程序上下文获取该beanm名称对象实例作为处理程序handler
				if (rawHandler instanceof String) {
					String handlerName = (String) rawHandler;
					rawHandler = obtainApplicationContext().getBean(handlerName);
				}
				//校验处理程序handler 模板方法
				validateHandler(rawHandler, request);
				//创建HandlerExecutionChain包装handel
				handler = buildPathExposingHandler(rawHandler, lookupPath, lookupPath, null);
			}
		}
		//返回处理程序handler
		return handler;
	}

	/**
	 * 查找给定URL路径的处理程序handler
	 */
	@Nullable
	protected Object lookupHandler(String urlPath, HttpServletRequest request) throws Exception {
		// 1 从handlerMap中获取urlPath请求路径直接匹配的处理程序handler
		Object handler = this.handlerMap.get(urlPath);
		// 如果获取处理程序handler，包装成HandlerExecutionChain返回
		if (handler != null) {
			 // 如果处理程序handler类型为String,通过应用程序上下文获取该beanm名称对象实例作为处理程序handler
			if (handler instanceof String) {
				String handlerName = (String) handler;
				handler = obtainApplicationContext().getBean(handlerName);
			}
			//校验处理程序handler 模板方法
			validateHandler(handler, request);
			//创建HandlerExecutionChain包装handel
			return buildPathExposingHandler(handler, urlPath, urlPath, null);
		}

		// 从handlerMap中无法获取urlPath请求路径直接匹配的处理程序handler
		// 判断配置urlPath请求是否能够和的处理程序handler配置请求URL进行正则匹配


		// 临时存储多个正则匹配urlPath，处理器请求配置：
		// 对于请求/expression，如下配置请求URL正则都匹配
		//  <bean name="/expressio?" class="com.wuhao.web.SupportExpressionController1"/>
		//  <bean name="/expressio*" class="com.wuhao.web.SupportExpressionController2"/>
		// matchingPatterns={"/expressio","/expressio*"}
		List<String> matchingPatterns = new ArrayList<>();
		//2  将请求urlPath和处理器请求配置做正则匹配，匹配设置添加到matchingPatterns
		for (String registeredPattern : this.handlerMap.keySet()) {
			if (getPathMatcher().match(registeredPattern, urlPath)) {
				matchingPatterns.add(registeredPattern);
			}
			//是否开启尾部斜杠正则匹配
			else if (useTrailingSlashMatch()) {
				if (!registeredPattern.endsWith("/") && getPathMatcher().match(registeredPattern + "/", urlPath)) {
					matchingPatterns.add(registeredPattern + "/");
				}
			}
		}

		//3 当一个请求多个处理器程序Handle的请求配置满足时，
		//比如 /expressio?，/expressio* 对于请求请求/expression
		//通过new AntPatternComparator(path)获取最佳匹配存储最佳匹配为/expressio?
		String bestMatch = null;
		Comparator<String> patternComparator = getPathMatcher().getPatternComparator(urlPath);
		if (!matchingPatterns.isEmpty()) {
			//排序
			matchingPatterns.sort(patternComparator);
			if (logger.isTraceEnabled() && matchingPatterns.size() > 1) {
				logger.trace("Matching patterns " + matchingPatterns);
			}
			//获取最佳请求配置
			bestMatch = matchingPatterns.get(0);
		}

		//4 找到最佳请求配置，获取处理程序handler，包装成HandlerExecutionChain返回
		if (bestMatch != null) {
			//获取对处理程序handler
			handler = this.handlerMap.get(bestMatch);
			//如果没有找到，则可能是开启尾部斜杠匹配，可能尾部添加了/，去掉尾部添加了/在匹配
			if (handler == null) {
				if (bestMatch.endsWith("/")) {
					handler = this.handlerMap.get(bestMatch.substring(0, bestMatch.length() - 1));
				}
				if (handler == null) {
					throw new IllegalStateException(
							"Could not find handler for best pattern match [" + bestMatch + "]");
				}
			}
			// 如果处理程序handler类型为String,通过应用程序上下文获取该beanm名称对象实例作为处理程序handler
			if (handler instanceof String) {
				String handlerName = (String) handler;
				handler = obtainApplicationContext().getBean(handlerName);
			}
			//校验处理程序handler 模板方法
			validateHandler(handler, request);

			//获取正则匹配的路径，参考 AntPathMatcherTests
			//assertThat(pathMatcher.extractPathWithinPattern("/docs/*", "/docs/cvs/commit")).isEqualTo("cvs/commit");
			String pathWithinMapping = getPathMatcher().extractPathWithinPattern(bestMatch, urlPath);

			//获取路径中占位符key-value 参考 AntPathMatcherTests
			//Map<String, String> result = pathMatcher.extractUriTemplateVariables("/hotels/{hotel}", "/hotels/1");
			//assertThat(result).isEqualTo(Collections.singletonMap("hotel", "1"));
			Map<String, String> uriTemplateVariables = new LinkedHashMap<>();
			for (String matchingPattern : matchingPatterns) {
				if (patternComparator.compare(bestMatch, matchingPattern) == 0) {
					Map<String, String> vars = getPathMatcher().extractUriTemplateVariables(matchingPattern, urlPath);
					Map<String, String> decodedVars = getUrlPathHelper().decodePathVariables(request, vars);
					uriTemplateVariables.putAll(decodedVars);
				}
			}
			//打印日志
			if (logger.isTraceEnabled() && uriTemplateVariables.size() > 0) {
				logger.trace("URI variables " + uriTemplateVariables);
			}
			//创建HandlerExecutionChain包装handel
			return buildPathExposingHandler(handler, bestMatch, pathWithinMapping, uriTemplateVariables);
		}

		// No handler found...
		return null;
	}

	/**
	 * 校验处理程序handler，模板方法。提供给子类扩展实现
	 */
	protected void validateHandler(Object handler, HttpServletRequest request) throws Exception {
	}

	/**
	 *  通过请求URL获取处理程序Handler被包装在{@link HandlerExecutionChain}处理程序执行链实例中，
	 *
	 *  处理程序执行链中管理着{@link HandlerInterceptor}实例。
	 *
	 *  DispatcherServlet执行{@link HandlerExecutionChain}处理程序执行链实例顺序是
	 *
	 *  1 将首先以给定的顺序调用每个HandlerInterceptor的{@code preHandle}方法，所有拦截器满足返回true之后
	 *  2 执行处理程序Handler
	 *  3 最后执行顺序调用每个HandlerInterceptor的{@code postHandle}方法
	 *
	 */
	protected Object buildPathExposingHandler(Object rawHandler, String bestMatchingPattern,
											  String pathWithinMapping, @Nullable Map<String, String> uriTemplateVariables) {
        // 创建{@link HandlerExecutionChain}处理程序执行链实例，将处理程序包装在其内部
		HandlerExecutionChain chain = new HandlerExecutionChain(rawHandler);
		// 添加PathExposingHandlerInterceptor 拦截器，负责将bestMatchingPattern，pathWithinMapping添加到属性中
		chain.addInterceptor(new PathExposingHandlerInterceptor(bestMatchingPattern, pathWithinMapping));
		// 添加UriTemplateVariablesHandlerInterceptor 拦截器，将uriTemplateVariables添加到属性中
		if (!CollectionUtils.isEmpty(uriTemplateVariables)) {
			chain.addInterceptor(new UriTemplateVariablesHandlerInterceptor(uriTemplateVariables));
		}
		// 返回
		return chain;
	}

	/**
	 * 将bestMatchingPattern，pathWithinMapping设置到request属性。
	 */
	protected void exposePathWithinMapping(String bestMatchingPattern, String pathWithinMapping,
										   HttpServletRequest request) {

		request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, bestMatchingPattern);
		request.setAttribute(PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, pathWithinMapping);
	}

	/**
	 * 将uriTemplateVariables设置到request属性。
	 */
	protected void exposeUriTemplateVariables(Map<String, String> uriTemplateVariables, HttpServletRequest request) {
		request.setAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriTemplateVariables);
	}

	@Override
	@Nullable
	public RequestMatchResult match(HttpServletRequest request, String pattern) {
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request, LOOKUP_PATH);
		if (getPathMatcher().match(pattern, lookupPath)) {
			return new RequestMatchResult(pattern, lookupPath, getPathMatcher());
		} else if (useTrailingSlashMatch()) {
			if (!pattern.endsWith("/") && getPathMatcher().match(pattern + "/", lookupPath)) {
				return new RequestMatchResult(pattern + "/", lookupPath, getPathMatcher());
			}
		}
		return null;
	}

	/**
	 * 为给定的URL路径注册指定的处理程序。
	 *
	 * @param urlPaths 作为处理程序Bean对象对应URL请求路径数组
	 * @param beanName 处理程序bean的名称
	 */
	protected void registerHandler(String[] urlPaths, String beanName) throws BeansException, IllegalStateException {
		//URL请求路径数组不能为NULL
		Assert.notNull(urlPaths, "URL path array must not be null");
		//遍历URL请求路径数组
		for (String urlPath : urlPaths) {
			registerHandler(urlPath, beanName);
		}
	}

	/**
	 * 作为处理程序Bean对象对应URL请求路径
	 *
	 * @param urlPath 作为处理程序Bean对象对应URL请求路径
	 * @param handler 处理程序实例或处理程序bean名称字符串
	 *                （bean名称将自动解析为相应的处理程序bean）
	 */
	protected void registerHandler(String urlPath, Object handler) throws BeansException, IllegalStateException {
		//URL请求路径不能为NULL
		Assert.notNull(urlPath, "URL path must not be null");
		//处理程序实例或处理程序bean名称字符串不能为NULL
		Assert.notNull(handler, "Handler object must not be null");
		//临时存储解析完毕处理程序实例
		Object resolvedHandler = handler;

		// 如果传入handler是处理程序bean名称字符串，且设置为不需要延迟加载处理程序，同时当前Bean在IOC中为单例的类型，
		// 通过获取应用程序上下文，通过bean名称获取bean对象
		if (!this.lazyInitHandlers && handler instanceof String) {
			String handlerName = (String) handler;
			ApplicationContext applicationContext = obtainApplicationContext();
			if (applicationContext.isSingleton(handlerName)) {
				resolvedHandler = applicationContext.getBean(handlerName);
			}
		}

		//从handlerMap获取注册的URL请求是否已经存在处理程序，
		Object mappedHandler = this.handlerMap.get(urlPath);
		//URL请求已经存在处理程序，且和当前注册处理程序不一致，抛出异常
		if (mappedHandler != null) {
			if (mappedHandler != resolvedHandler) {
				throw new IllegalStateException(
						"Cannot map " + getHandlerDescription(handler) + " to URL path [" + urlPath +
								"]: There is already " + getHandlerDescription(mappedHandler) + " mapped.");
			}
		}
		///URL请求不存在处理程序
		else {
			//对于URL请求为"/",设置当前处理程序为根路径处理程序
			if (urlPath.equals("/")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Root mapping to " + getHandlerDescription(handler));
				}
				//设置当前注册处理程序为根路径处理程序
				setRootHandler(resolvedHandler);
			}
			//对于URL请求为"/*",设置当前处理程序为默认处理程序
			else if (urlPath.equals("/*")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Default mapping to " + getHandlerDescription(handler));
				}
				//设置当前注册处理程序为默认路径处理程序
				setDefaultHandler(resolvedHandler);
			}
			//对于非根路径，非默认请求，将注册处理程序和关联的URL请求注册到handlerMap
			else {
				this.handlerMap.put(urlPath, resolvedHandler);
				if (logger.isTraceEnabled()) {
					logger.trace("Mapped [" + urlPath + "] onto " + getHandlerDescription(handler));
				}
			}
		}
	}

	private String getHandlerDescription(Object handler) {
		return (handler instanceof String ? "'" + handler + "'" : handler.toString());
	}


	/**
	 * 将注册的处理程序作为不可修改的Map返回，
	 * （如果注册程序是Bean对象名称且为单例，同时设置lazy-init，获取处理程序为bean名称）
	 */
	public final Map<String, Object> getHandlerMap() {
		return Collections.unmodifiableMap(this.handlerMap);
	}


	/**
	 * 指示此处理程序映射是否支持类型级别的映射。默认为{@code false}。
	 */
	protected boolean supportsTypeLevelMappings() {
		return false;
	}


	/**
	 * 路径暴露拦截器，用于将如下值暴露到请求属性中
	 * {@link AbstractUrlHandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}属性，最匹配的请求配置
	 * {@link AbstractUrlHandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}属性，最匹配的处理程序
	 * {@link AbstractUrlHandlerMapping#PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}属性，正则匹配路径
	 * 参考 AntPathMatcherTests
	 * //assertThat(pathMatcher.extractPathWithinPattern("/docs/*", "/docs/cvs/commit")).isEqualTo("cvs/commit");
	 * {@link AbstractUrlHandlerMapping#INTROSPECT_TYPE_LEVEL_MAPPING}属性 是否支持类型级别的映射。默认为{@code false}。
	 *
	 * @see AbstractUrlHandlerMapping#exposePathWithinMapping
	 */
	private class PathExposingHandlerInterceptor extends HandlerInterceptorAdapter {

		private final String bestMatchingPattern;

		private final String pathWithinMapping;

		public PathExposingHandlerInterceptor(String bestMatchingPattern, String pathWithinMapping) {
			this.bestMatchingPattern = bestMatchingPattern;
			this.pathWithinMapping = pathWithinMapping;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
			exposePathWithinMapping(this.bestMatchingPattern, this.pathWithinMapping, request);
			request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, handler);
			request.setAttribute(INTROSPECT_TYPE_LEVEL_MAPPING, supportsTypeLevelMappings());
			return true;
		}

	}

	/**
	 * 路径占位符暴露拦截器，
	 * 获取路径中占位符key value 暴露到请求属性中
	 *  参考 AntPathMatcherTests
	 *  //Map<String, String> result = pathMatcher.extractUriTemplateVariables("/hotels/{hotel}", "/hotels/1");
	 *	//assertThat(result).isEqualTo(Collections.singletonMap("hotel", "1"));
	 */
	private class UriTemplateVariablesHandlerInterceptor extends HandlerInterceptorAdapter {

		private final Map<String, String> uriTemplateVariables;

		public UriTemplateVariablesHandlerInterceptor(Map<String, String> uriTemplateVariables) {
			this.uriTemplateVariables = uriTemplateVariables;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
			exposeUriTemplateVariables(this.uriTemplateVariables, request);
			return true;
		}
	}

}
