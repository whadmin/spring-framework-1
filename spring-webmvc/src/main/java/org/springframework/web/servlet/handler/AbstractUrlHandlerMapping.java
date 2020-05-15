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
	 * 默认值为{@code false}。
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
	 * 设置是否延迟初始化处理程序。仅适用于单例处理程序，因为原型总是延迟初始化的
	 */
	public void setLazyInitHandlers(boolean lazyInitHandlers) {
		this.lazyInitHandlers = lazyInitHandlers;
	}

	/**
	 * 查找给定请求的URL路径的处理程序。
	 *
	 * @param request 当前的HTTP请求
	 * @return 处理程序handler；如果找不到，则为{@code null}
	 */
	@Override
	@Nullable
	protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
		//通过UrlPathHelper工具获取查Http请求中查找路径
		//例如 http请求 http://localhost:8080/beanNameUrlHandlerMapping_war/hello 返回/hello
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		//将查找路径设置到 request 属性中
		request.setAttribute(LOOKUP_PATH, lookupPath);
		//通过查找路径获取处理程序handler
		Object handler = lookupHandler(lookupPath, request);
        //查找处理程序handler 为Null
		if (handler == null) {
			Object rawHandler = null;
			//如果查找路径为根路径，获取根路径对应的特殊处理程序handler
			if ("/".equals(lookupPath)) {
				rawHandler = getRootHandler();
			}
			//若没有匹配上处理器，那就走默认的处理程序handler
			if (rawHandler == null) {
				rawHandler = getDefaultHandler();
			}
			//
			if (rawHandler != null) {
				// 如果处理程序handler类型为String,则将其作为Bean名称
				// 通过应用程序上下文获取该bean对象作为处理程序handler
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
		// 将请求urlPath和handlerMap中key直接匹配
		Object handler = this.handlerMap.get(urlPath);
		if (handler != null) {
			// Bean name or resolved handler?
			if (handler instanceof String) {
				String handlerName = (String) handler;
				handler = obtainApplicationContext().getBean(handlerName);
			}
			//校验处理程序handler 模板方法
			validateHandler(handler, request);
			//创建HandlerExecutionChain包装handel
			return buildPathExposingHandler(handler, urlPath, urlPath, null);
		}

		//临时存储满足条件的配置，配置如下：
		//  <bean name="/expressio?" class="com.wuhao.web.SupportExpressionController1"/>
		//  <bean name="/expressio*" class="com.wuhao.web.SupportExpressionController2"/>
		// 请求/expression 都满足 则将matchingPatterns={"/expressio","/expressio*"}
		List<String> matchingPatterns = new ArrayList<>();
		//  将请求urlPath和handlerMap中key正则匹配
		for (String registeredPattern : this.handlerMap.keySet()) {
			if (getPathMatcher().match(registeredPattern, urlPath)) {
				matchingPatterns.add(registeredPattern);
			}
			//是否开启尾部斜杠匹配
			else if (useTrailingSlashMatch()) {
				if (!registeredPattern.endsWith("/") && getPathMatcher().match(registeredPattern + "/", urlPath)) {
					matchingPatterns.add(registeredPattern + "/");
				}
			}
		}

		//当一个请求多个处理器程序Handle的配置满足时，
		//比如 /expressio?，/expressio* 对于请求请求/expression ，通过new AntPatternComparator(path)获取最佳匹配存储最佳匹配为/expressio?
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

		//找到最佳请求配置
		if (bestMatch != null) {
			//获取对处理程序handler
			handler = this.handlerMap.get(bestMatch);
			//如果没有找到，则可能是开启尾部斜杠匹配，可能尾部添加了/，去掉在匹配
			if (handler == null) {
				if (bestMatch.endsWith("/")) {
					handler = this.handlerMap.get(bestMatch.substring(0, bestMatch.length() - 1));
				}
				if (handler == null) {
					throw new IllegalStateException(
							"Could not find handler for best pattern match [" + bestMatch + "]");
				}
			}
			// 处理器类型为String,表示对应Bean名称，获取Bean对象作为处理程序handler
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
	 *
	 */
	protected Object buildPathExposingHandler(Object rawHandler, String bestMatchingPattern,
											  String pathWithinMapping, @Nullable Map<String, String> uriTemplateVariables) {

		HandlerExecutionChain chain = new HandlerExecutionChain(rawHandler);
		chain.addInterceptor(new PathExposingHandlerInterceptor(bestMatchingPattern, pathWithinMapping));
		if (!CollectionUtils.isEmpty(uriTemplateVariables)) {
			chain.addInterceptor(new UriTemplateVariablesHandlerInterceptor(uriTemplateVariables));
		}
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
	 * Expose the URI templates variables as request attribute.
	 *
	 * @param uriTemplateVariables the URI template variables
	 * @param request              the request to expose the path to
	 * @see #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
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
	 * Register the specified handler for the given URL paths.
	 *
	 * @param urlPaths the URLs that the bean should be mapped to
	 * @param beanName the name of the handler bean
	 * @throws BeansException        if the handler couldn't be registered
	 * @throws IllegalStateException if there is a conflicting handler registered
	 */
	protected void registerHandler(String[] urlPaths, String beanName) throws BeansException, IllegalStateException {
		Assert.notNull(urlPaths, "URL path array must not be null");
		for (String urlPath : urlPaths) {
			registerHandler(urlPath, beanName);
		}
	}

	/**
	 * Register the specified handler for the given URL path.
	 *
	 * @param urlPath the URL the bean should be mapped to
	 * @param handler the handler instance or handler bean name String
	 *                (a bean name will automatically be resolved into the corresponding handler bean)
	 * @throws BeansException        if the handler couldn't be registered
	 * @throws IllegalStateException if there is a conflicting handler registered
	 */
	protected void registerHandler(String urlPath, Object handler) throws BeansException, IllegalStateException {
		Assert.notNull(urlPath, "URL path must not be null");
		Assert.notNull(handler, "Handler object must not be null");
		Object resolvedHandler = handler;

		// Eagerly resolve handler if referencing singleton via name.
		if (!this.lazyInitHandlers && handler instanceof String) {
			String handlerName = (String) handler;
			ApplicationContext applicationContext = obtainApplicationContext();
			if (applicationContext.isSingleton(handlerName)) {
				resolvedHandler = applicationContext.getBean(handlerName);
			}
		}

		Object mappedHandler = this.handlerMap.get(urlPath);
		if (mappedHandler != null) {
			if (mappedHandler != resolvedHandler) {
				throw new IllegalStateException(
						"Cannot map " + getHandlerDescription(handler) + " to URL path [" + urlPath +
								"]: There is already " + getHandlerDescription(mappedHandler) + " mapped.");
			}
		} else {
			if (urlPath.equals("/")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Root mapping to " + getHandlerDescription(handler));
				}
				setRootHandler(resolvedHandler);
			} else if (urlPath.equals("/*")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Default mapping to " + getHandlerDescription(handler));
				}
				setDefaultHandler(resolvedHandler);
			} else {
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
	 * Return the registered handlers as an unmodifiable Map, with the registered path
	 * as key and the handler object (or handler bean name in case of a lazy-init handler)
	 * as value.
	 *
	 * @see #getDefaultHandler()
	 */
	public final Map<String, Object> getHandlerMap() {
		return Collections.unmodifiableMap(this.handlerMap);
	}

	/**
	 * Indicates whether this handler mapping support type-level mappings. Default to {@code false}.
	 */
	protected boolean supportsTypeLevelMappings() {
		return false;
	}


	/**
	 * Special interceptor for exposing the
	 * {@link AbstractUrlHandlerMapping#PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE} attribute.
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
	 * Special interceptor for exposing the
	 * {@link AbstractUrlHandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE} attribute.
	 *
	 * @see AbstractUrlHandlerMapping#exposePathWithinMapping
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
